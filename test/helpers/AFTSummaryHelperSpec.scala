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

package helpers

import base.SpecBase
import controllers._
import controllers.chargeB.{routes => _}
import data.SampleData
import data.SampleData.{accessType, sessionAccessDataCompile, version, versionInt}
import models.ChargeType.{ChargeTypeAnnualAllowance, _}
import models.LocalDateBinder._
import models.chargeA.{ChargeDetails => ChargeADetails}
import models.chargeB.ChargeBDetails
import models.chargeF.{ChargeDetails => ChargeFDetails}
import models.requests.DataRequest
import models.{AccessMode, ChargeType, SessionAccessData, UserAnswers}
import org.mockito.MockitoSugar
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers.GET
import play.twirl.api.{Html => TwirlHtml}
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, _}

import java.time.LocalDate

class AFTSummaryHelperSpec extends SpecBase with Matchers with MockitoSugar with BeforeAndAfterEach {

  private val userAnswers = UserAnswers(Json.obj())
    .setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(100.00))
    .setOrException(pages.chargeC.TotalChargeAmountPage, BigDecimal(200.00))
    .setOrException(pages.chargeF.ChargeDetailsPage, ChargeFDetails(LocalDate.now(), BigDecimal(300.00)))
    .setOrException(pages.chargeD.TotalChargeAmountPage, BigDecimal(400.00))
    .setOrException(pages.chargeA.ChargeDetailsPage, ChargeADetails(1, None, None, BigDecimal(500.00)))
    .setOrException(pages.chargeB.ChargeBDetailsPage, ChargeBDetails(1, BigDecimal(600.00)))
    .setOrException(pages.chargeG.TotalChargeAmountPage, BigDecimal(700.00))

  private val srn = "test-srn"
  private val startDate = LocalDate.now

  private def createRow(chargeType: ChargeType, amount: BigDecimal, href: Option[String]) = {
    Row(
      key = Key(msg"aft.summary.${chargeType.toString}.row", classes = Seq("govuk-!-width-three-quarters")),
      value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(amount)}"),
        classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
      actions = href
        .map { url =>
          List(
            Action(
              content = Html(s"<span  aria-hidden=true >${messages("site.view")}</span>"),
              href = url,
              visuallyHiddenText = Some(Literal(
                messages("site.view") + " " + messages(s"aft.summary.${chargeType.toString}.visuallyHidden.row")
              ))
            )
          )
        }
        .getOrElse(Nil)
    )
  }

  val aftSummaryHelper = new AFTSummaryHelper

  "summaryListData" must {

    "return all the rows if all the charges have data" in {
      val result = aftSummaryHelper.summaryListData(userAnswers, srn, startDate, accessType, versionInt)(messages)

      result mustBe Seq(
        createRow(ChargeTypeAnnualAllowance, BigDecimal(100.00), Some(chargeE.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeAuthSurplus, BigDecimal(200.00), Some(chargeC.routes.AddEmployersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeDeRegistration, BigDecimal(300.00), Some(chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeLifetimeAllowance, BigDecimal(400.00), Some(chargeD.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeShortService, BigDecimal(500.00), Some(chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeLumpSumDeath, BigDecimal(600.00), Some(chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        Row(
          key = Key(msg"aft.summary.total", classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(BigDecimal(2100.00))}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        createRow(ChargeTypeOverseasTransfer, BigDecimal(700.00), Some(chargeG.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url))
      )
    }

    "return only one row with link and others with zero amount if only one charge has data" in {
      val result = aftSummaryHelper.summaryListData(UserAnswers(Json.obj()).setOrException(pages.chargeE.TotalChargeAmountPage, BigDecimal(100.00)),
        srn,
        startDate, accessType, versionInt)

      result mustBe Seq(
        createRow(ChargeTypeAnnualAllowance, BigDecimal(100.00), Some(chargeE.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url)),
        createRow(ChargeTypeAuthSurplus, BigDecimal(0.00), None),
        createRow(ChargeTypeDeRegistration, BigDecimal(0.00), None),
        createRow(ChargeTypeLifetimeAllowance, BigDecimal(0.00), None),
        createRow(ChargeTypeShortService, BigDecimal(0.00), None),
        createRow(ChargeTypeLumpSumDeath, BigDecimal(0.00), None),
        Row(
          key = Key(msg"aft.summary.total", classes = Seq("govuk-table__header--numeric", "govuk-!-padding-right-0")),
          value = Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(BigDecimal(100.00))}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")),
          actions = Nil
        ),
        createRow(ChargeTypeOverseasTransfer, BigDecimal(0.00), None)
      )
    }
  }

  "viewAmendmentsLink" must {
    def dataRequest(sessionData: SessionAccessData = sessionAccessDataCompile): DataRequest[_] =
      DataRequest(FakeRequest(GET, "/"),
        "test-internal-id",
        Some(PsaId("A2100000")),
        None,
        UserAnswers(),
        SampleData.sessionData(sessionAccessData = sessionData))

    def amendmentsUrl = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, startDate, accessType, versionInt).url

    "have correct link text when its amendment compile" in {

      val link = aftSummaryHelper.viewAmendmentsLink(versionInt, srn, startDate, accessType)(implicitly, dataRequest())

      link mustBe TwirlHtml(s"${
        TwirlHtml(
          s"""<a id=view-amendments-link href=$amendmentsUrl class="govuk-link"> ${
            messages(
              "allAmendments.view.changes.draft.link")
          }</a>""".stripMargin).toString()
      }")

    }

    "have correct link text when its previous submission" in {

      val link = aftSummaryHelper.viewAmendmentsLink(versionInt, srn, startDate, accessType)(implicitly,
        dataRequest(SessionAccessData(version.toInt, AccessMode.PageAccessModeViewOnly, areSubmittedVersionsAvailable = false)))

      link mustBe TwirlHtml(s"${
        TwirlHtml(
          s"""<a id=view-amendments-link href=$amendmentsUrl class="govuk-link"> ${
            messages(
              "allAmendments.view.changes.submission.link")
          }</a>""".stripMargin).toString()
      }")

    }
  }
}
