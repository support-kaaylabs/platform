package uk.ac.wellcome.platform.sierra_to_dynamo.models

import java.time.Instant

import com.gu.scanamo.DynamoFormat

case class SierraRecord(
  id: String,
  data: String,
  updatedDate: Instant
)

object SierraRecord {
  implicit val instantLongFormat =
    DynamoFormat.coercedXmap[Instant, Long, IllegalArgumentException](
      Instant.ofEpochSecond
    )(
      _.getEpochSecond
    )

  def apply(id: String, data: String, updatedDate: String): SierraRecord =
    SierraRecord(
      id = id,
      data = data,
      updatedDate = Instant.parse(updatedDate)
    )
}