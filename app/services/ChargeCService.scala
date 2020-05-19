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

import java.time.LocalDate

import com.google.inject.Inject
import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeAuthSurplus
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Employer, UserAnswers}
import pages.chargeC._
import play.api.i18n.Messages
import play.api.libs.json.JsArray
import play.api.mvc.{AnyContent, Call}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import utils.DeleteChargeHelper
import viewmodels.Table
import viewmodels.Table.Cell

class ChargeCService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  private def numberOfEmployersIncludingDeleted(ua: UserAnswers): Int =
    (ua.data \ "chargeCDetails" \ "employers").toOption
      .map(_.as[JsArray].value.length)
      .getOrElse(0)

  private def getEmployerDetails(ua: UserAnswers, index: Int): Option[(String, Boolean)] = ua.get(WhichTypeOfSponsoringEmployerPage(index)) flatMap {
    case SponsoringEmployerTypeIndividual => ua.get(SponsoringIndividualDetailsPage(index)).map(i => Tuple2(i.fullName, i.isDeleted))
    case _                                => ua.get(SponsoringOrganisationDetailsPage(index)).map(o => Tuple2(o.name, o.isDeleted))
  }

  def getSponsoringEmployersIncludingDeleted(ua: UserAnswers, srn: String, startDate: LocalDate)
                                            (implicit request: DataRequest[AnyContent]): Seq[Employer] = {

    (0 until numberOfEmployersIncludingDeleted(ua)).flatMap { index =>
      getEmployerDetails(ua, index).flatMap {
        case (name, isDeleted) =>
          ua.get(ChargeCDetailsPage(index)).map { chargeDetails =>
            Employer(
              index,
              name,
              chargeDetails.amountTaxDue,
              viewUrl(index, srn, startDate).url,
              removeUrl(index, srn, startDate, ua).url,
              isDeleted
            )
          }
      }.toSeq
    }
  }

  def getSponsoringEmployers(ua: UserAnswers, srn: String, startDate: LocalDate)(implicit request: DataRequest[AnyContent]): Seq[Employer] =
    getSponsoringEmployersIncludingDeleted(ua, srn, startDate).filterNot(_.isDeleted)

  def getAllAuthSurplusAmendments(ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Seq[ViewAmendmentDetails] = {
    (0 until numberOfEmployersIncludingDeleted(ua)).flatMap { index =>
      getEmployerDetails(ua, index).flatMap {
        case (name, _) =>
          ua.get(ChargeCDetailsPage(index)).map { chargeAmounts =>
            val currentVersion = request.aftVersion
            val memberVersion = ua.get(MemberAFTVersionPage(index)).getOrElse(0)

            if (memberVersion == currentVersion) {
              Seq(
                ViewAmendmentDetails(
                  name,
                  ChargeTypeAuthSurplus.toString,
                  FormatHelper.formatCurrencyAmountAsString(chargeAmounts.amountTaxDue),
                  ua.get(MemberStatusPage(index)).map(amendedChargeStatus).getOrElse(Unknown)
                )
              )
            } else {
              Nil
            }
          }
      }
    }.flatten
  }

  def viewUrl(index: Int, srn: String, startDate: LocalDate): Call =
    controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, index)

  private def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers)(implicit request: DataRequest[AnyContent]): Call =
    if (request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeC.routes.RemoveLastChargeController.onPageLoad(srn, startDate, index)
    } else {
      controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, index)
    }

  def mapToTable(members: Seq[Employer], canChange: Boolean)(implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header", classes = Seq("govuk-!-width-one-half")),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
      Cell(msg"")
    ) ++ (if (canChange) Seq(Cell(msg"")) else Nil)

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"),
             classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name), classes = Seq("govuk-!-width-one-quarter"))
      ) ++ (if (canChange) Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name), classes = Seq("govuk-!-width-one-quarter")))
            else Nil)
    }
    val totalAmount = members.map(_.amount).sum

    val totalRow = Seq(
      Seq(
        Cell(msg"addMembers.total", classes = Seq("govuk-table__header--numeric")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
             classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow)
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
      s"<a id=$id href=$url> ${messages(text)}" +
        s"<span class= $hiddenTag>${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

}