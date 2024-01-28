/*
 * Copyright 2024 HM Revenue & Customs
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

package pages.mccloud

import models.{ChargeType, YearRange}
import pages.QuestionPage
import play.api.libs.json.JsPath

case class TaxYearReportedAndPaidPage(chargeType: ChargeType, index: Int, schemeIndex: Option[Int]) extends QuestionPage[YearRange] {
  private def basePath = JsPath \ ChargeType.chargeBaseNode(chargeType) \ "members" \ index \ "mccloudRemedy"
  override def path: JsPath =
    schemeIndex match {
      case Some(i) => basePath \ "schemes" \ i \ TaxYearReportedAndPaidPage.toString
      case None => basePath \ TaxYearReportedAndPaidPage.toString
    }
}

object TaxYearReportedAndPaidPage {
  override lazy val toString: String = "taxYearReportedAndPaidPage"
}

