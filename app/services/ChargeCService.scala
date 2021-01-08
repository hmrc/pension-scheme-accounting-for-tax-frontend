/*
 * Copyright 2021 HM Revenue & Customs
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
import helpers.{DeleteChargeHelper, FormatHelper}
import models.AmendedChargeStatus.{amendedChargeStatus, Unknown}
import models.ChargeType.ChargeTypeAuthSurplus
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.{Employer, AccessType, UserAnswers}
import pages.chargeC._
import play.api.i18n.Messages
import play.api.libs.json.JsArray
import play.api.mvc.{Call, AnyContent}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell

class ChargeCService @Inject()(deleteChargeHelper: DeleteChargeHelper) {

  private def numberOfEmployersIncludingDeleted(ua: UserAnswers): Int =
    (ua.data \ "chargeCDetails" \ "employers").toOption
      .map(_.as[JsArray].value.length)
      .getOrElse(0)

  private def getEmployerDetails(ua: UserAnswers, index: Int): Option[String] = ua.get(WhichTypeOfSponsoringEmployerPage(index)) flatMap {
    case SponsoringEmployerTypeIndividual => ua.get(SponsoringIndividualDetailsPage(index)).map(_.fullName)
    case _ => ua.get(SponsoringOrganisationDetailsPage(index)).map(_.name)
  }

  def getSponsoringEmployers(ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)
                            (implicit request: DataRequest[AnyContent]): Seq[Employer] = {

    (0 until numberOfEmployersIncludingDeleted(ua)).flatMap { index =>
      ua.get(MemberStatusPage(index)) match {
        case Some(status) if status == "Deleted" => Nil
        case _ =>
          getEmployerDetails(ua, index).flatMap { name =>
            ua.get(ChargeCDetailsPage(index)).map { chargeDetails =>
              Employer(
                index,
                name,
                chargeDetails.amountTaxDue,
                viewUrl(index, srn, startDate, accessType, version).url,
                removeUrl(index, srn, startDate, ua, accessType, version).url
              )
            }
          }.toSeq
      }
    }
  }

  def getAllAuthSurplusAmendments(ua: UserAnswers, currentVersion: Int): Seq[ViewAmendmentDetails] = {
    (0 until numberOfEmployersIncludingDeleted(ua)).flatMap { index =>
      getEmployerDetails(ua, index).flatMap { name =>
        ua.get(ChargeCDetailsPage(index)).map { chargeAmounts =>
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

  def viewUrl(index: Int, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Call =
    controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, index)

  private def removeUrl(index: Int, srn: String, startDate: LocalDate, ua: UserAnswers, accessType: AccessType, version: Int)
                       (implicit request: DataRequest[AnyContent]): Call =
    if (request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeC.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, index)
    } else {
      controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, accessType, version, index)
    }

  def mapToTable(members: Seq[Employer], canChange: Boolean)
                (implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header"),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-table__header--numeric")),
      Cell(Html(s"""<span class=govuk-visually-hidden>${messages("site.view.link")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(Cell(Html(s"""<span class=govuk-visually-hidden>${messages("site.remove.link")}</span>""")))
      else
        Nil
      )

    val rows = members.map { data =>
      Seq(
        Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("addEmployers.employer.header")}</span>${data.name}""")),
        Cell(Html(s"""<span class=hmrc-responsive-table__heading aria-hidden=true>${messages("addEmployers.amount.header")}</span>${FormatHelper.formatCurrencyAmountAsString(data.amount)}"""), classes = Seq("govuk-table__header--numeric")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name))
      ) ++ (if (canChange) Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name)))
      else Nil)
    }
    val totalAmount = members.map(_.amount).sum

    val totalRow = Seq(
      Seq(
        Cell(msg"addMembers.total", classes = Seq("govuk-table__header--numeric")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
          classes = Seq("govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow, classes= Seq("hmrc-responsive-table"),attributes = Map("role" -> "table"))
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
        s"<a class=govuk-link id=$id href=$url>" + s"<span aria-hidden=true >${messages(text)}</span>" +
        s"<span class= $hiddenTag>${messages(text)} ${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

}
