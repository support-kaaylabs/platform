package uk.ac.wellcome.platform.ingestor

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.{
  CommonFilters,
  LoggingMDCFilter,
  TraceIdMDCFilter
}
import com.twitter.finatra.http.routing.HttpRouter
import uk.ac.wellcome.finatra.akka.{AkkaModule, ExecutionContextModule}
import uk.ac.wellcome.finatra.controllers.ManagementController
import uk.ac.wellcome.finatra.elasticsearch.ElasticClientModule
import uk.ac.wellcome.finatra.messaging.{
  MessageReaderConfigModule,
  SQSClientModule
}
import uk.ac.wellcome.finatra.monitoring.MetricsSenderModule
import uk.ac.wellcome.finatra.storage.S3ClientModule
import uk.ac.wellcome.platform.ingestor.modules._

object ServerMain extends Server

class Server extends HttpServer {
  override val name = "uk.ac.wellcome.platform.ingestor Ingestor"
  override val modules = Seq(
    MessageReaderConfigModule,
    MetricsSenderModule,
    SQSClientModule,
    S3ClientModule,
    AkkaModule,
    IngestorWorkerModule,
    ElasticClientModule,
    IngestElasticConfigModule,
    ExecutionContextModule,
    WorksIndexModule,
    IdentifiedBaseWorkModule,
    IngestorConfigModule
  )

  override def configureHttp(router: HttpRouter) {
    router
      .filter[CommonFilters]
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .add[ManagementController]
  }
}
