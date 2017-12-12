package uk.ac.wellcome.platform.sierra_bibs_to_dynamo.modules

import akka.actor.ActorSystem
import com.twitter.inject.{Injector, TwitterModule}
import uk.ac.wellcome.platform.sierra_bibs_to_dynamo.services.SierraBibsToDynamoWorkerService

object SierraBibsToDynamoModule extends TwitterModule {
  flag[String]("sierra.apiUrl", "", "Sierra API url")
  flag[String]("sierra.oauthKey", "", "Sierra API oauth key")
  flag[String]("sierra.oauthSecret", "", "Sierra API oauth secret")
  flag[String]("sierra.fields",
               "",
               "List of fields to include in the Sierra API response")

  override def singletonStartup(injector: Injector) {
    val workerService = injector.instance[SierraBibsToDynamoWorkerService]

    workerService.runSQSWorker()

    super.singletonStartup(injector)
  }

  override def singletonShutdown(injector: Injector) {
    info("Terminating Sierra to Dynamo worker")

    val system = injector.instance[ActorSystem]
    val workerService = injector.instance[SierraBibsToDynamoWorkerService]

    workerService.cancelRun()
    system.terminate()
  }
}