package uk.ac.wellcome.platform.sierra_reader.services

import akka.actor.ActorSystem
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FunSpec, Matchers}
import uk.ac.wellcome.metrics.MetricsSender
import uk.ac.wellcome.models.aws.{SQSConfig, SQSMessage}
import uk.ac.wellcome.sqs.{SQSReader, SQSReaderGracefulException}
import uk.ac.wellcome.test.utils.{ExtendedPatience, S3Local, SQSLocal}
import uk.ac.wellcome.utils.JsonUtil

import scala.concurrent.duration._

class SierraReaderWorkerServiceTest
    extends FunSpec
    with MockitoSugar
    with S3Local
    with SQSLocal
    with Eventually
    with Matchers
    with ExtendedPatience
    with ScalaFutures
    with BeforeAndAfterEach
    with BeforeAndAfterAll {

  val queueUrl = createQueueAndReturnUrl("sierra-test-queue")
  val bucketName: String = createBucketAndReturnName("sierra-reader-test-bucket")

  val mockMetrics = mock[MetricsSender]
  var worker: Option[SierraReaderWorkerService] = None
  val actorSystem = ActorSystem()

  override def beforeEach(): Unit = {
    super.beforeEach()
    stopWorker(worker)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    actorSystem.terminate()
  }

  private def createSierraReaderWorkerService(
    fields: String,
    apiUrl: String = "http://localhost:8080"
  ) = {
    Some(
      new SierraReaderWorkerService(
        reader = new SQSReader(sqsClient, SQSConfig(queueUrl, 1.second, 1)),
        s3client = s3Client,
        bucketName = bucketName,
        system = actorSystem,
        metrics = mockMetrics,
        apiUrl = apiUrl,
        sierraOauthKey = "key",
        sierraOauthSecret = "secret",
        fields = fields
      ))
  }

  it(
    "reads a window message from SQS, retrieves the bibs from Sierra and writes them to S3") {
    worker = createSierraReaderWorkerService(
      fields = "updatedDate,deletedDate,deleted,suppressed,author,title"
    )
    worker.get.runSQSWorker()
    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")
    sqsClient.sendMessage(queueUrl, JsonUtil.toJson(sqsMessage).get)

    eventually {
      val objects = s3Client.listObjects(bucketName).getObjectSummaries

      // One file containing the page, another "done" marker for the window
      objects should have size 2
    }
  }

  it(
    "returns a SQSReaderGracefulException if it receives a message that doesn't contain start or end values") {
    worker = createSierraReaderWorkerService(fields = "")

    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")
    whenReady(worker.get.processMessage(sqsMessage).failed) { ex =>
      ex shouldBe a[SQSReaderGracefulException]
    }

  }

  it(
    "does not return a SQSReaderGracefulException if it cannot reach the Sierra API") {
    worker = createSierraReaderWorkerService(
      fields = "",
      apiUrl = "http://localhost:5050"
    )

    val message =
      """
        |{
        | "start": "2013-12-10T17:16:35Z",
        | "end": "2013-12-13T21:34:35Z"
        |}
      """.stripMargin

    val sqsMessage =
      SQSMessage(Some("subject"), message, "topic", "messageType", "timestamp")

    whenReady(worker.get.processMessage(sqsMessage).failed) { ex =>
      ex shouldNot be(a[SQSReaderGracefulException])
    }
  }

  private def stopWorker(worker: Option[SierraReaderWorkerService]) = {
    eventually {
      worker.fold(true)(_.cancelRun()) shouldBe true
    }
  }
}
