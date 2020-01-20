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

import models.{Member, UserAnswers}
import pages.chargeC.{ChargeCDetailsPage, IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.i18n.Messages
import play.api.libs.json.Reads._
import play.api.libs.json.{JsArray, JsDefined}
import play.api.mvc.Call
import services.AddMembersService.mapChargeXMembersToTable
import viewmodels.Table

object ChargeCService {

  def getSponsoringEmployersIncludingDeleted(ua: UserAnswers, srn: String): Seq[Member] = {

    def getEmployerDetails(index: Int): Option[(String, Boolean)] =
      ua.get(IsSponsoringEmployerIndividualPage(index)).flatMap { isIndividual =>
        if (isIndividual)
          ua.get(SponsoringIndividualDetailsPage(index)).map { individual =>
            (individual.fullName, individual.isDeleted)
          }
        else
          ua.get(SponsoringOrganisationDetailsPage(index)).map { org =>
            (org.name, org.isDeleted)
          }
      }

    ua.data \ "chargeCDetails" \ "employers" match {
      case JsDefined(JsArray(employers)) =>
        for {
          (_, index) <- employers.zipWithIndex
        } yield
          (getEmployerDetails(index), ua.get(ChargeCDetailsPage(index))) match {
            case (Some(details), Some(chargeDetails)) =>

              val (name, isDeleted) = details
              Member(
                index,
                name, "",
                chargeDetails.amountTaxDue,
                viewUrl(index, srn).url,
                removeUrl(index, srn).url,
                isDeleted
              )
          }

      case _ => Nil


    }



  }

  def getSponsoringEmployers(ua: UserAnswers, srn: String): Seq[Member] =
    getSponsoringEmployersIncludingDeleted(ua, srn).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String): Call = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, index)
  def removeUrl(index: Int, srn: String): Call = controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, index)

  def mapToTable(members: Seq[Member])(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeC", members)

}
