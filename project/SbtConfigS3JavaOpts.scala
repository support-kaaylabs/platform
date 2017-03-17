package sbtconfigs3javaopts

import sbt._, Keys._

import scala.collection.JavaConverters._
import java.io.{File, InputStream}
import java.nio.file.{Files, Paths}
import java.nio.file.StandardCopyOption._
import com.typesafe.config._
import com.amazonaws.services.s3.AmazonS3ClientBuilder

object ConfigS3JavaOptsPlugin extends sbt.AutoPlugin {
  object autoImport {
    val configS3JavaOpts =
      taskKey[Seq[String]]("Containerise application for deployment.")
    val configS3Stage =
      SettingKey[String]("config-s3-stage", "Deploy environment.")
    val configS3Bucket =
      SettingKey[String]("config-s3-bucket", "Settings bucket.")
    val configS3App = SettingKey[String]("config-s3-app", "App name")

    lazy val baseConfigS3JavaOptsSettings: Seq[Def.Setting[_]] = Seq(
      configS3JavaOpts := {
        ConfigS3JavaOpts(
          (configS3Stage in configS3JavaOpts).value,
          (configS3Bucket in configS3JavaOpts).value,
          (configS3App in configS3JavaOpts).value
        )
      },
      configS3Stage in configS3JavaOpts := "dev",
      configS3Bucket in configS3JavaOpts := "config-bucket",
      configS3App in configS3JavaOpts := "api"
    )
  }
  import autoImport._
  override def trigger = allRequirements
  override val projectSettings = baseConfigS3JavaOptsSettings
}

object ConfigS3JavaOpts {
  def apply(stage: String, bucket: String, appName: String): Seq[String] = {
    if (!Files.exists(Paths.get(s"${appName}/conf/"))) return Nil

    val key = s"config/${stage}/${appName}.conf"
    val localPath = s"${appName}/conf/application.${stage}.conf"

    if (stage != "dev") {
      val s3Client = AmazonS3ClientBuilder.defaultClient()
      val s3Object = s3Client.getObject(bucket, key)

      val fileStream = s3Object.getObjectContent().asInstanceOf[InputStream]
      val targetFile = new File(localPath)
      val targetPath = targetFile.toPath()

      Files.copy(fileStream, targetPath, REPLACE_EXISTING)
      fileStream.close()
    }

    val confSet = ConfigFactory
      .parseFile(new File(localPath))
      .resolve()
      .entrySet()
      .asScala

    confSet
      .map(entry => {
        val key = entry.getKey()
        val value = entry.getValue().unwrapped()

        s"-${key}=${value}"
      })
      .toSeq

  }
}
