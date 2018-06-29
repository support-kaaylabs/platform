package uk.ac.wellcome.platform.api.responses

import com.twitter.finagle.http.{Method, Request}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.display.models.v1.DisplayWorkV1
import uk.ac.wellcome.platform.api.models.DisplayResultList
import uk.ac.wellcome.platform.api.requests.MultipleResultsRequest

class ResultListResponseTest extends FunSpec with Matchers {
  val contextUri = "https://example.org/context.json"

  val requestBaseUri = "https://api.example.org"
  val requestUri = "/works"

  val displayResultList = DisplayResultList[DisplayWorkV1](
    pageSize = 10,
    totalPages = 5,
    totalResults = 45,

    // Nothing checks that results is populated correctly!
    results = List()
  )

  val multipleResultsRequest = MultipleResultsRequest(
    page = 1,
    pageSize = Some(displayResultList.pageSize),
    includes = None,
    id = None,
    query = None,
    _index = None,
    request = Request(method = Method.Get, uri = requestUri)
  )

  describe("nextPage") {
    it("omits the parameter if this is the only page") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 1)
      )

      resp.nextPage shouldBe None
    }

    it("omits the parameter if this is the last page") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 5),
        multipleResultsRequest = multipleResultsRequest.copy(page = 5)
      )

      resp.nextPage shouldBe None
    }

    it("omits the parameter if the request is beyond the last page") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 5),
        multipleResultsRequest = multipleResultsRequest.copy(page = 10)
      )

      resp.nextPage shouldBe None
    }

    it("includes the parameter if there's a next page to view") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 10),
        multipleResultsRequest = multipleResultsRequest.copy(page = 5)
      )

      resp.nextPage shouldBe Some(s"$requestBaseUri$requestUri?page=6")
    }
  }

  describe("prevPage") {
    it("omits the parameter if this is the only page") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 1)
      )

      resp.prevPage shouldBe None
    }

    it("omits the parameter if this is the first page") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 5),
        multipleResultsRequest = multipleResultsRequest.copy(page = 1)
      )

      resp.prevPage shouldBe None
    }

    it("includes the parameter if there's a previous page to view") {
      val resp = getResponse(
        displayResultList = displayResultList.copy(totalPages = 10),
        multipleResultsRequest = multipleResultsRequest.copy(page = 5)
      )

      resp.prevPage shouldBe Some(s"$requestBaseUri$requestUri?page=4")
    }
  }

  private def getResponse(
    contextUri: String = contextUri,
    displayResultList: DisplayResultList[DisplayWorkV1],
    multipleResultsRequest: MultipleResultsRequest = multipleResultsRequest,
    requestBaseUri: String = requestBaseUri
  ): ResultListResponse =
    ResultListResponse.create(
      contextUri = contextUri,
      displayResultList = displayResultList,
      multipleResultsRequest = multipleResultsRequest,
      requestBaseUri = requestBaseUri
    )
}
