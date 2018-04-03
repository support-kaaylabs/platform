package uk.ac.wellcome.display.models

import com.fasterxml.jackson.core.{JsonParser, JsonProcessingException}
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{
  DeserializationContext,
  JsonDeserializer
}

case class WorksIncludes(
  identifiers: Boolean = false,
  thumbnail: Boolean = false,
  items: Boolean = false
)

class WorksIncludesParsingException(msg: String)
    extends JsonProcessingException(msg: String)

case object WorksIncludes {

  val recognisedIncludes = List("identifiers", "thumbnail", "items")

  /// Parse an ?includes query-parameter string.
  ///
  /// If any unexpected includes are spotted, we raise an
  /// `WorksIncludesParsingException`.
  def apply(queryParam: String): WorksIncludes = {
    val includesList = queryParam.split(",").toList
    val unrecognisedIncludes = includesList
      .filterNot(recognisedIncludes.contains)
    if (unrecognisedIncludes.isEmpty) {
      WorksIncludes(
        identifiers = includesList.contains("identifiers"),
        thumbnail = includesList.contains("thumbnail"),
        items = includesList.contains("items")
      )
    } else {
      val errorMessage = if (unrecognisedIncludes.length == 1) {
        s"'${unrecognisedIncludes.head}' is not a valid include"
      } else {
        s"${unrecognisedIncludes.mkString("'", "', '", "'")} are not valid includes"
      }
      throw new WorksIncludesParsingException(errorMessage)
    }
  }

  def apply(queryParam: Option[String]): WorksIncludes =
    queryParam match {
      case Some(s) => WorksIncludes(s)
      case None => WorksIncludes()
    }
}

/** Convenience wrapper that has every field on WorksIncludes set to true.
  *
  * We're piggybacking the string-parsing logic used for parsing includes from
  * a URL query string.  This isn't especially neat, but it means it always
  * stays up-to-date if/when we add new includes, and avoids the messiness of
  * doing reflection.
  */
object AllWorksIncludes {
  def apply(): WorksIncludes =
    WorksIncludes(queryParam = WorksIncludes.recognisedIncludes.mkString(","))
}

class WorksIncludesDeserializer extends JsonDeserializer[WorksIncludes] {
  override def deserialize(p: JsonParser,
                           ctxt: DeserializationContext): WorksIncludes =
    WorksIncludes(p.getText())
}

class WorksIncludesDeserializerModule extends SimpleModule {
  addDeserializer(classOf[WorksIncludes], new WorksIncludesDeserializer())
}