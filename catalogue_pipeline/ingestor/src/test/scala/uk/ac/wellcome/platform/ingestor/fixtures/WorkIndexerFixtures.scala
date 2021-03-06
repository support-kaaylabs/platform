package uk.ac.wellcome.platform.ingestor.fixtures

import com.sksamuel.elastic4s.http.HttpClient
import org.scalatest.Suite
import uk.ac.wellcome.platform.ingestor.services.WorkIndexer
import uk.ac.wellcome.test.fixtures._

import scala.concurrent.ExecutionContext.Implicits.global

trait WorkIndexerFixtures extends Akka { this: Suite =>
  def withWorkIndexer[R](elasticClient: HttpClient)(
    testWith: TestWith[WorkIndexer, R]): R = {
    val workIndexer = new WorkIndexer(elasticClient = elasticClient)
    testWith(workIndexer)
  }

  def withWorkIndexerFixtures[R](esType: String, elasticClient: HttpClient)(
    testWith: TestWith[WorkIndexer, R]): R = {
    withActorSystem { actorSystem =>
      withWorkIndexer(elasticClient = elasticClient) { workIndexer =>
        testWith(workIndexer)
      }
    }
  }
}
