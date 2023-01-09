/*
 * Copyright 2023 HM Revenue & Customs
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

case class IsPublicServicePensionsRemedyPage(chargeType: ChargeType, optIndex: Option[Int]) extends QuestionPage[Boolean] {
  override def path: JsPath = optIndex match {
    case Some(index) => JsPath \ ChargeType.chargeBaseNode(chargeType) \ "members" \ index \ "mccloudRemedy" \ IsPublicServicePensionsRemedyPage.toString
    case None => JsPath \ ChargeType.chargeBaseNode(chargeType) \ "mccloudRemedy" \ IsPublicServicePensionsRemedyPage.toString
  }
}

object IsPublicServicePensionsRemedyPage {
  override def toString: String = "isPublicServicePensionsRemedy"
}
