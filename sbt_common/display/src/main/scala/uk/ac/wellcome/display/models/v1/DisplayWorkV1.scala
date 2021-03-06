package uk.ac.wellcome.display.models.v1

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal.{
  AbstractAgent,
  Contributor,
  Displayable,
  IdentifiedWork
}

@ApiModel(
  value = "Work",
  description =
    "An individual work such as a text, archive item or picture; or a grouping of individual works (so, for instance, an archive collection counts as a work, as do all the series and individual files within it).  Each work may exist in multiple instances (e.g. copies of the same book).  N.B. this is not synonymous with \\\"work\\\" as that is understood in the International Federation of Library Associations and Institutions' Functional Requirements for Bibliographic Records model (FRBR) but represents something lower down the FRBR hierarchy, namely manifestation. Groups of related items are also included as works because they have similar properties to the individual ones."
)
case class DisplayWorkV1(
  @ApiModelProperty(
    readOnly = true,
    value = "The canonical identifier given to a thing."
  ) id: String,
  @ApiModelProperty(value =
    "The title or other short label of a work, including labels not present in the actual work or item but applied by the cataloguer for the purposes of search or description.") title: String,
  @ApiModelProperty(
    dataType = "String",
    value = "A description given to a thing."
  ) description: Option[String] = None,
  @ApiModelProperty(
    dataType = "String",
    value = "A description of specific physical characteristics of the work."
  ) physicalDescription: Option[String] = None,
  @ApiModelProperty(
    dataType = "uk.ac.wellcome.display.models.DisplayWorkType",
    value = "The type of work."
  ) workType: Option[DisplayWorkType] = None,
  @ApiModelProperty(
    dataType = "String",
    value =
      "Number of physical pages, volumes, cassettes, total playing time, etc., of of each type of unit"
  ) extent: Option[String] = None,
  @ApiModelProperty(
    dataType = "String",
    value = "Recording written text on a (usually visual) work."
  ) lettering: Option[String] = None,
  @ApiModelProperty(
    dataType = "uk.ac.wellcome.display.models.v1.DisplayPeriodV1",
    value =
      "Relates the creation of a work to a date, when the date of creation does not cover a range."
  ) createdDate: Option[DisplayPeriodV1] = None,
  @ApiModelProperty(
    value =
      "Relates a work to its author, compiler, editor, artist or other entity responsible for its coming into existence in the form that it has."
  ) creators: List[DisplayAgentV1] = List(),
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v1.DisplayIdentifierV1]",
    value =
      "Relates the item to a unique system-generated identifier that governs interaction between systems and is regarded as canonical within the Wellcome data ecosystem."
  ) identifiers: Option[List[DisplayIdentifierV1]] = None,
  @ApiModelProperty(
    value =
      "Relates a work to the general thesaurus-based concept that describes the work's content."
  ) subjects: List[DisplayConceptV1] = List(),
  @ApiModelProperty(
    value = "Relates a work to the genre that describes the work's content."
  ) genres: List[DisplayConceptV1] = List(),
  @ApiModelProperty(
    dataType = "uk.ac.wellcome.display.models.v1.DisplayLocationV1",
    value =
      "Relates any thing to the location of a representative thumbnail image"
  ) thumbnail: Option[DisplayLocationV1] = None,
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v1.DisplayItemV1]",
    value = "List of items related to this work."
  ) items: Option[List[DisplayItemV1]] = None,
  @ApiModelProperty(
    value = "Relates a published work to its publisher."
  ) publishers: List[DisplayAgentV1] = List(),
  @ApiModelProperty(
    dataType = "List[uk.ac.wellcome.display.models.v1.DisplayPlaceV1]",
    value = "Show a list of places of publication."
  ) placesOfPublication: List[DisplayPlaceV1] = List(),
  @ApiModelProperty(
    dataType = "uk.ac.wellcome.display.models.v1.DisplayPeriodV1",
    value =
      "Relates the publication of a work to a date when the work has been formally published."
  ) publicationDate: Option[DisplayPeriodV1] = None,
  @ApiModelProperty(
    dataType = "uk.ac.wellcome.display.models.DisplayLanguage",
    value = "Relates a work to its primary language."
  ) language: Option[DisplayLanguage] = None,
  @ApiModelProperty(
    dataType = "String"
  ) dimensions: Option[String] = None
) extends DisplayWork {
  @ApiModelProperty(
    readOnly = true,
    value =
      "A broad, top-level description of the form of a work: namely, whether it is a printed book, archive, painting, photograph, moving image, etc."
  )
  @JsonProperty("type") val ontologyType: String = "Work"
}

case object DisplayWorkV1 {
  def apply(work: IdentifiedWork, includes: V1WorksIncludes): DisplayWorkV1 = {

    // The "production" field on work contains information that should go
    // into the publisher-specific fields.
    //
    // In practice, the V1 display model should only be used to serialise Miro
    // data, which never populates the production field -- so rather than
    // writing and testing code to tease it out, just error here instead.
    if (work.production != Nil) {
      throw new IllegalArgumentException(
        s"IdentifiedWork ${work.canonicalId} has production fields set, cannot be converted to a V1 DisplayWork"
      )
    }

    DisplayWorkV1(
      id = work.canonicalId,
      title = work.title,
      description = work.description,
      physicalDescription = work.physicalDescription,
      extent = work.extent,
      lettering = work.lettering,
      createdDate = work.createdDate.map { DisplayPeriodV1(_) },
      creators = work.contributors.map {
        contributor: Contributor[Displayable[AbstractAgent]] =>
          DisplayAgentV1(contributor.agent)
      },
      subjects = work.subjects.flatMap { subject =>
        subject.concepts.map { DisplayConceptV1(_) }
      },
      genres = work.genres.flatMap { genre =>
        genre.concepts.map { DisplayConceptV1(_) }
      },
      identifiers =
        if (includes.identifiers)
          Some(work.identifiers.map { DisplayIdentifierV1(_) })
        else None,
      workType = work.workType.map { DisplayWorkType(_) },
      thumbnail =
        if (includes.thumbnail)
          work.thumbnail.map { DisplayLocationV1(_) } else None,
      items =
        if (includes.items)
          Some(work.itemsV1.map {
            DisplayItemV1(_, includesIdentifiers = includes.identifiers)
          })
        else None,
      publishers = List(),
      publicationDate = None,
      placesOfPublication = List(),
      language = work.language.map { DisplayLanguage(_) },
      dimensions = work.dimensions
    )
  }

  def apply(work: IdentifiedWork): DisplayWorkV1 =
    DisplayWorkV1(work = work, includes = V1WorksIncludes())
}
