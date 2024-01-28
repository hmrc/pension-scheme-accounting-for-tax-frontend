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

package services

import controllers.chargeB.{routes => _}
import helpers.FormatHelper
import models.AmendedChargeStatus.{Unknown, amendedChargeStatus}
import models.ChargeType.ChargeTypeAuthSurplus
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.viewModels.ViewAmendmentDetails
import models.{AccessType, Employer, UserAnswers}
import pages.chargeC._
import play.api.i18n.Messages
import play.api.libs.json.JsArray
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import scala.math.Numeric.BigDecimalIsFractional

class ChargeCService {

   def numberOfEmployersIncludingDeleted(ua: UserAnswers): Int =
    (ua.data \ "chargeCDetails" \ "employers").toOption
      .map(_.as[JsArray].value.length)
      .getOrElse(0)

  private def getEmployerDetails(ua: UserAnswers, index: Int): Option[String] = ua.get(WhichTypeOfSponsoringEmployerPage(index)) flatMap {
    case SponsoringEmployerTypeIndividual => ua.get(SponsoringIndividualDetailsPage(index)).map(_.fullName)
    case _ => ua.get(SponsoringOrganisationDetailsPage(index)).map(_.name)
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

  def mapToTable(members: Seq[Employer], canChange: Boolean)
                (implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header"),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-table__header--numeric")),
      Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.viewSponsoringEmployer")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.removeSponsoringEmployer")}</span>""")))
      else
        Nil
      )

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
          classes = Seq("govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow,attributes = Map("role" -> "table"))
  }

  def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
        s"<a class=govuk-link id=$id href=$url>" + s"<span aria-hidden=true >${messages(text)}</span>" +
        s"<span class= $hiddenTag>${messages(text)} ${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

    }
