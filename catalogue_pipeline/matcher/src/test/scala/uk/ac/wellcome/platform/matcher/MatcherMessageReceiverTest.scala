package uk.ac.wellcome.platform.matcher

import com.amazonaws.services.s3.AmazonS3
import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.sns.{NotificationMessage, SNSConfig, SNSWriter}
import uk.ac.wellcome.messaging.sqs.{SQSConfig, SQSStream}
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.test.fixtures.{SNS, SQS}
import uk.ac.wellcome.models.recorder.internal.RecorderWorkEntry
import uk.ac.wellcome.models.work.internal.{
  IdentifierSchemes,
  SourceIdentifier,
  UnidentifiedWork
}
import uk.ac.wellcome.monitoring.test.fixtures.MetricsSenderFixture
import uk.ac.wellcome.storage.s3.{S3Config, S3TypeStore}
import uk.ac.wellcome.storage.test.fixtures.S3
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.storage.vhs.HybridRecord
import uk.ac.wellcome.test.fixtures.{Akka, TestWith}
import uk.ac.wellcome.test.utils.ExtendedPatience
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.duration._

class MatcherMessageReceiverTest
    extends FunSpec
    with Matchers
    with Akka
    with SQS
    with SNS
    with S3
    with MetricsSenderFixture
    with ExtendedPatience
    with Eventually {

  def withMatcherMessageReceiver[R](
    queue: SQS.Queue,
    storageBucket: Bucket,
    topic: Topic)(testWith: TestWith[MatcherMessageReceiver, R]) = {
    val storageS3Config = S3Config(storageBucket.name)

    val snsWriter =
      new SNSWriter(snsClient, SNSConfig(topic.arn))

    withActorSystem { actorSystem =>
      withMetricsSender(actorSystem) { metricsSender =>
        val sqsStream = new SQSStream[NotificationMessage](
          actorSystem = actorSystem,
          sqsClient = asyncSqsClient,
          sqsConfig = SQSConfig(queue.url, 1 second, 1),
          metricsSender = metricsSender
        )
        val matcherMessageReceiver = new MatcherMessageReceiver(
          sqsStream,
          snsWriter,
          new S3TypeStore[RecorderWorkEntry](s3Client),
          storageS3Config,
          actorSystem,
          new Bah)
        testWith(matcherMessageReceiver)
      }
    }
  }

  it("sends no redirects for a work without identifiers") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          val work = unidentifiedWork
          sendSQS(queue, storageBucket, work)

          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            eventually {
              assertMessageSent(topic, MatchedWorksList(List(
                  MatchedWorkIds(matchedWorkId = "sierra-system-number/id", linkedWorkIds = List("sierra-system-number/id")))))
              }

          }
        }
      }
    }
  }

  it("redirects a work with one link and no existing redirects") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          val linkedIdentifier = sourceIdentifier("B")
          val aIdentifier = sourceIdentifier("A")
          val work = unidentifiedWork.copy(
            sourceIdentifier = aIdentifier,
            identifiers = List(aIdentifier, linkedIdentifier))

          sendSQS(queue, storageBucket, work)

          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            eventually {
              assertMessageSent(topic, MatchedWorksList(List(
                  MatchedWorkIds(
                    matchedWorkId = "sierra-system-number/A+sierra-system-number/B",
                    linkedWorkIds = List(
                      "sierra-system-number/A",
                      "sierra-system-number/B",
                      "sierra-system-number/A+sierra-system-number/B")))))

            }
          }
        }
      }
    }
  }

  it("redirects a work with one link and existing redirects") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
          val aIdentifier = sourceIdentifier("A")
          val bIdentifier = sourceIdentifier("B")
          val cIdentifier = sourceIdentifier("C")
          val aWork = unidentifiedWork.copy(
            sourceIdentifier = aIdentifier,
            identifiers = List(aIdentifier, bIdentifier))

          sendSQS(queue, storageBucket, aWork)

          eventually {

            assertMessageSent(topic, MatchedWorksList(List(
                    MatchedWorkIds(
                      matchedWorkId = "sierra-system-number/A+sierra-system-number/B",
                      linkedWorkIds = List(
                        "sierra-system-number/A",
                        "sierra-system-number/B",
                        "sierra-system-number/A+sierra-system-number/B")))))

            val bWork = unidentifiedWork.copy(
              sourceIdentifier = bIdentifier,
              identifiers = List(bIdentifier, cIdentifier))

            sendSQS(queue, storageBucket, bWork)

              eventually {

                assertMessageSent(topic, MatchedWorksList(List(
                  MatchedWorkIds(
                    matchedWorkId = "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C",
                    linkedWorkIds = List(
                      "sierra-system-number/A",
                      "sierra-system-number/B",
                      "sierra-system-number/C",
                      "sierra-system-number/A+sierra-system-number/B",
                      "sierra-system-number/A+sierra-system-number/B+sierra-system-number/C"
                    ))))
                )

              }
            }
          }
        }
      }
    }
  }

  private def sourceIdentifier(id: String) = SourceIdentifier(IdentifierSchemes.sierraSystemNumber, "Work", id)

  private def assertMessageSent(topic: Topic, matchedWorksList: MatchedWorksList) = {
    val snsMessages = listMessagesReceivedFromSNS(topic)
    snsMessages.size should be >= 1

    val actualMatchedWorkLists= snsMessages.map { snsMessage =>
        fromJson[MatchedWorksList](snsMessage.message).get
    }
      actualMatchedWorkLists should contain (matchedWorksList)

  }

  private def sendSQS(queue: SQS.Queue,
                      storageBucket: Bucket,
                      work: UnidentifiedWork) = {
    val workSqsMessage: NotificationMessage =
      hybridRecordNotificationMessage(
        message = toJson(RecorderWorkEntry(work = work)).get,
        version = 1,
        s3Client = s3Client,
        bucket = storageBucket
      )
    sqsClient.sendMessage(
      queue.url,
      toJson(workSqsMessage).get
    )
  }

  private def unidentifiedWork = {
    val sourceIdentifier =
      SourceIdentifier(IdentifierSchemes.sierraSystemNumber, "Work", "id")
    UnidentifiedWork(
      sourceIdentifier = sourceIdentifier,
      title = Some("Work"),
      version = 1,
      identifiers = List(sourceIdentifier)
    )
  }

  def hybridRecordNotificationMessage(message: String,
                                      version: Int,
                                      s3Client: AmazonS3,
                                      bucket: Bucket) = {
    val key = "recorder/1/testId/dshg548.json"
    s3Client.putObject(bucket.name, key, message)

    val hybridRecord = HybridRecord(
      id = "testId",
      version = version,
      s3key = key
    )

    NotificationMessage(
      "messageId",
      "topicArn",
      "subject",
      toJson(hybridRecord).get
    )
  }

}
