package uk.ac.wellcome.storage.s3

import com.amazonaws.services.s3.model.AmazonS3Exception
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Assertion, FunSpec, Matchers}
import uk.ac.wellcome.storage.test.fixtures.S3
import uk.ac.wellcome.storage.test.fixtures.S3.Bucket
import uk.ac.wellcome.test.fixtures.TestWith
import uk.ac.wellcome.test.utils.{ExtendedPatience, JsonTestUtil}
import uk.ac.wellcome.utils.JsonUtil
import uk.ac.wellcome.utils.JsonUtil._

import scala.concurrent.ExecutionContext.Implicits.global

case class TestObject(content: String)

class S3TypeStoreTest
  extends FunSpec
    with S3
    with Matchers
    with JsonTestUtil
    with ScalaFutures
    with ExtendedPatience {

  val content = "Some content!"
  val expectedHash = "1770874231"

  it("stores a versioned object with path id/version/hash") {
    withLocalS3Bucket { bucket =>
      withS3TypeStore(bucket) { objectStore =>
        val content = "Some content!"
        val prefix = "foo"

        val testObject = TestObject(content = content)

        val writtenToS3 = objectStore.put(bucket.name)(testObject, prefix)

        whenReady(writtenToS3) { actualKey =>
          val expectedJson = JsonUtil.toJson(testObject).get

          val expectedKey = s"$prefix/$expectedHash.json"
          val expectedUri = S3ObjectLocation(bucket.name, expectedKey)

          actualKey shouldBe expectedUri

          val jsonFromS3 = getJsonFromS3(
            bucket,
            expectedKey
          ).noSpaces

          assertJsonStringsAreEqual(jsonFromS3, expectedJson)
        }
      }
    }
  }

  it("removes leading slashes from prefixes") {
    withLocalS3Bucket { bucket =>
      withS3TypeStore(bucket) { objectStore =>
        val prefix = "/foo"

        val testObject = TestObject(content = content)
        val writtenToS3 = objectStore.put(bucket.name)(testObject, prefix)

        whenReady(writtenToS3) { actualKey =>
          val expectedUri =
            S3ObjectLocation(bucket.name, s"foo/$expectedHash.json")
          actualKey shouldBe expectedUri
        }
      }
    }
  }

  it("removes trailing slashes from prefixes") {
    withLocalS3Bucket { bucket =>
      withS3TypeStore(bucket) { objectStore =>
        val prefix = "foo/"

        val testObject = TestObject(content = content)
        val writtenToS3 = objectStore.put(bucket.name)(testObject, prefix)

        whenReady(writtenToS3) { actualKey =>
          val expectedUri =
            S3ObjectLocation(bucket.name, s"foo/$expectedHash.json")
          actualKey shouldBe expectedUri
        }
      }
    }
  }

  it("retrieves a versioned object from s3") {
    withLocalS3Bucket { bucket =>
      withS3TypeStore(bucket) { objectStore =>
        val prefix = "foo"

        val testObject = TestObject(content = content)

        val writtenToS3 = objectStore.put(bucket.name)(testObject, prefix)

        whenReady(writtenToS3.flatMap(objectStore.get)) { actualTestObject =>
          actualTestObject shouldBe testObject
        }
      }
    }
  }

  it("throws an exception when retrieving a missing object") {
    withLocalS3Bucket { bucket =>
      withS3TypeStore(bucket) { objectStore =>
        whenReady(
          objectStore
            .get(S3ObjectLocation(bucket.name, "not/a/real/object"))
            .failed) { exception =>
          exception shouldBe a[AmazonS3Exception]
          exception
            .asInstanceOf[AmazonS3Exception]
            .getErrorCode shouldBe "NoSuchKey"

        }
      }
    }
  }

  private def withS3TypeStore(bucket: Bucket)(
    testWith: TestWith[S3TypeStore[TestObject], Assertion]) = {
    val s3TypeStore = new S3TypeStore[TestObject](
      s3Client = s3Client
    )

    testWith(s3TypeStore)
  }
}
