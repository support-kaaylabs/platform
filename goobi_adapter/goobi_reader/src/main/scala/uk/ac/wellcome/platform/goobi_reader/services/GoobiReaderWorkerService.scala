package uk.ac.wellcome.platform.goobi_reader.services

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.amazonaws.services.s3.AmazonS3
import com.google.inject.Inject
import com.twitter.inject.Logging
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs._
import uk.ac.wellcome.platform.goobi_reader.GoobiRecordMetadata
import uk.ac.wellcome.platform.goobi_reader.models.{S3Event, S3Record}
import uk.ac.wellcome.storage.ObjectStore
import uk.ac.wellcome.storage.dynamo._
import uk.ac.wellcome.storage.vhs.VersionedHybridStore
import uk.ac.wellcome.json.JsonUtil._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Try

class GoobiReaderWorkerService @Inject()(
  s3Client: AmazonS3,
  system: ActorSystem,
  sqsStream: SQSStream[NotificationMessage],
  versionedHybridStore: VersionedHybridStore[InputStream,
                                             GoobiRecordMetadata,
                                             ObjectStore[InputStream]]
) extends Logging {

  implicit val actorSystem: ActorSystem = system
  implicit val materialiser: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  sqsStream.foreach(
    streamName = this.getClass.getSimpleName,
    process = processMessage
  )

  private def processMessage(snsNotification: NotificationMessage) = {
    debug(s"Received notification: $snsNotification")
    val eventuallyProcessedMessages = for {
      // AWS events are URL encoded, which means that the object key is URL encoded
      // The s3Client.putObject method doesn't want URL encoded keys, so decode it
      urlDecodedMessage <- Future.fromTry(
        Try(java.net.URLDecoder.decode(snsNotification.Message, "utf-8")))
      eventNotification <- Future.fromTry(fromJson[S3Event](urlDecodedMessage))
      _ <- Future.sequence(eventNotification.Records.map(updateRecord))
    } yield ()
    eventuallyProcessedMessages.failed.foreach { e: Throwable =>
      error(
        s"Error processing message with SNS-MessageId=${snsNotification.MessageId}. Exception ${e.getClass.getCanonicalName} $e.getMessage")
    }
    eventuallyProcessedMessages
  }

  private def updateRecord(r: S3Record) = {
    val bucketName = r.s3.bucket.name
    val objectKey = r.s3.`object`.key
    val id = objectKey.replaceAll(".xml", "")
    val updateEventTime = r.eventTime

    val eventuallyContent = Future {
      debug(s"trying to retrieve object s3://$bucketName/$objectKey")
      s3Client.getObject(bucketName, objectKey).getObjectContent
    }
    eventuallyContent.flatMap(updatedContent => {
      versionedHybridStore.updateRecord(id = id)(
        ifNotExisting = (updatedContent, GoobiRecordMetadata(updateEventTime)))(
        ifExisting = (existingContent, existingMetadata) => {
          if (existingMetadata.eventTime.isBefore(updateEventTime))
            (updatedContent, GoobiRecordMetadata(updateEventTime))
          else
            (existingContent, existingMetadata)
        })
    })
  }
}
