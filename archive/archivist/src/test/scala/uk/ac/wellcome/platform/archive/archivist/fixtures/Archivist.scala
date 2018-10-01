package uk.ac.wellcome.platform.archive.archivist.fixtures

import java.io.File
import java.net.URI
import java.util.UUID
import java.util.zip.ZipFile

import com.google.inject.Guice
import uk.ac.wellcome.messaging.test.fixtures.Messaging
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.test.fixtures.SQS.QueuePair
import uk.ac.wellcome.platform.archive.archivist.modules.{
  ConfigModule,
  TestAppConfigModule
}
import uk.ac.wellcome.platform.archive.archivist.{Archivist => ArchivistApp}
import uk.ac.wellcome.platform.archive.common.fixtures.FileEntry
import uk.ac.wellcome.platform.archive.common.models.{BagPath, IngestBagRequest}
import uk.ac.wellcome.platform.archive.common.modules._
import uk.ac.wellcome.platform.archive.common.progress.fixtures.ProgressMonitorFixture
import uk.ac.wellcome.platform.archive.common.progress.modules.ProgressMonitorModule
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.TestWith

trait Archivist
    extends LocalDynamoDb
    with ProgressMonitorFixture
    with Messaging
    with ZipBagItFixture {

  def sendBag[R](bagName: BagPath,
                 zipFile: ZipFile,
                 ingestBucket: Bucket,
                 callbackUri: Option[URI],
                 queuePair: QueuePair)(
    testWith: TestWith[(UUID, ObjectLocation, BagPath), R]) = {
    val uploadKey = s"upload/path/$bagName.zip"

    s3Client.putObject(ingestBucket.name, uploadKey, new File(zipFile.getName))

    val uploadedBagLocation = ObjectLocation(ingestBucket.name, uploadKey)
    val ingestRequestId = UUID.randomUUID()
    sendNotificationToSQS(
      queuePair.queue,
      IngestBagRequest(ingestRequestId, uploadedBagLocation, callbackUri))

    testWith((ingestRequestId, uploadedBagLocation, bagName))
  }

  def createAndSendBag[R](
    ingestBucket: Bucket,
    callbackUri: Option[URI],
    queuePair: QueuePair,
    dataFileCount: Int = 12,
    createDigest: String => String = createValidDigest,
    createDataManifest: (BagPath, List[(String, String)]) => Option[FileEntry] =
      createValidDataManifest,
    createBagItFile: BagPath => Option[FileEntry] = createValidBagItFile)(
    testWith: TestWith[(UUID, ObjectLocation, BagPath), R]) =
    withBagItZip(
      dataFileCount = dataFileCount,
      createDigest = createDigest,
      createDataManifest = createDataManifest,
      createBagItFile = createBagItFile) {
      case (bagName, zipFile) =>
        sendBag(bagName, zipFile, ingestBucket, callbackUri, queuePair) {
          case (requestId, uploadObjectLocation, bag) =>
            testWith((requestId, uploadObjectLocation, bag))
        }
    }

  def withApp[R](storageBucket: Bucket,
                 queuePair: QueuePair,
                 topicArn: Topic,
                 progressTable: Table)(testWith: TestWith[ArchivistApp, R]) = {
    val archivist = new ArchivistApp {
      val injector = Guice.createInjector(
        new TestAppConfigModule(
          queuePair.queue.url,
          storageBucket.name,
          topicArn.arn,
          progressTable),
        ConfigModule,
        AkkaModule,
        S3ClientModule,
        CloudWatchClientModule,
        SQSClientModule,
        SNSAsyncClientModule,
        ProgressMonitorModule
      )
    }
    testWith(archivist)
  }

  def withArchivist[R](
    testWith: TestWith[(Bucket, Bucket, QueuePair, Topic, Table, ArchivistApp),
                       R]) = {
    withLocalSqsQueueAndDlqAndTimeout(5)(queuePair => {
      withLocalSnsTopic {
        snsTopic =>
          withLocalS3Bucket {
            ingestBucket =>
              withLocalS3Bucket {
                storageBucket =>
                  withSpecifiedLocalDynamoDbTable(createProgressMonitorTable) {
                    progressTable =>
                      withApp(storageBucket, queuePair, snsTopic, progressTable) {
                        archivist =>
                          testWith(
                            (
                              ingestBucket,
                              storageBucket,
                              queuePair,
                              snsTopic,
                              progressTable,
                              archivist))
                      }
                  }
              }
          }
      }
    })
  }

}
