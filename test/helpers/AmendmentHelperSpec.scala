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

package helpers

import java.time.LocalDate

import base.SpecBase
import models.chargeB.ChargeBDetails
import models.chargeF.ChargeDetails
import models.{UserAnswers, chargeA}
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._

class AmendmentHelperSpec extends SpecBase {
import AmendmentHelperSpec._
  private val amendmentHelper = new AmendmentHelper
/*
  "getTotalAmount" must {

    "return sum of the total amounts of all charges for UK/NonUK" in {
      val ua = UserAnswers().setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(100.00))
        .setOrException(pages.chargeC.TotalChargeAmountPage, BigDecimal(1000.00))
        .setOrException(pages.chargeD.TotalChargeAmountPage, BigDecimal(1000.00))
        .setOrException(pages.chargeF.ChargeDetailsPage, ChargeDetails(LocalDate.now(), BigDecimal(2500.00)))
        .setOrException(pages.chargeA.ChargeDetailsPage, chargeA.ChargeDetails(3, None, None, BigDecimal(3500.00)))
        .setOrException(pages.chargeB.ChargeBDetailsPage, ChargeBDetails(3, BigDecimal(5000.00)))
        .setOrException(pages.chargeG.TotalChargeAmountPage, BigDecimal(34220.00))

      amendmentHelper.getTotalAmount(ua) mustEqual (BigDecimal(13100.00), BigDecimal(34220.00))
    }
  }

  "amendmentSummaryRows" must {
    val previousVersion = 2
    val currentVersion = 3
    val previousTotalAmount = BigDecimal(5000.00)
    val currentTotalAmount = BigDecimal(8000.00)
    val differenceAmount = BigDecimal(3000.00)

    "return all the summary list rows" in {
      val result = amendmentHelper.amendmentSummaryRows(currentTotalAmount, previousTotalAmount, currentVersion, previousVersion)

      result mustBe Seq(
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for".withArgs(previousVersion), classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(previousTotalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.total.for.draft", classes = Seq("govuk-!-width-three-quarters")),
          value = Value(
            Literal(s"${FormatHelper.formatCurrencyAmountAsString(currentTotalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
          ),
          actions = Nil
        ),
        Row(
          key = Key(msg"confirmSubmitAFTReturn.difference",
            classes = Seq("govuk-!-width-three-quarters")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(differenceAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        )
      )
    }
  }*/

  "getAllAmendments" must {
    "return all the amendment details for Charge C" in {
      val res = amendmentHelper.getAllAmendments(UserAnswers(userAnswersRequestJson.as[JsObject]))

    }
  }
}

object AmendmentHelperSpec {
  private val userAnswersRequestJson = Json.parse(
    """{
      |  "aftStatus": "Compiled",
      |  "quarter": {
      |    "startDate": "2019-01-01",
      |    "endDate": "2019-03-31"
      |  },
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
      |          "nino": "AB100100A"
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
      |  },
      |  "chargeDDetails": {
      |    "numberOfMembers": 2,
      |    "members": [
      |      {
      |        "memberStatus": "Deleted",
      |        "memberAFTVersion": 1,
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
      |      },
      |      {
      |        "memberStatus": "New",
      |        "memberAFTVersion": 1,
      |        "memberDetails": {
      |          "firstName": "secondName",
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
      |  },
      |  "chargeEDetails": {
      |    "members": [
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
      |  },
      |  "chargeFDetails": {
      |    "chargeDetails": {
      |      "amountTaxDue": 200.02,
      |      "deRegistrationDate": "1980-02-29"
      |    }
      |  },
      |  "chargeGDetails": {
      |    "members": [
      |      {
      |        "memberDetails": {
      |          "firstName": "Craig",
      |          "lastName": "White",
      |          "dob": "1980-02-29",
      |          "nino": "AA012000A",
      |          "isDeleted": false
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
      |  },
      |  "declaration" : {
      |    "submittedBy" : "PSA",
      |    "submittedID" : "A2000000",
      |    "hasAgreed" : true
      |  }
      |}""".stripMargin)
}
