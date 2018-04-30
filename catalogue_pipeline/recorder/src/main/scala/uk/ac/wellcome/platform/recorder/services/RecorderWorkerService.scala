package uk.ac.wellcome.platform.recorder.services

import akka.actor.ActorSystem
import com.google.inject.Inject
import io.circe.{Decoder, Encoder}
import uk.ac.wellcome.messaging.sqs.{SQSReader, SQSWorkerToDynamo}
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.monitoring.MetricsSender
import uk.ac.wellcome.recorder.models.RecorderWorkEntry
import uk.ac.wellcome.storage.VersionedHybridStore
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.Future

case class EmptyMetadata()

class RecorderWorkerService @Inject()(
  versionedHybridStore: VersionedHybridStore[RecorderWorkEntry],
  reader: SQSReader,
  system: ActorSystem,
  metrics: MetricsSender
) extends SQSWorkerToDynamo[UnidentifiedWork](reader, system, metrics) {

  implicit val decoder = Decoder[RecorderWorkEntry]
  implicit val encoder = Encoder[RecorderWorkEntry]

  override def store(work: UnidentifiedWork): Future[Unit] = {

    val newRecorderEntry = RecorderWorkEntry(work)

    versionedHybridStore.updateRecord(newRecorderEntry.id)(newRecorderEntry)(
      existingEntry => if (existingEntry.work.version > newRecorderEntry.work.version) {
        existingEntry
      } else { newRecorderEntry }
    )(metadata = EmptyMetadata())
  }
}
