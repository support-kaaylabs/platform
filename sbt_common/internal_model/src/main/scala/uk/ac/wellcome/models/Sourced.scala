package uk.ac.wellcome.models

trait Sourced {
  val sourceId: String
  val sourceName: String
  def id: String = Sourced.id(sourceName, sourceId)
}

object Sourced {
  def id(sourceName: String, sourceId: String) = s"$sourceName/$sourceId"
}
