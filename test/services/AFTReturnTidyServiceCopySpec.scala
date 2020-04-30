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

package services

import base.SpecBase
import data.SampleData
import data.SampleData._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.UserAnswers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{ChargeCDetailsPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results

class AFTReturnTidyServiceCopySpec extends SpecBase with ScalaFutures with BeforeAndAfterEach with MockitoSugar with Results {

  private val userAnswersRequestJson = Json.parse(
    """{
      |  "isNewReturn": null,
      |  "chargeEDetails": {
      |    "totalChargeAmount": 0,
      |    "members": [
      |      {
      |        "annualAllowanceYear": "2020",
      |        "memberDetails": {
      |          "firstName": "sa",
      |          "lastName": "af",
      |          "nino": "CS800100A",
      |          "isDeleted": true
      |        },
      |        "chargeDetails": {
      |          "chargeAmount": 23,
      |          "dateNoticeReceived": "2020-04-02",
      |          "isPaymentMandatory": true
      |        }
      |      }
      |    ]
      |  },
      |  "pstr": "24000001IN",
      |  "schemeName": "Open Single Trust Scheme with Indiv Establisher and Trustees",
      |  "isPsaSuspended": false,
      |  "chargeType": "annualAllowance",
      |  "aftStatus": "Compiled",
      |  "psaName": "Nigel Robert Smith",
      |  "schemeStatus": "Open",
      |  "psaEmail": "nigel@test.com",
      |  "quarter": {
      |    "startDate": "2020-04-01",
      |    "endDate": "2020-06-30"
      |  }
      |}""".stripMargin)

  val aftSrvice = new AFTReturnTidyServiceCopy

  "return correct json" in {
    val res = aftSrvice.zeroOutLastCharge(UserAnswers(userAnswersRequestJson.as[JsObject]))
    println("\n\n\n res: "+res)
  }
}
