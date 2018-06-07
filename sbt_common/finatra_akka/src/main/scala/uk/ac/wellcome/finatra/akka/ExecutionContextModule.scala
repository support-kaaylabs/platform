package uk.ac.wellcome.finatra.akka

import javax.inject.Singleton
import akka.actor.ActorSystem
import com.google.inject.Provides
import com.twitter.inject.TwitterModule

import scala.concurrent.ExecutionContext

object ExecutionContextModule extends TwitterModule {
  @Provides
  @Singleton
  def provideExecutionContext(actorSystem: ActorSystem): ExecutionContext =
    actorSystem.dispatcher
}