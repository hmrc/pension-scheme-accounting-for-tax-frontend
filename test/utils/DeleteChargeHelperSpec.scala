/*
 * Copyright 2020 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import models.UserAnswers
import models.chargeA.ChargeDetails
import models.chargeB.ChargeBDetails
import org.scalatest.{FreeSpec, MustMatchers, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeA.{ShortServiceRefundQuery, ChargeDetailsPage => chargeADetailsPage}
import pages.chargeB.{ChargeBDetailsPage, SpecialDeathBenefitsQuery}
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage}
import pages.chargeD.{ChargeDetailsPage => chargeDDetailsPage, MemberDetailsPage => chargeDMemberDetailsPage}
import pages.chargeE.{ChargeDetailsPage => chargeEDetailsPage, MemberDetailsPage => chargeEMemberDetailsPage}
import pages.chargeF.{DeregistrationQuery, ChargeDetailsPage => chargeFDetailsPage}
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage => chargeGMemberDetailsPage}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results

class DeleteChargeHelperSpec extends FreeSpec with MustMatchers with OptionValues with MockitoSugar with Results {

  import DeleteChargeHelperSpec._

  private val deleteChargeHelper = new DeleteChargeHelper

  "zeroOutLastCharge" - {
    " must zero out the amounts for scheme level charges when user answers have only " - {
      "charge A" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeAUa))
        result.get(chargeADetailsPage).value mustBe ChargeDetails(2, Some(0), Some(0), 0)
      }

      "charge B" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeBUa))
        result.get(ChargeBDetailsPage).value mustBe ChargeBDetails(4, 0)
      }

      "charge F" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeFUa))
        result.get(chargeFDetailsPage).value.amountTaxDue mustBe 0
      }
    }

    " must zero out the amounts and set the isDeleted flag to false for member level charges when user answers have only " - {
      "charge C" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeCUa))
        result.get(ChargeCDetailsPage(0)).value.amountTaxDue mustBe 0
        result.get(SponsoringIndividualDetailsPage(0)).value.isDeleted mustBe false
      }

      "charge D" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeDUa))
        result.get(chargeDDetailsPage(0)).value.taxAt25Percent.value mustBe 0
        result.get(chargeDDetailsPage(0)).value.taxAt55Percent.value mustBe 0
        result.get(chargeDMemberDetailsPage(0)).value.isDeleted mustBe false
      }

      "charge E" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeEUa()))
        result.get(chargeEDetailsPage(0)).value.chargeAmount mustBe 0
        result.get(chargeEMemberDetailsPage(0)).value.isDeleted mustBe false
      }

      "charge G" in {
        val result = deleteChargeHelper.zeroOutLastCharge(UserAnswers(onlyChargeGUa))
        result.get(ChargeAmountsPage(0)).value.amountTaxDue mustBe 0
        result.get(ChargeAmountsPage(0)).value.amountTransferred mustBe 0
        result.get(chargeGMemberDetailsPage(0)).value.isDeleted mustBe false
      }
    }
  }

  "zeroOutCharge" - {
    " must zero out the amounts for scheme level charges for a specific charge " - {
      "charge A" in {
        val result = deleteChargeHelper.zeroOutCharge(ShortServiceRefundQuery, UserAnswers(allSchemeLevelCharges))
        result.get(chargeADetailsPage).value mustBe ChargeDetails(2, Some(0), Some(0), 0)
      }

      "charge B" in {
        val result = deleteChargeHelper.zeroOutCharge(SpecialDeathBenefitsQuery, UserAnswers(allSchemeLevelCharges))
        result.get(ChargeBDetailsPage).value mustBe ChargeBDetails(4, 0)
      }

      "charge F" in {
        val result = deleteChargeHelper.zeroOutCharge(DeregistrationQuery, UserAnswers(allSchemeLevelCharges))
        result.get(chargeFDetailsPage).value.amountTaxDue mustBe 0
      }
    }
  }

  "hasLastChargeOnly" - {

    "return true if there is only one scheme level charge" in {
      deleteChargeHelper.hasLastChargeOnly(UserAnswers(onlyChargeAUa)) mustBe true
    }

    "return true if last charge is charge C and only one or more deleted member" in {
      deleteChargeHelper.hasLastChargeOnly(UserAnswers(onlyChargeCUa)) mustBe true
    }

    "return true if the last charge is charge D and only one or more deleted member" in {
      deleteChargeHelper.hasLastChargeOnly(UserAnswers(onlyChargeDUa)) mustBe true
    }

    "return false if the there are more than one scheme level charge" in {
      val ua = Json.parse(
        """{
          |  "chargeADetails": {
          |    "chargeDetails": {
          |      "numberOfMembers": 2,
          |      "totalAmtOfTaxDueAtLowerRate": 200.02,
          |      "totalAmtOfTaxDueAtHigherRate": 200.02,
          |      "totalAmount": 200.02
          |    },
          |    "amendedVersion": 2
          |  },
          |  "chargeFDetails": {
          |    "chargeDetails": {
          |      "amountTaxDue": 200.02,
          |      "deRegistrationDate": "1980-02-29"
          |    }
          |  }
          |}""".stripMargin).as[JsObject]

      deleteChargeHelper.hasLastChargeOnly(UserAnswers(ua)) mustBe false
    }

    "return false if the there is one scheme level charge and one member level charge with only deleted member" in {
      val ua = Json.parse(
        """{
          |  "chargeADetails": {
          |    "chargeDetails": {
          |      "numberOfMembers": 2,
          |      "totalAmtOfTaxDueAtLowerRate": 200.02,
          |      "totalAmtOfTaxDueAtHigherRate": 200.02,
          |      "totalAmount": 200.02
          |    },
          |    "amendedVersion": 2
          |  },
          |  "chargeEDetails": {
          |    "members": [
          |      {
          |        "memberDetails": {
          |          "firstName": "eFirstName",
          |          "lastName": "eLastName",
          |          "nino": "AE100100A",
          |          "isDeleted": true
          |        },
          |        "annualAllowanceYear": "2020",
          |        "chargeDetails": {
          |          "dateNoticeReceived": "2020-01-11",
          |          "chargeAmount": 200.02,
          |          "isPaymentMandatory": true
          |        }
          |      },
          |      {
          |        "memberDetails": {
          |          "firstName": "eFirstName",
          |          "lastName": "eLastName",
          |          "nino": "AE100100A",
          |          "isDeleted": false
          |        },
          |        "annualAllowanceYear": "2020",
          |        "chargeDetails": {
          |          "dateNoticeReceived": "2020-01-11",
          |          "chargeAmount": 200.02,
          |          "isPaymentMandatory": true
          |        }
          |      }
          |    ],
          |    "totalChargeAmount": 200.02
          |  }
          |}""".stripMargin).as[JsObject]

      deleteChargeHelper.hasLastChargeOnly(UserAnswers(ua)) mustBe false
    }

    "return false if the there are one member level charge with one or more non deleted members" in {
      deleteChargeHelper.hasLastChargeOnly(UserAnswers(onlyChargeEUa(isDeleted = false))) mustBe false
    }
  }
}

object DeleteChargeHelperSpec {
  private val onlyChargeAUa = Json.parse(
    """{
      |  "chargeADetails": {
      |    "chargeDetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "amendedVersion": 2
      |  }
      |}""".stripMargin).as[JsObject]

  private val onlyChargeBUa = Json.parse(
    """{
      |  "chargeBDetails": {
      |    "chargeDetails": {
      |      "numberOfDeceased": 4,
      |      "amountTaxDue": 55.55
      |    }
      |  }
      |}""".stripMargin).as[JsObject]

  private val onlyChargeFUa = Json.parse(
    """{
      |  "chargeFDetails": {
      |    "chargeDetails": {
      |      "amountTaxDue": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  }
      |}""".stripMargin).as[JsObject]

  private val onlyChargeCUa = Json.parse(
    """{
      |  "chargeCDetails": {
      |    "employers": [
      |      {
      |        "whichTypeOfSponsoringEmployer": "individual",
      |        "memberStatus": "Changed",
      |        "memberAFTVersion": 1,
      |        "chargeDetails": {
      |          "paymentDate": "2020-01-01",
      |          "amountTaxDue": 500.02
      |        },
      |        "sponsoringIndividualDetails": {
      |          "firstName": "testFirst",
      |          "lastName": "testLast",
      |          "nino": "AB100100A",
      |          "isDeleted": true
      |        },
      |        "sponsoringEmployerAddress": {
      |          "line1": "line1",
      |          "line2": "line2",
      |          "line3": "line3",
      |          "line4": "line4",
      |          "postcode": "NE20 0GG",
      |          "country": "GB"
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 500.02,
      |    "amendedVersion": 1
      |  }
      |}""".stripMargin).as[JsObject]

  private val onlyChargeDUa = Json.parse(
    """{
      |  "chargeDDetails": {
      |    "numberOfMembers": 2,
      |    "members": [
      |      {
      |        "memberDetails": {
      |          "firstName": "firstName",
      |          "lastName": "lastName",
      |          "nino": "AC100100A",
      |          "isDeleted": true
      |        },
      |        "chargeDetails": {
      |          "dateOfEvent": "2020-01-10",
      |          "taxAt25Percent": 100,
      |          "taxAt55Percent": 100.02
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 200.02
      |  }
      |}""".stripMargin).as[JsObject]

  private def onlyChargeEUa(isDeleted: Boolean = true): JsObject = Json.parse(
    s"""{
       |  "chargeEDetails": {
       |    "members": [
       |      {
       |        "memberDetails": {
       |          "firstName": "eFirstName",
       |          "lastName": "eLastName",
       |          "nino": "AE100100A",
       |          "isDeleted": $isDeleted
       |        },
       |        "annualAllowanceYear": "2020",
       |        "chargeDetails": {
       |          "dateNoticeReceived": "2020-01-11",
       |          "chargeAmount": 200.02,
       |          "isPaymentMandatory": true
       |        }
       |      }
       |    ],
       |    "totalChargeAmount": 200.02
       |  }
       |}""".stripMargin).as[JsObject]

  private val onlyChargeGUa = Json.parse(
    """{
      |  "chargeGDetails": {
      |    "members": [
      |      {
      |        "memberDetails": {
      |          "firstName": "Craig",
      |          "lastName": "White",
      |          "dob": "1980-02-29",
      |          "nino": "AA012000A",
      |          "isDeleted": true
      |        },
      |        "chargeDetails": {
      |          "qropsReferenceNumber": "300000",
      |          "qropsTransferDate": "2016-02-29"
      |        },
      |        "chargeAmounts": {
      |          "amountTransferred": 45670.02,
      |          "amountTaxDue": 4560.02
      |        }
      |      }
      |    ],
      |    "totalChargeAmount": 1230.02
      |  }
      |}""".stripMargin).as[JsObject]

  private val allSchemeLevelCharges = Json.parse(
    """{
      |  "chargeADetails": {
      |    "chargeDetails": {
      |      "numberOfMembers": 2,
      |      "totalAmtOfTaxDueAtLowerRate": 200.02,
      |      "totalAmtOfTaxDueAtHigherRate": 200.02,
      |      "totalAmount": 200.02
      |    },
      |    "amendedVersion": 2
      |  },
      |  "chargeBDetails": {
      |    "chargeDetails": {
      |      "numberOfDeceased": 4,
      |      "amountTaxDue": 55.55
      |    }
      |  },
      |  "chargeFDetails": {
      |    "chargeDetails": {
      |      "amountTaxDue": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  }
      |}""".stripMargin).as[JsObject]
}