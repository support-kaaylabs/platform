package uk.ac.wellcome.platform.sierra_items_to_dynamo.sink

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneOffset}

import akka.Done
import akka.stream.scaladsl.Sink
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.gu.scanamo.error.DynamoReadError
import com.gu.scanamo.syntax._
import com.gu.scanamo.{Scanamo, Table}
import com.twitter.inject.Logging
import io.circe.Json
import io.circe.optics.JsonPath.root
import uk.ac.wellcome.models.SierraItemRecord
import uk.ac.wellcome.dynamo._

import scala.concurrent.{ExecutionContext, Future}

object SierraItemsDynamoSink extends Logging {
  def apply(client: AmazonDynamoDB, tableName: String)(
    implicit executionContext: ExecutionContext): Sink[Json, Future[Done]] =
    Sink.foreachParallel(10)(unprefixedJson => {
      val table = Table[SierraItemRecord](tableName)

      val json = addIDPrefix(json = unprefixedJson)
      logger.info(s"Inserting ${json.noSpaces} into DynamoDB")
      val maybeUpdatedDate = root.updatedDate.string.getOption(json)
      // TODO: fail if bibIds filed does not exist
      val bibIdList = root.bibIds.each.string.getAll(json)

      val record = maybeUpdatedDate match {
        case Some(updatedDate) =>
          SierraItemRecord(
            id = getId(json),
            data = json.noSpaces,
            modifiedDate = updatedDate,
            bibIds = bibIdList
          )
        case None =>
          SierraItemRecord(
            id = getId(json),
            data = json.noSpaces,
            modifiedDate = getDeletedDateTimeAtStartOfDay(json),
            bibIds = bibIdList
          )
      }

      val option = Scanamo.exec(client)(
        table.get('id -> record.id)
      )
      val newRecord = option
        .map { either =>
          val existingRecord = either.right.get
          SierraItemRecordMerger.mergeItems(oldRecord = existingRecord,
                                            newRecord = record)
        }
        .getOrElse(record)

      val ops = table
        .given(
          not(attributeExists('id)) or
            (attributeExists('id) and 'modifiedDate < newRecord.modifiedDate.getEpochSecond)
        )
        .put(newRecord)
      Scanamo.exec(client)(ops) match {
        case Right(_) =>
          logger.info(s"${json.noSpaces} saved successfully to DynamoDB")
        case Left(error) =>
          logger.warn(s"Failed saving ${json.noSpaces} into DynamoDB", error)
      }
    })

  private def getDeletedDateTimeAtStartOfDay(json: Json) = {
    val formatter = DateTimeFormatter.ISO_DATE
    LocalDate
      .parse(root.deletedDate.string.getOption(json).get, formatter)
      .atStartOfDay()
      .toInstant(ZoneOffset.UTC)
  }

  // Sierra assigns IDs for bibs and items in the same namespace.  A record
  // with ID "1234567" could be a bib or an item (or something else!).
  //
  // Outside Sierra, IDs are prefixed with a little to denote what type of
  // record they are, e.g. "b1234567" and "i1234567" refer to a bib and item,
  // respectively.
  //
  // This updates the ID in a block of JSON to add this disambiguating prefix.
  def addIDPrefix(json: Json): Json =
    root.id.string.modify(id => s"i$id")(json)

  private def getId(json: Json) = {
    root.id.string.getOption(json).get
  }
}