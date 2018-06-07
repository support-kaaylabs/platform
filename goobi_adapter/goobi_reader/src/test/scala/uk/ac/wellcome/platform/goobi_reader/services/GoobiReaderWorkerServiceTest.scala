package uk.ac.wellcome.platform.goobi_reader.services

import java.io.{ByteArrayInputStream, InputStream}
import java.time.Instant
import java.time.temporal.ChronoUnit

import akka.actor.ActorSystem
import com.amazonaws.services.s3.{AmazonS3, AmazonS3Client}
import com.gu.scanamo.Scanamo
import org.mockito.Matchers.{any, anyDouble, endsWith}
import org.mockito.Mockito.{never, times, verify, when}
import org.scalatest.FunSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.messaging.test.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.monitoring.test.fixtures.MetricsSenderFixture
import uk.ac.wellcome.platform.goobi_reader.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.fixtures.GoobiReaderFixtures
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.test.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.storage.vhs.{HybridRecord, VersionedHybridStore}
import uk.ac.wellcome.test.fixtures.{fixture, Akka, TestWith}
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class GoobiReaderWorkerServiceTest
    extends FunSpec
    with Akka
    with MetricsSenderFixture
    with ScalaFutures
    with ExtendedPatience
    with MockitoSugar
    with GoobiReaderFixtures {

  private val id = "mets-0001"
  private val goobiS3Prefix = "goobi"
  private val sourceKey = s"$id.xml"
  private val contents = "muddling the machinations of morose METS"
  private val updatedContents = "Updated contents"
  private val eventTime = Instant.parse("2018-01-01T01:00:00.000Z")
  private val contentsStorageKey = s"$goobiS3Prefix/10/$id/cd92f8d3"
  private val updatedContentsStorageKey = s"$goobiS3Prefix/10/$id/536b51f7"

  it("processes a notification") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, _), _, table, _) =>
        s3Client.putObject(bucket.name, sourceKey, contents)

        val message = aNotificationMessage(
          topicArn = queue.arn,
          message = anS3Notification(sourceKey, bucket.name, eventTime)
        )
        sqsClient.sendMessage(queue.url, toJson(message).get)

        eventually {
          val expectedRecord = HybridRecord(
            id = id,
            version = 1,
            s3key = contentsStorageKey
          )
          assertRecordStored(
            expectedRecord,
            GoobiRecordMetadata(eventTime),
            contents,
            table,
            bucket)
        }
    }
  }

  it("doesn't overwrite a newer work with an older work") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), _, table, vhs) =>
        val contentStream = new ByteArrayInputStream(contents.getBytes)
        vhs.updateRecord(id = id)(
          ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
          ifExisting = (t, m) => (t, m))
        eventually {
          val expectedRecord = HybridRecord(
            id = id,
            version = 1,
            s3key = contentsStorageKey
          )
          val expectedMetadata = GoobiRecordMetadata(eventTime)
          assertRecordStored(
            expectedRecord,
            expectedMetadata,
            contents,
            table,
            bucket)
        }

        s3Client.putObject(bucket.name, sourceKey, contents)

        val olderEventTime = eventTime.minus(1, ChronoUnit.HOURS)
        val message = aNotificationMessage(
          topicArn = queue.arn,
          message = anS3Notification(sourceKey, bucket.name, olderEventTime))

        sqsClient.sendMessage(queue.url, toJson(message).get)

        eventually {
          val expectedRecord = HybridRecord(
            id = id,
            version = 1,
            s3key = contentsStorageKey
          )
          val expectedMetadata = GoobiRecordMetadata(eventTime)
          assertRecordStored(
            expectedRecord,
            expectedMetadata,
            contents,
            table,
            bucket)
        }
    }
  }

  it("overwrites an older work with an newer work") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), _, table, vhs) =>
        val contentStream = new ByteArrayInputStream(contents.getBytes)
        vhs.updateRecord(id = id)(
          ifNotExisting = (contentStream, GoobiRecordMetadata(eventTime)))(
          ifExisting = (t, m) => (t, m))
        eventually {
          val expectedRecord = HybridRecord(
            id = id,
            version = 1,
            s3key = contentsStorageKey
          )
          assertRecordStored(
            expectedRecord,
            GoobiRecordMetadata(eventTime),
            contents,
            table,
            bucket)
        }

        s3Client.putObject(bucket.name, sourceKey, updatedContents)
        val newerEventTime = eventTime.plus(1, ChronoUnit.HOURS)
        val message = aNotificationMessage(
          topicArn = queue.arn,
          message = anS3Notification(sourceKey, bucket.name, newerEventTime))

        sqsClient.sendMessage(queue.url, toJson(message).get)

        eventually {
          val expectedRecord = HybridRecord(
            id = id,
            version = 2,
            s3key = updatedContentsStorageKey
          )
          val expectedMetadata = GoobiRecordMetadata(newerEventTime)
          assertRecordStored(
            expectedRecord,
            expectedMetadata,
            updatedContents,
            table,
            bucket)
        }
    }
  }

  it("fails gracefully if Json cannot be parsed") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), mockMetricsSender, table, _) =>
        val notificationMessage = aNotificationMessage(
          topicArn = queue.arn,
          message = "NotJson"
        )

        sqsClient.sendMessage(queue.url, toJson(notificationMessage).get)

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          assertFailureMetricNotIncremented(mockMetricsSender)
        }
    }
  }

  it("does not fail gracefully if content cannot be fetched") {
    withGoobiReaderWorkerService(s3Client) {
      case (bucket, QueuePair(queue, dlq), mockMetricsSender, table, _) =>
        val sourceKey = "NotThere.xml"

        val notificationMessage = aNotificationMessage(
          topicArn = queue.arn,
          message = anS3Notification(sourceKey, bucket.name, eventTime)
        )

        sqsClient.sendMessage(queue.url, toJson(notificationMessage).get)

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          assertFailureMetricIncremented(mockMetricsSender)
        }
    }
  }

  it("does not fail gracefully when there is an unexpected failure") {
    val mockClient = mock[AmazonS3Client]
    val expectedException = new RuntimeException("Failed!")
    when(mockClient.getObject(any[String], any[String]))
      .thenThrow(expectedException)
    withGoobiReaderWorkerService(mockClient) {
      case (bucket, QueuePair(queue, dlq), mockMetricsSender, table, _) =>
        val sourceKey = "any.xml"
        val notificationMessage = aNotificationMessage(
          topicArn = queue.arn,
          message = anS3Notification(sourceKey, bucket.name, eventTime)
        )

        sqsClient.sendMessage(queue.url, toJson(notificationMessage).get)

        eventually {
          assertMessageSentToDlq(queue, dlq)
          assertUpdateNotSaved(bucket, table)
          assertFailureMetricIncremented(mockMetricsSender)
        }
    }
  }

  private def assertRecordStored(expectedRecord: HybridRecord,
                                 expectedMetadata: GoobiRecordMetadata,
                                 expectedContents: String,
                                 table: Table,
                                 bucket: Bucket) = {
    val id = expectedRecord.id
    getHybridRecord(table, id) shouldBe expectedRecord
    getRecordMetadata[GoobiRecordMetadata](table, id).toString shouldBe expectedMetadata.toString
    getContentFromS3(bucket, getHybridRecord(table, id).s3key) shouldBe expectedContents
  }

  private def assertMessageSentToDlq(queue: Queue, dlq: Queue) = {
    assertQueueEmpty(queue)
    assertQueueHasSize(dlq, 1)
  }

  private def assertFailureMetricNotIncremented(
    mockMetricsSender: MetricsSender) = {
    verify(mockMetricsSender, never())
      .incrementCount(endsWith("_MessageProcessingFailure"), anyDouble())
  }

  private def assertUpdateNotSaved(bucket: Bucket, table: Table) = {
    assertDynamoTableIsEmpty(table)
    assertS3StorageIsEmpty(bucket)
  }

  private def assertFailureMetricIncremented(
    mockMetricsSender: MetricsSender) = {
    verify(mockMetricsSender, times(3))
      .incrementCount(endsWith("_MessageProcessingFailure"), anyDouble())
  }

  private def assertDynamoTableIsEmpty(table: Table) = {
    Scanamo.scan[HybridRecord](dynamoDbClient)(table.name) shouldBe empty
  }

  private def assertS3StorageIsEmpty(bucket: Bucket) = {
    s3Client.listObjects(bucket.name).getObjectSummaries shouldBe empty
  }

  private def withS3StreamStoreFixtures[R](
    testWith: TestWith[(Bucket,
                        Table,
                        VersionedHybridStore[InputStream,
                                             GoobiRecordMetadata,
                                             ObjectStore[InputStream]]),
                       R]): R =
    withLocalS3Bucket[R] { bucket =>
      withLocalDynamoDbTable[R] { table =>
        withTypeVHS[InputStream, GoobiRecordMetadata, R](
          bucket,
          table,
          goobiS3Prefix) { vhs =>
          testWith((bucket, table, vhs))
        }
      }
    }

  private def withSqsStream[R](actorSystem: ActorSystem,
                               queue: Queue,
                               metricsSender: MetricsSender) =
    fixture[SQSStream[NotificationMessage], R](
      new SQSStream(
        actorSystem = actorSystem,
        sqsClient = asyncSqsClient,
        sqsConfig = SQSConfig(
          queueUrl = queue.url,
          waitTime = 1.second,
          maxMessages = 1
        ),
        metricsSender = metricsSender
      )
    )

  private def withGoobiReaderWorkerService[R](s3Client: AmazonS3)(
    testWith: TestWith[(Bucket,
                        QueuePair,
                        MetricsSender,
                        Table,
                        VersionedHybridStore[InputStream,
                                             GoobiRecordMetadata,
                                             ObjectStore[InputStream]]),
                       R]): R =
    withActorSystem { actorSystem =>
      withLocalSqsQueueAndDlq {
        case queuePair @ QueuePair(queue, dlq) =>
          withMockMetricSender { mockMetricsSender =>
            withSqsStream(actorSystem, queue, mockMetricsSender) { sqsStream =>
              withS3StreamStoreFixtures {
                case (bucket, table, vhs) =>
                  new GoobiReaderWorkerService(
                    s3Client,
                    actorSystem,
                    sqsStream,
                    vhs)
                  testWith((bucket, queuePair, mockMetricsSender, table, vhs))
              }
            }
          }
      }
    }
}