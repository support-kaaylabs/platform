package uk.ac.wellcome.platform.reindex.creator

import com.gu.scanamo.Scanamo
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.messaging.test.fixtures.{SNS, SQS}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.reindex.creator.models.ReindexJob
import uk.ac.wellcome.storage.ObjectLocation
import uk.ac.wellcome.storage.fixtures.LocalDynamoDbVersioned
import uk.ac.wellcome.storage.vhs.HybridRecord

class ReindexRequestCreatorFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with fixtures.Server
    with LocalDynamoDbVersioned
    with SNS
    with SQS
    with ScalaFutures {

  private def createReindexableData(table: Table): Seq[HybridRecord] = {
    val numberOfRecords = 4

    val testRecords = (1 to numberOfRecords).map(i => {
      HybridRecord(
        id = s"id$i",
        location = ObjectLocation(
          namespace = "s3://example-bukkit",
          key = s"id$i"
        ),
        version = 1
      )
    })

    testRecords.map { testRecord =>
      Scanamo.put(dynamoDbClient)(table.name)(testRecord)
      testRecord
    }
  }

  it("sends a notification for every record that needs a reindex") {
    withLocalSqsQueue { queue =>
      withLocalDynamoDbTable { table =>
        withLocalSnsTopic { topic =>
          val flags = snsLocalFlags(topic) ++ dynamoDbLocalEndpointFlags(table) ++ sqsLocalFlags(
            queue)

          withServer(flags) { _ =>
            val testRecords = createReindexableData(table)

            val reindexJob = ReindexJob(segment = 0, totalSegments = 1)

            sendNotificationToSQS(
              queue = queue,
              message = reindexJob
            )

            eventually {
              val actualRecords: Seq[HybridRecord] =
                listMessagesReceivedFromSNS(topic)
                  .map { _.message }
                  .map { fromJson[HybridRecord](_).get }
                  .distinct

              actualRecords should contain theSameElementsAs testRecords
            }
          }
        }
      }
    }
  }
}
