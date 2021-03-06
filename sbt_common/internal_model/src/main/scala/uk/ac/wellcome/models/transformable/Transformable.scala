package uk.ac.wellcome.models.transformable

import io.circe.{KeyDecoder, KeyEncoder}
import uk.ac.wellcome.models.Sourced
import uk.ac.wellcome.models.transformable.sierra.{
  SierraBibNumber,
  SierraBibRecord,
  SierraItemNumber,
  SierraItemRecord
}

sealed trait Transformable extends Sourced

case class MiroTransformable(sourceId: String,
                             MiroCollection: String,
                             data: String)
    extends Transformable {
  val sourceName = "miro"
}

case class SierraTransformable(
  sierraId: SierraBibNumber,
  maybeBibRecord: Option[SierraBibRecord] = None,
  itemRecords: Map[SierraItemNumber, SierraItemRecord] = Map()
) extends Transformable {
  val sourceId: String = sierraId.withoutCheckDigit
  val sourceName = "sierra"
}

object SierraTransformable {
  def apply(bibRecord: SierraBibRecord): SierraTransformable =
    SierraTransformable(
      sierraId = bibRecord.id,
      maybeBibRecord = Some(bibRecord))

  // Because the [[SierraTransformable.itemRecords]] field is keyed by
  // [[SierraItemNumber]] in our case class, but JSON only supports string
  // keys, we need to turn the ID into a string when storing as JSON.
  //
  // This is based on the "Custom key types" section of the Circe docs:
  // https://circe.github.io/circe/codecs/custom-codecs.html#custom-key-types
  //
  implicit val keyEncoder: KeyEncoder[SierraItemNumber] =
    (key: SierraItemNumber) => key.withoutCheckDigit

  implicit val keyDecoder: KeyDecoder[SierraItemNumber] =
    (key: String) => Some(SierraItemNumber(key))
}
