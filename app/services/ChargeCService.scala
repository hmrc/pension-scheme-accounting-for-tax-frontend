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

import controllers.chargeB.{routes => _}
import models.{Employer, UserAnswers}
import pages.chargeC.{ChargeCDetailsPage, IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.i18n.Messages
import play.api.libs.json.Reads._
import play.api.libs.json.{JsArray, JsDefined}
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.CheckYourAnswersHelper.formatBigDecimalAsString
import viewmodels.Table
import viewmodels.Table.Cell

object ChargeCService {

  def getSponsoringEmployersIncludingDeleted(ua: UserAnswers, srn: String): Seq[Employer] = {

    def getEmployerDetails(index: Int): Option[(String, Boolean)] =
      ua.get(IsSponsoringEmployerIndividualPage(index)).flatMap { isIndividual =>
        if (isIndividual) {
          ua.get(SponsoringIndividualDetailsPage(index)).map { individual =>
            (individual.fullName, individual.isDeleted)
          }
        } else {
          ua.get(SponsoringOrganisationDetailsPage(index)).map { org =>
            (org.name, org.isDeleted)
          }
        }
      }

    val employers = ua.data \ "chargeCDetails" \ "employers" match {
      case JsDefined(JsArray(employers)) =>
        for {
          (_, index) <- employers.zipWithIndex
        } yield
          (getEmployerDetails(index), ua.get(ChargeCDetailsPage(index))) match {
            case (Some(details), Some(chargeDetails)) =>

              val (name, isDeleted) = details
              Seq(Employer(
                index,
                name,
                chargeDetails.amountTaxDue,
                viewUrl(index, srn).url,
                removeUrl(index, srn).url,
                isDeleted
              ))
            case _ => Nil

          }

      case _ => Nil


    }

    employers.flatten
  }

  def getSponsoringEmployers(ua: UserAnswers, srn: String): Seq[Employer] =
    getSponsoringEmployersIncludingDeleted(ua, srn).filterNot(_.isDeleted)

  def viewUrl(index: Int, srn: String): Call = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, index)

  def removeUrl(index: Int, srn: String): Call = controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, index)

  def mapToTable(members: Seq[Employer])(implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg""),
      Cell(msg"")
    )

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"£${formatBigDecimalAsString(data.amount)}"), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name), classes = Seq("govuk-!-width-one-quarter"))

      )
    }
    val totalAmount = members.map(_.amount).sum

    val totalRow = Seq(Seq(
      Cell(msg"addMembers.total", classes = Seq("govuk-table__header--numeric")),
      Cell(Literal(s"£${formatBigDecimalAsString(totalAmount)}"), classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg""),
      Cell(msg"")
    ))

    Table(head = head, rows = rows ++ totalRow)
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(s"<a id=$id href=$url> ${messages(text)}" +
      s"<span class= $hiddenTag>${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

}
