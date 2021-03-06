package uk.ac.wellcome.platform.matcher.messages

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.test.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.test.fixtures.SQS
import uk.ac.wellcome.messaging.test.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.matcher.{
  MatchedIdentifiers,
  MatcherResult,
  WorkIdentifier
}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.matcher.fixtures.MatcherFixtures
import uk.ac.wellcome.storage.fixtures.S3.Bucket

import scala.concurrent.ExecutionContext.Implicits.global

class MatcherMessageReceiverTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MatcherFixtures {

  private val aIdentifier = aSierraSourceIdentifier("A")
  private val bIdentifier = aSierraSourceIdentifier("B")
  private val cIdentifier = aSierraSourceIdentifier("C")

  it("creates a work without identifiers") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            // Work Av1 created without any matched works
            val updatedWork = anUnidentifiedSierraWork
            val expectedMatchedWorks =
              MatcherResult(
                Set(MatchedIdentifiers(
                  Set(WorkIdentifier("sierra-system-number/id", 1)))))

            processAndAssertMatchedWorkIs(
              updatedWork,
              expectedMatchedWorks,
              queue,
              storageBucket,
              topic)
          }
        }
      }
    }
  }

  it(
    "sends an invisible work as a single matched result with no other matched identifiers") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            val invisibleWork = createUnidentifiedInvisibleWork
            val workId = invisibleWork.sourceIdentifier.toString
            val expectedMatchedWorks =
              MatcherResult(
                Set(
                  MatchedIdentifiers(
                    Set(WorkIdentifier(identifier = workId, version = 1))
                  ))
              )

            processAndAssertMatchedWorkIs(
              workToMatch = invisibleWork,
              expectedMatchedWorks = expectedMatchedWorks,
              queue = queue,
              storageBucket = storageBucket,
              topic = topic
            )
          }
        }
      }
    }
  }

  it(
    "work A with one link to B and no existing works returns a single matched work") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            // Work Av1
            val workAv1 =
              createUnidentifiedWorkWith(
                sourceIdentifier = aIdentifier,
                mergeCandidates = List(MergeCandidate(bIdentifier)))
            // Work Av1 matched to B (before B exists hence version 0)
            // need to match to works that do not exist to support
            // bi-directionally matched works without deadlocking (A->B, B->A)
            val expectedMatchedWorks = MatcherResult(
              Set(
                MatchedIdentifiers(Set(
                  WorkIdentifier("sierra-system-number/A", 1),
                  WorkIdentifier("sierra-system-number/B", 0)))))

            processAndAssertMatchedWorkIs(
              workAv1,
              expectedMatchedWorks,
              queue,
              storageBucket,
              topic)
          }
        }
      }
    }
  }

  it(
    "matches a work with one link then matches the combined work to a new work") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            // Work Av1
            val workAv1 =
              createUnidentifiedWorkWith(sourceIdentifier = aIdentifier)

            val expectedMatchedWorks = MatcherResult(
              Set(
                MatchedIdentifiers(Set(
                  WorkIdentifier("sierra-system-number/A", 1)
                ))))

            processAndAssertMatchedWorkIs(
              workAv1,
              expectedMatchedWorks,
              queue,
              storageBucket,
              topic)

            // Work Bv1
            val workBv1 =
              createUnidentifiedWorkWith(sourceIdentifier = bIdentifier)

            processAndAssertMatchedWorkIs(
              workBv1,
              MatcherResult(Set(MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/B", 1))))),
              queue,
              storageBucket,
              topic)

            // Work Av1 matched to B
            val workAv2 = createUnidentifiedWorkWith(
              sourceIdentifier = aIdentifier,
              version = 2,
              mergeCandidates = List(MergeCandidate(bIdentifier)))

            processAndAssertMatchedWorkIs(
              workAv2,
              MatcherResult(
                Set(
                  MatchedIdentifiers(Set(
                    WorkIdentifier("sierra-system-number/A", 2),
                    WorkIdentifier("sierra-system-number/B", 1))))),
              queue,
              storageBucket,
              topic
            )

            // Work Cv1
            val workCv1 =
              createUnidentifiedWorkWith(sourceIdentifier = cIdentifier)

            processAndAssertMatchedWorkIs(
              workCv1,
              MatcherResult(Set(MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/C", 1))))),
              queue,
              storageBucket,
              topic)

            // Work Bv2 matched to C
            val workBv2 = createUnidentifiedWorkWith(
              sourceIdentifier = bIdentifier,
              version = 2,
              mergeCandidates = List(MergeCandidate(cIdentifier)))

            processAndAssertMatchedWorkIs(
              workBv2,
              MatcherResult(
                Set(
                  MatchedIdentifiers(
                    Set(
                      WorkIdentifier("sierra-system-number/A", 2),
                      WorkIdentifier("sierra-system-number/B", 2),
                      WorkIdentifier("sierra-system-number/C", 1))))),
              queue,
              storageBucket,
              topic
            )
          }
        }
      }
    }
  }

  it("breaks matched works into individual works") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueue { queue =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
            // Work Av1
            val workAv1 = createUnidentifiedWorkWith(
              sourceIdentifier = aIdentifier,
              version = 1)

            processAndAssertMatchedWorkIs(
              workAv1,
              MatcherResult(Set(MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/A", 1))))),
              queue,
              storageBucket,
              topic)

            // Work Bv1
            val workBv1 = createUnidentifiedWorkWith(
              sourceIdentifier = bIdentifier,
              version = 1)

            processAndAssertMatchedWorkIs(
              workBv1,
              MatcherResult(Set(MatchedIdentifiers(
                Set(WorkIdentifier("sierra-system-number/B", 1))))),
              queue,
              storageBucket,
              topic)

            // Match Work A to Work B
            val workAv2MatchedToB = createUnidentifiedWorkWith(
              sourceIdentifier = aIdentifier,
              version = 2,
              mergeCandidates = List(MergeCandidate(bIdentifier)))

            processAndAssertMatchedWorkIs(
              workAv2MatchedToB,
              MatcherResult(
                Set(
                  MatchedIdentifiers(Set(
                    WorkIdentifier("sierra-system-number/A", 2),
                    WorkIdentifier("sierra-system-number/B", 1))))),
              queue,
              storageBucket,
              topic
            )

            // A no longer matches B
            val workAv3WithNoMatchingWorks = createUnidentifiedWorkWith(
              sourceIdentifier = aIdentifier,
              version = 3)

            processAndAssertMatchedWorkIs(
              workAv3WithNoMatchingWorks,
              MatcherResult(
                Set(
                  MatchedIdentifiers(
                    Set(WorkIdentifier("sierra-system-number/A", 3))),
                  MatchedIdentifiers(
                    Set(WorkIdentifier("sierra-system-number/B", 1))))),
              queue,
              storageBucket,
              topic
            )
          }
        }
      }
    }
  }

  it("does not match a lower version") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueueAndDlq { queuePair =>
        withLocalS3Bucket { storageBucket =>
          withMatcherMessageReceiver(queuePair.queue, storageBucket, topic) {
            _ =>
              // process Work V2
              val workAv2 = createUnidentifiedWorkWith(
                sourceIdentifier = aIdentifier,
                version = 2
              )

              val expectedMatchedWorkAv2 = MatcherResult(
                Set(MatchedIdentifiers(
                  Set(WorkIdentifier("sierra-system-number/A", 2)))))

              processAndAssertMatchedWorkIs(
                workAv2,
                expectedMatchedWorkAv2,
                queuePair.queue,
                storageBucket,
                topic)

              // Work V1 is sent but not matched
              val workAv1 = createUnidentifiedWorkWith(
                sourceIdentifier = aIdentifier,
                version = 1)

              sendMessage[TransformedBaseWork](
                bucket = storageBucket,
                queue = queuePair.queue,
                workAv1)
              eventually {
                noMessagesAreWaitingIn(queuePair.queue)
                noMessagesAreWaitingIn(queuePair.dlq)
                assertLastMatchedResultIs(
                  topic = topic,
                  expectedMatcherResult = expectedMatchedWorkAv2
                )
              }
          }
        }
      }
    }
  }

  it("does not match an existing version with different information") {
    withLocalSnsTopic { topic =>
      withLocalSqsQueueAndDlq {
        case QueuePair(queue, dlq) =>
          withLocalS3Bucket { storageBucket =>
            withMatcherMessageReceiver(queue, storageBucket, topic) { _ =>
              val workAv2 = createUnidentifiedWorkWith(
                sourceIdentifier = aIdentifier,
                version = 2
              )

              val expectedMatchedWorkAv2 = MatcherResult(
                Set(MatchedIdentifiers(
                  Set(WorkIdentifier("sierra-system-number/A", 2)))))

              processAndAssertMatchedWorkIs(
                workAv2,
                expectedMatchedWorkAv2,
                queue,
                storageBucket,
                topic)

              // Work V1 is sent but not matched
              val differentWorkAv2 = createUnidentifiedWorkWith(
                sourceIdentifier = aIdentifier,
                mergeCandidates = List(MergeCandidate(bIdentifier)),
                version = 2)

              sendMessage[TransformedBaseWork](
                bucket = storageBucket,
                queue = queue,
                differentWorkAv2
              )
              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, 1)
              }
            }
          }
      }
    }
  }

  private def processAndAssertMatchedWorkIs(workToMatch: TransformedBaseWork,
                                            expectedMatchedWorks: MatcherResult,
                                            queue: SQS.Queue,
                                            storageBucket: Bucket,
                                            topic: Topic): Any = {
    sendMessage(bucket = storageBucket, queue = queue, workToMatch)
    eventually {
      assertLastMatchedResultIs(
        topic = topic,
        expectedMatcherResult = expectedMatchedWorks
      )
    }
  }

  private def assertLastMatchedResultIs(
    topic: Topic,
    expectedMatcherResult: MatcherResult) = {

    val snsMessages = listMessagesReceivedFromSNS(topic)
    snsMessages.size should be >= 1

    val actualMatcherResults = snsMessages.map { snsMessage =>
      fromJson[MatcherResult](snsMessage.message).get
    }
    actualMatcherResults.last shouldBe expectedMatcherResult
  }
}
