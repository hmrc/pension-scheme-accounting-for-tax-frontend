/*
 * Copyright 2019 HM Revenue & Customs
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

package data

import java.time.LocalDate

import models.chargeE.ChargeEDetails
import models.chargeB.ChargeBDetails
import models.{MemberDetails, SchemeDetails, UserAnswers}
import play.api.libs.json.Json
import play.api.mvc.Call

object SampleData {
  val userAnswersId = "id"
  val psaId = "A0000000"
  val srn = "aa"
  val pstr = "pstr"
  val schemeName = "Big Scheme"
  val dummyCall = Call("GET","/foo")
  val chargeFChargeDetails = models.chargeF.ChargeDetails(LocalDate.of(2020, 4, 3), BigDecimal(33.44))
  val chargeAChargeDetails = models.chargeA.ChargeDetails(44, BigDecimal(33.44), BigDecimal(34.34), BigDecimal(67.78))
  val chargeEDetails = ChargeEDetails(BigDecimal(33.44), LocalDate.of(2019, 4, 3), isPaymentMandatory = true)
  val schemeDetails: SchemeDetails = SchemeDetails(schemeName, pstr)
  def userAnswersWithSchemeName = UserAnswers(Json.obj("schemeName" -> schemeName, "pstr" -> pstr))

  val chargeBDetails = ChargeBDetails(4, BigDecimal(33.44))
  val memberDetails: MemberDetails = MemberDetails("first", "last", "AB123456C")
}
