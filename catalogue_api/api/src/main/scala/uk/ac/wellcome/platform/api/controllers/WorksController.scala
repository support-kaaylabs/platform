package uk.ac.wellcome.platform.api.controllers

import com.jakehschwartz.finatra.swagger.SwaggerController
import com.twitter.finatra.http.Controller
import io.swagger.models.parameters.QueryParameter
import io.swagger.models.properties.StringProperty
import io.swagger.models.{Operation, Swagger}
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.api.ContextHelper.buildContextUri
import uk.ac.wellcome.platform.api.models.{
  ApiConfig,
  DisplayError,
  DisplayResultList,
  Error
}
import uk.ac.wellcome.platform.api.requests._
import uk.ac.wellcome.platform.api.responses.{
  ResultListResponse,
  ResultResponse
}
import uk.ac.wellcome.platform.api.services.WorksService

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.runtime.universe.TypeTag

abstract class WorksController[M <: MultipleResultsRequest[W],
                               S <: SingleWorkRequest[W],
                               W <: WorksIncludes](
  apiConfig: ApiConfig,
  indexName: String,
  worksService: WorksService)(implicit ec: ExecutionContext)
    extends Controller
    with SwaggerController {

  protected val includeParameterName: String
  def emptyWorksIncludes: W
  def recognisedIncludes: List[String]

  val includeSwaggerParam: QueryParameter = new QueryParameter()
    .name(includeParameterName)
    .description("A comma-separated list of extra fields to include")
    .required(false)
    .`type`("array")
    .collectionFormat("csv")
    .items(new StringProperty()._enum(recognisedIncludes.asJava))

  protected def setupResultListEndpoint[T <: DisplayWork](
    version: ApiVersions.Value,
    endpointSuffix: String,
    toDisplayWork: (IdentifiedWork, W) => T)(
    implicit evidence: TypeTag[DisplayResultList[T]],
    manifest: Manifest[M]): Unit = {
    getWithDoc(s"$endpointSuffix") { doc =>
      setupResultListSwaggerDocs[T](s"$endpointSuffix", swagger, doc)
    } { request: M =>
      val pageSize = request.pageSize.getOrElse(apiConfig.defaultPageSize)
      val includes = request.include.getOrElse(emptyWorksIncludes)

      for {
        resultList <- getWorkList(request, pageSize)
        displayResultList = DisplayResultList(
          resultList = resultList,
          toDisplayWork,
          pageSize = pageSize,
          includes = includes)
      } yield
        ResultListResponse.create[T, M, W](
          buildContextUri(apiConfig = apiConfig, version = version),
          displayResultList,
          request,
          s"${apiConfig.scheme}://${apiConfig.host}"
        )
    }
  }

  protected def setupSingleWorkEndpoint[T <: DisplayWork](
    version: ApiVersions.Value,
    endpointSuffix: String,
    toDisplayWork: (IdentifiedWork, W) => T)(implicit evidence: TypeTag[T],
                                             manifest: Manifest[S]): Unit = {
    getWithDoc(s"$endpointSuffix") { doc =>
      setUpSingleWorkSwaggerDocs[T](swagger, doc)
    } { request: S =>
      val includes = request.include.getOrElse(emptyWorksIncludes)

      val contextUri =
        buildContextUri(apiConfig = apiConfig, version = version)
      for {
        maybeWork <- worksService.findWorkById(
          canonicalId = request.id,
          indexName = request._index
            .getOrElse(indexName))
      } yield
        generateSingleWorkResponse(
          maybeWork,
          toDisplayWork,
          includes,
          request,
          contextUri)
    }
  }

  private def getWorkList(request: M, pageSize: Int) = {
    val works = request.query match {
      case Some(queryString) =>
        worksService.searchWorks(
          queryString,
          pageSize = pageSize,
          pageNumber = request.page,
          indexName = request._index
            .getOrElse(indexName)
        )
      case None =>
        worksService.listWorks(
          pageSize = pageSize,
          pageNumber = request.page,
          indexName = request._index
            .getOrElse(indexName)
        )
    }
    works
  }

  private def generateSingleWorkResponse[T <: DisplayWork](
    maybeWork: Option[IdentifiedBaseWork],
    toDisplayWork: (IdentifiedWork, W) => T,
    includes: W,
    request: S,
    contextUri: String) =
    maybeWork match {
      case Some(work: IdentifiedWork) =>
        respondWithWork[T](toDisplayWork(work, includes), contextUri: String)
      case Some(work: IdentifiedRedirectedWork) =>
        respondWithRedirect(
          originalUri = request.request.uri,
          work = work,
          contextUri: String)
      case Some(_) => respondWithGoneError(contextUri: String)
      case None =>
        respondWithNotFoundError(request, contextUri: String)
    }

  private def respondWithWork[T <: DisplayWork](result: T,
                                                contextUri: String) = {
    response.ok.json(ResultResponse(context = contextUri, result = result))
  }

  /** Create a 302 Redirect to a new Work.
    *
    * Assumes the original URI requested was for a single work, i.e. a request
    * of the form /works/{id}.
    *
    */
  private def respondWithRedirect(originalUri: String,
                                  work: IdentifiedRedirectedWork,
                                  contextUri: String) =
    response.found
      .body("")
      .location(
        uri = originalUri.replaceAll(
          s"/${work.canonicalId}",
          s"/${work.redirect.canonicalId}"
        )
      )

  private def respondWithGoneError(contextUri: String) = {
    val result = Error(
      variant = "http-410",
      description = Some("This work has been deleted")
    )
    response.gone.json(
      ResultResponse(
        context = contextUri,
        result = DisplayError(result)
      )
    )
  }

  private def respondWithNotFoundError(request: S, contextUri: String) = {
    val result = Error(
      variant = "http-404",
      description = Some(s"Work not found for identifier ${request.id}")
    )
    response.notFound.json(
      ResultResponse(context = contextUri, result = DisplayError(result))
    )
  }

  private def setupResultListSwaggerDocs[T <: DisplayWork](
    endpointSuffix: String,
    swagger: Swagger,
    doc: Operation)(implicit evidence: TypeTag[DisplayResultList[T]]) = {
    implicit val finatraSwagger = swagger
    doc
      .summary(endpointSuffix)
      .description("Returns a paginated list of works")
      .tag("Works")
      .responseWith[DisplayResultList[T]](200, "ResultList[Work]")
      .responseWith[DisplayError](400, "Bad Request Error")
      .responseWith[DisplayError](404, "Not Found Error")
      .responseWith[DisplayError](500, "Internal Server Error")
      .queryParam[Int](
        "page",
        "The page to return from the result list",
        required = false)
      .queryParam[Int](
        "pageSize",
        "The number of works to return per page (default: 10)",
        required = false)
      .queryParam[String](
        "query",
        """Full-text search query, which will OR supplied terms by default.
        |
        |The following special characters can be used to change the search behaviour:
        |
        |- \+ signifies AND operation
        |- | signifies OR operation
        |- \- negates a single token
        |- " wraps a number of tokens to signify a phrase for searching
        |- \* at the end of a term signifies a prefix query
        |- ( and ) signify precedence
        |- ~N after a word signifies edit distance (fuzziness)
        |- ~N after a phrase signifies slop amount
        |
        |To search for any of these special characters, they should be escaped with \.""".stripMargin,
        required = false
      )
      .parameter(includeSwaggerParam)
    // Deliberately undocumented: we have an 'index' query param that
    // allows the user to pick which Elasticsearch index to use.  This is
    // useful for us to try out transformer changes, different index
    // weighting, etc., but we don't want to advertise its existence
    // in the public docs.
  }

  private def setUpSingleWorkSwaggerDocs[T <: DisplayWork](
    swagger: Swagger,
    doc: Operation)(implicit evidence: TypeTag[T]) = {
    implicit val finatraSwagger = swagger
    doc
      .summary(s"/works/{id}")
      .description("Returns a single work")
      .tag("Works")
      .routeParam[String]("id", "The work to return", required = true)
      .responseWith[T](200, "Work")
      .responseWith[DisplayError](400, "Bad Request Error")
      .responseWith[DisplayError](404, "Not Found Error")
      .responseWith[DisplayError](410, "Gone Error")
      .responseWith[DisplayError](500, "Internal Server Error")
      .parameter(includeSwaggerParam)
    // Deliberately undocumented: the index flag.  See above.
  }
}
