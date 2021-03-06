package uk.ac.wellcome.platform.reindex.creator.exceptions

import uk.ac.wellcome.exceptions.GracefulFailureException

case class ReindexerException(e: Throwable) extends GracefulFailureException

case object ReindexerException {
  def apply(message: String): ReindexerException =
    ReindexerException(new RuntimeException(message))
}
