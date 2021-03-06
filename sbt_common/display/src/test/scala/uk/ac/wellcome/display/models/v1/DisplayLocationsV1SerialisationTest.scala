package uk.ac.wellcome.display.models.v1

import org.scalatest.FunSpec
import uk.ac.wellcome.display.models.V1WorksIncludes
import uk.ac.wellcome.display.test.util.JsonMapperTestUtil
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.test.util.WorksGenerators

class DisplayLocationsV1SerialisationTest
    extends FunSpec
    with DisplayV1SerialisationTestBase
    with JsonMapperTestUtil
    with WorksGenerators {

  it("serialises a physical location") {
    val physicalLocation = PhysicalLocation(
      locationType = LocationType("sgmed"),
      label = "a stack of slick slimes"
    )

    val work = createIdentifiedWorkWith(
      itemsV1 = List(
        createIdentifiedItem(locations = List(physicalLocation))
      )
    )
    val displayWork =
      DisplayWorkV1(work, includes = V1WorksIncludes(items = true))

    val actualJson = objectMapper.writeValueAsString(displayWork)
    val expectedJson = s"""
                            |{
                            |  "type": "Work",
                            |  "id": "${work.canonicalId}",
                            |  "title": "${work.title}",
                            |  "creators": [ ],
                            |  "items": [ ${items(work.itemsV1)} ],
                            |  "subjects": [ ],
                            |  "genres": [ ],
                            |  "publishers": [],
                            |  "placesOfPublication": [ ]
                            |}""".stripMargin

    assertJsonStringsAreEqual(actualJson, expectedJson)
  }

  it("serialises a digital location") {
    val digitalLocation = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType("iiif-image")
    )

    val work = createIdentifiedWorkWith(
      itemsV1 = List(createIdentifiedItem(locations = List(digitalLocation)))
    )

    val displayWork =
      DisplayWorkV1(work, includes = V1WorksIncludes(items = true))

    val actualJson = objectMapper.writeValueAsString(displayWork)
    val expectedJson = s"""
                          |{
                          |  "type": "Work",
                          |  "id": "${work.canonicalId}",
                          |  "title": "${work.title}",
                          |  "creators": [ ],
                          |  "items": [ ${items(work.itemsV1)} ],
                          |  "subjects": [ ],
                          |  "genres": [ ],
                          |  "publishers": [],
                          |  "placesOfPublication": [ ]
                          |}""".stripMargin
    assertJsonStringsAreEqual(actualJson, expectedJson)
  }

  it("serialises a digital location with a license") {
    val digitalLocation = DigitalLocation(
      url = "https://wellcomelibrary.org/iiif/b22015085/manifest",
      locationType = LocationType("iiif-image"),
      license = Some(License_CC0)
    )

    val work = createIdentifiedWorkWith(
      itemsV1 = List(createIdentifiedItem(locations = List(digitalLocation)))
    )

    val displayWork =
      DisplayWorkV1(work, includes = V1WorksIncludes(items = true))

    val actualJson = objectMapper.writeValueAsString(displayWork)

    val expectedJson = s"""
                          |{
                          |  "type": "Work",
                          |  "id": "${work.canonicalId}",
                          |  "title": "${work.title}",
                          |  "creators": [ ],
                          |  "items": [ ${items(work.itemsV1)} ],
                          |  "subjects": [ ],
                          |  "genres": [ ],
                          |  "publishers": [],
                          |  "placesOfPublication": [ ]
                          |}""".stripMargin

    assertJsonStringsAreEqual(actualJson, expectedJson)
  }
}
