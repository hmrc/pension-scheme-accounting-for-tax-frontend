/*
 * Copyright 2022 HM Revenue & Customs
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

import models.ChargeType
import pages.QuestionPage
import play.api.libs.json.JsPath

case class IsPublicServicePensionsRemedyPage(chargeType: ChargeType, index: Int) extends QuestionPage[Boolean] {
  override def path: JsPath = JsPath \ detailsNode \ "members" \ index \ IsPublicServicePensionsRemedyPage.toString

  private def detailsNode: String = chargeType match {
    case ChargeType.ChargeTypeAnnualAllowance => "chargeEDetails"
    case ChargeType.ChargeTypeLifetimeAllowance => "chargeDDetails"
    case _ => throw new RuntimeException(s"McCloud Remedy not available for charge type $chargeType")
  }
}

object IsPublicServicePensionsRemedyPage {
  override def toString: String = "isPublicServicePensionsRemedy"
}
