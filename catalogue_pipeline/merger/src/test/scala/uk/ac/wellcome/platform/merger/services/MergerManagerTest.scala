package uk.ac.wellcome.platform.merger.services

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.recorder.internal.RecorderWorkEntry
import uk.ac.wellcome.models.work.internal.{
  BaseWork,
  IdentifiableRedirect,
  UnidentifiedRedirectedWork,
  UnidentifiedWork
}
import uk.ac.wellcome.platform.merger.MergerTestUtils

class MergerManagerTest extends FunSpec with Matchers with MergerTestUtils {

  it("performs a merge with a single work") {
    val workEntry = createRecorderWorkEntry

    val result = mergerManager.applyMerge(
      maybeWorkEntries = List(Some(workEntry)))

    result shouldBe List(workEntry.work)
  }

  it("performs a merge with multiple works") {
    val workEntry = createRecorderWorkEntry
    val otherEntries = (1 to 3).map { _ =>
      createRecorderWorkEntry
    }

    val workEntries = (workEntry +: otherEntries).map { Some(_) }.toList

    val result = mergerManager.applyMerge(maybeWorkEntries = workEntries)

    result.head shouldBe workEntry.work

    result.tail.zip(otherEntries).map {
      case (baseWork: BaseWork, workEntry: RecorderWorkEntry) =>
        baseWork.sourceIdentifier shouldBe workEntry.work.sourceIdentifier

        val redirect = baseWork.asInstanceOf[UnidentifiedRedirectedWork]
        val redirectTarget = result.head.asInstanceOf[UnidentifiedWork]
        redirect.redirect.sourceIdentifier shouldBe redirectTarget.sourceIdentifier
    }
  }

  it("returns the works unmerged if any of the work entries are None") {
    val workEntries = (1 to 3).map { _ =>
      createRecorderWorkEntry
    }

    val result = mergerManager.applyMerge(
      maybeWorkEntries = workEntries.map { Some(_) }.toList ++ List(None))

    result should contain theSameElementsAs workEntries.map { _.work }
  }

  val mergerRules: MergerRules = new MergerRules {

    /** Make every work a redirect to the first work in the list, and leave
      * the first work intact.
      */
    override def merge(works: Seq[UnidentifiedWork]): Seq[BaseWork] =
      works.head +: works.tail.map { work =>
        UnidentifiedRedirectedWork(
          sourceIdentifier = work.sourceIdentifier,
          version = work.version,
          redirect = IdentifiableRedirect(works.head.sourceIdentifier)
        )
      }
  }

  val mergerManager = new MergerManager(mergerRules = mergerRules)
}
