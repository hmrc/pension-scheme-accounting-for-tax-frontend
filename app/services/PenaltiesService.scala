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
import config.FrontendAppConfig
import helpers.FormatHelper
import models.LocalDateBinder._
import models.Quarters._
import models.financialStatement.PsaFS
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}
import viewmodels.Table
import viewmodels.Table.Cell

class PenaltiesService @Inject()(config: FrontendAppConfig) {

  def getPsaFsJson(psaFS: Seq[PsaFS], srn: String, year: Int)
                          (implicit messages: Messages): Seq[JsObject] =
    availableQuarters(year)(config).map { quarter =>
      val startDate = getStartDate(quarter, year)
      singlePeriodFSMapping(srn, startDate, psaFS.filter(_.periodStartDate == startDate))
    }

  private def singlePeriodFSMapping(srn: String, startDate: LocalDate, filteredPsaFS: Seq[PsaFS])
                                   (implicit messages: Messages): JsObject = {

    val head = Seq(
      Cell(msg"penalties.column.penalty", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"penalties.column.amount", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    )

    val rows = filteredPsaFS.map { data =>
      Seq(
        Cell(chargeTypeLink(srn, data, startDate), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.outstandingAmount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        statusCell(data)
      )
    }

    Json.obj(
      "header" -> messages("penalties.period", startDate, getQuarter(startDate).endDate),
      "table" -> Table(head = head, rows = rows)
    )
  }

  def chargeTypeLink(srn: String, data: PsaFS, startDate: LocalDate)(implicit messages: Messages): Html =
    Html(
      s"<a id=${data.chargeReference}" +
        s"href=${controllers.financialStatement.routes.ChargeDetailsController.onPageLoad(srn, startDate, data.chargeType)}>" +
        s"${messages(data.chargeType.toString)} </a>")

  def statusCell(data: PsaFS): Cell =
    if(data.outstandingAmount > BigDecimal(0.00)) {
      Cell(msg"penalties.status.paymentOverdue", classes = Seq("govuk-tag govuk-tag--red"))
    } else {
      Cell(msg"")
    }


}

