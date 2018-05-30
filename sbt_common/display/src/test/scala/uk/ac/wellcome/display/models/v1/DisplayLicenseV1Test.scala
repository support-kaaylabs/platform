package uk.ac.wellcome.display.models.v1

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.License_CCBY

class DisplayLicenseV1Test extends FunSpec with Matchers {

  it("should read a License as a DisplayLicenseV1 correctly") {
    val displayLicense = DisplayLicenseV1(License_CCBY)

    displayLicense.licenseType shouldBe "CC-BY"
    displayLicense.label shouldBe "Attribution 4.0 International (CC BY 4.0)"
    displayLicense.url shouldBe "http://creativecommons.org/licenses/by/4.0/"
    displayLicense.ontologyType shouldBe "License"
  }
}
