package pages

import java.time.LocalDate

import org.scalacheck.Arbitrary
import pages.behaviours.PageBehaviours

class ChargeDetailsPageSpec extends PageBehaviours {

  "ChargeDetailsPage" - {

    implicit lazy val arbitraryLocalDate: Arbitrary[LocalDate] = Arbitrary {
      datesBetween(LocalDate.of(1900, 1, 1), LocalDate.of(2100, 1, 1))
    }

    beRetrievable[LocalDate](ChargeDetailsPage)

    beSettable[LocalDate](ChargeDetailsPage)

    beRemovable[LocalDate](ChargeDetailsPage)
  }
}
