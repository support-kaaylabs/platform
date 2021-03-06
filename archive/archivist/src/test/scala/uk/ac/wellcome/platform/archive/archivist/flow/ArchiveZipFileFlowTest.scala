package uk.ac.wellcome.platform.archive.archivist.flow

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.platform.archive.archivist.fixtures.{
  Archivist => ArchivistFixture
}
import uk.ac.wellcome.platform.archive.archivist.models.{
  BagItConfig,
  BagUploaderConfig,
  IngestRequestContextGenerators,
  UploadConfig
}
import uk.ac.wellcome.storage.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.Akka

class ArchiveZipFileFlowTest
    extends FunSpec
    with Matchers
    with ScalaFutures
    with ArchivistFixture
    with IngestRequestContextGenerators
    with Akka {

  implicit val s3client = s3Client

  def createBagUploaderConfig(bucket: Bucket) =
    BagUploaderConfig(
      uploadConfig = UploadConfig(
        uploadNamespace = bucket.name
      ),
      parallelism = 10,
      bagItConfig = BagItConfig()
    )

  it("succeeds when verifying and uploading a valid bag") {
    withLocalS3Bucket { storageBucket =>
      withActorSystem { actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagUploaderConfig = createBagUploaderConfig(storageBucket)
          withBagItZip() {
            case (bagName, zipFile) =>
              val uploader = ArchiveZipFileFlow(bagUploaderConfig)
              val ingestContext = createIngestBagRequestWith()
              val (_, verification) =
                uploader.runWith(
                  Source.single(
                    ZipFileDownloadComplete(zipFile, ingestContext)),
                  Sink.seq
                )

              whenReady(verification) { result =>
                listKeysInBucket(storageBucket) should have size 4
                result should have size 1
              }
          }
        }
      }
    }
  }

  it("fails when verifying and uploading a bag with incorrect digests") {
    withLocalS3Bucket { storageBucket =>
      withActorSystem { actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagUploaderConfig = createBagUploaderConfig(storageBucket)
          withBagItZip(createDigest = _ => "bad_digest") {
            case (bagName, zipFile) =>
              val uploader = ArchiveZipFileFlow(bagUploaderConfig)
              val ingestContext = createIngestBagRequest

              val (_, verification) =
                uploader.runWith(
                  Source.single(
                    ZipFileDownloadComplete(zipFile, ingestContext)),
                  Sink.seq)

              whenReady(verification) { result =>
                result shouldBe empty
              }
          }
        }
      }
    }

  }

  it("fails when verifying and uploading a bag with no bagit.txt file") {
    withLocalS3Bucket { storageBucket =>
      withActorSystem { actorSystem =>
        withMaterializer(actorSystem) { implicit materializer =>
          val bagUploaderConfig = createBagUploaderConfig(storageBucket)
          withBagItZip(createBagItFile = _ => None) {
            case (bagName, zipFile) =>
              val uploader = ArchiveZipFileFlow(bagUploaderConfig)
              val ingestContext = createIngestBagRequest

              val (_, verification) =
                uploader.runWith(
                  Source.single(
                    ZipFileDownloadComplete(zipFile, ingestContext)),
                  Sink.seq)

              whenReady(verification) { result =>
                result shouldBe empty
              }
          }
        }
      }
    }

  }
}
