import sbt._
import Keys._

object Dependencies {

  lazy val versions = new {
    val finatra = "2.7.0"
    val guice = "4.0"
    val logback = "1.1.8"
    val mockito = "1.9.5"
    val scalatest = "3.0.1"
    val junitInterface = "0.11"
    val elastic4s = "5.1.5"
  }

  val commonDependencies: Seq[ModuleID] = Seq(
    "com.twitter" %% "finatra-http" % versions.finatra,
    "com.twitter" %% "finatra-httpclient" % versions.finatra,
    "ch.qos.logback" % "logback-classic" % versions.logback,

    "com.twitter" %% "finatra-http" % versions.finatra % "test",
    "com.twitter" %% "finatra-jackson" % versions.finatra % "test",
    "com.twitter" %% "inject-server" % versions.finatra % "test",
    "com.twitter" %% "inject-app" % versions.finatra % "test",
    "com.twitter" %% "inject-core" % versions.finatra % "test",
    "com.twitter" %% "inject-modules" % versions.finatra % "test",
    "com.google.inject.extensions" % "guice-testlib" % versions.guice % "test",

    "com.twitter" %% "finatra-http" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "finatra-jackson" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-server" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-app" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-core" % versions.finatra % "test" classifier "tests",
    "com.twitter" %% "inject-modules" % versions.finatra % "test" classifier "tests",

    "org.mockito" % "mockito-core" % versions.mockito % "test",
    "org.scalatest" %% "scalatest" % versions.scalatest % "test",
    "com.novocode" % "junit-interface" % versions.junitInterface % "test"
  )

  val esDependencies: Seq[ModuleID] = Seq(
    "com.sksamuel.elastic4s" %% "elastic4s-core" % versions.elastic4s,
    "com.sksamuel.elastic4s" %% "elastic4s-xpack-security" % versions.elastic4s
  )

  val apiDependencies: Seq[ModuleID] = commonDependencies ++ esDependencies

}
