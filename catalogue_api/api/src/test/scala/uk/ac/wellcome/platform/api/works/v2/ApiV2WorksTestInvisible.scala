package uk.ac.wellcome.platform.api.works.v2

import com.twitter.finagle.http.Status
import com.twitter.finatra.http.EmbeddedHttpServer
import uk.ac.wellcome.display.models.ApiVersions
import uk.ac.wellcome.models.work.internal.IdentifiedBaseWork

class ApiV2WorksTestInvisible extends ApiV2WorksTestBase {
  def withV2Api[R] = withApiFixtures[R](ApiVersions.v2)(_)

  it("returns an HTTP 410 Gone if looking up a work with visible = false") {
    withV2Api {
      case (apiPrefix, _, indexNameV2, itemType, server: EmbeddedHttpServer) =>
        val work = createIdentifiedInvisibleWork

        insertIntoElasticsearch(indexNameV2, itemType, work)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works/${work.canonicalId}",
            andExpect = Status.Gone,
            withJsonBody = gone(apiPrefix)
          )
        }
    }
  }

  it("excludes works with visible=false from list results") {
    withV2Api {
      case (apiPrefix, _, indexNameV2, itemType, server: EmbeddedHttpServer) =>
        val deletedWork = createIdentifiedInvisibleWork

        // Then we index two ordinary works into Elasticsearch.
        val works = createWorks(2)

        val worksToIndex = Seq[IdentifiedBaseWork](deletedWork) ++ works
        insertIntoElasticsearch(indexNameV2, itemType, worksToIndex: _*)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(apiPrefix, totalResults = 2)},
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${works(0).canonicalId}",
               |     "title": "${works(0).title}",
               |     "description": "${works(0).description.get}",
               |     "workType" : ${workType(works(0).workType.get)},
               |     "lettering": "${works(0).lettering.get}",
               |     "createdDate": ${period(works(0).createdDate.get)},
               |     "contributors": [${contributor(works(0).contributors(0))}],
               |     "subjects": [ ],
               |     "genres": [ ],
               |     "production": [ ]
               |   },
               |   {
               |     "type": "Work",
               |     "id": "${works(1).canonicalId}",
               |     "title": "${works(1).title}",
               |     "description": "${works(1).description.get}",
               |     "workType" : ${workType(works(1).workType.get)},
               |     "lettering": "${works(1).lettering.get}",
               |     "createdDate": ${period(works(1).createdDate.get)},
               |     "contributors": [${contributor(works(1).contributors(0))}],
               |     "subjects": [ ],
               |     "genres": [ ],
               |     "production": [ ]
               |   }
               |  ]
               |}
          """.stripMargin
          )
        }
    }
  }

  it("excludes works with visible=false from search results") {
    withV2Api {
      case (apiPrefix, _, indexNameV2, itemType, server: EmbeddedHttpServer) =>
        val work = workWith(
          canonicalId = "r8dx6std",
          title = "A deleted dodo"
        )
        val deletedWork = createIdentifiedInvisibleWork
        insertIntoElasticsearch(indexNameV2, itemType, work, deletedWork)

        eventually {
          server.httpGet(
            path = s"/$apiPrefix/works?query=deleted",
            andExpect = Status.Ok,
            withJsonBody = s"""
               |{
               |  ${resultList(apiPrefix)},
               |  "results": [
               |   {
               |     "type": "Work",
               |     "id": "${work.canonicalId}",
               |     "title": "${work.title}",
               |     "contributors": [],
               |     "subjects": [ ],
               |     "genres": [ ],
               |     "production": [ ]
               |   }
               |  ]
               |}""".stripMargin
          )
        }
    }
  }
}
