package uk.ac.wellcome.platform.reindex.creator.dynamo

import com.amazonaws.services.dynamodbv2.model.AmazonDynamoDBException
import com.gu.scanamo.Scanamo
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.storage.dynamo.{DynamoConfig, TestVersioned}
import uk.ac.wellcome.storage.fixtures.LocalDynamoDb.Table
import uk.ac.wellcome.storage.fixtures.LocalDynamoDbVersioned
import uk.ac.wellcome.test.fixtures._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ParallelScannerTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with LocalDynamoDbVersioned {

  it("reads a table with a single record") {
    withLocalDynamoDbTable { table =>
      withParallelScanner(table) { parallelScanner =>
        val record =
          TestVersioned(id = "123", data = "hello world", version = 1)
        Scanamo.put(dynamoDbClient)(table.name)(record)

        val futureResult = parallelScanner.scan[TestVersioned](
          segment = 0,
          totalSegments = 1
        )

        whenReady(futureResult) { result =>
          result shouldBe List(Right(record))
        }
      }
    }
  }

  it("reads all the records from a table across multiple scans") {
    runTest(totalRecords = 1000, segmentCount = 6)
  }

  it("reads all the records even when segmentCount > totalRecords") {
    runTest(totalRecords = 5, segmentCount = 10)
  }

  it(
    "returns a failed future if asked for a segment that's greater than totalSegments") {
    withLocalDynamoDbTable { table =>
      withParallelScanner(table) { parallelScanner =>
        val future = parallelScanner.scan[TestVersioned](
          segment = 10,
          totalSegments = 5
        )

        whenReady(future.failed) { r =>
          r shouldBe a[AmazonDynamoDBException]
          val message = r.asInstanceOf[AmazonDynamoDBException].getMessage
          message should include(
            "Value '10' at 'segment' failed to satisfy constraint: Member must have value less than or equal to 4")
        }
      }
    }
  }

  private def runTest(totalRecords: Int, segmentCount: Int): Assertion = {
    withLocalDynamoDbTable { table =>
      withParallelScanner(table) { parallelScanner =>
        val records = (1 to totalRecords).map { id =>
          TestVersioned(id = id.toString, data = "Hello world", version = 1)
        }

        records.map { record =>
          Scanamo.put(dynamoDbClient)(table.name)(record)
        }

        // Note that segments are 0-indexed
        val futureResults = (0 to segmentCount - 1).map { segment =>
          parallelScanner.scan[TestVersioned](
            segment = segment,
            totalSegments = segmentCount
          )
        }

        whenReady(Future.sequence(futureResults)) { results =>
          val actualRecords: List[TestVersioned] = results.flatten.toList
            .map {
              _.right.get
            }
          actualRecords should contain theSameElementsAs records
        }
      }
    }
  }

  private def withParallelScanner[R](table: Table)(
    testWith: TestWith[ParallelScanner, R]): R = {
    val scanner = new ParallelScanner(
      dynamoDBClient = dynamoDbClient,
      dynamoConfig = DynamoConfig(
        table = table.name,
        index = table.index
      )
    )

    testWith(scanner)
  }
}
