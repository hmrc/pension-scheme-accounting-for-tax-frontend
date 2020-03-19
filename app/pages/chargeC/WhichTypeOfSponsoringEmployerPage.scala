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

package pages.chargeC

import models.{SponsoringEmployerType, UserAnswers}
import pages.QuestionPage
import play.api.libs.json.JsPath

import scala.util.Try

case class WhichTypeOfSponsoringEmployerPage(index: Int) extends QuestionPage[SponsoringEmployerType] {

  override def path: JsPath = SponsoringEmployersQuery(index).path \ WhichTypeOfSponsoringEmployerPage.toString

  override def cleanup(value: Option[SponsoringEmployerType], userAnswers: UserAnswers): Try[UserAnswers] = {
    val tidyResult = value match {
      case Some(SponsoringEmployerType.SponsoringEmployerTypeIndividual) if userAnswers.get(SponsoringOrganisationDetailsPage(index)).isDefined =>
        userAnswers
          .remove(SponsoringOrganisationDetailsPage(index)).toOption.getOrElse(userAnswers)
          .remove(SponsoringEmployerAddressPage(index)).toOption.getOrElse(userAnswers)
          .remove(ChargeCDetailsPage(index)).toOption
      case Some(SponsoringEmployerType.SponsoringEmployerTypeOrganisation) if userAnswers.get(SponsoringIndividualDetailsPage(index)).isDefined =>
        userAnswers
          .remove(SponsoringIndividualDetailsPage(index)).toOption.getOrElse(userAnswers)
          .remove(SponsoringEmployerAddressPage(index)).toOption.getOrElse(userAnswers)
          .remove(ChargeCDetailsPage(index)).toOption
      case _ => None
    }
    super.cleanup(value, tidyResult.getOrElse(userAnswers))
  }
}

object WhichTypeOfSponsoringEmployerPage {
  override lazy val toString: String = "whichTypeOfSponsoringEmployer"
}
