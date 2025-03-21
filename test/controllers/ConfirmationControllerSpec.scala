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

package controllers

import connectors.FinancialStatementConnector
import controllers.actions.{AllowSubmissionAction, FakeAllowSubmissionAction, MutableFakeDataRetrievalAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.ValueChangeType.{ChangeTypeDecrease, ChangeTypeIncrease, ChangeTypeSame}
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.financialStatement.{SchemeFS, SchemeFSDetail}
import models.viewModels.ConfirmationViewModel
import models.{AccessMode, SessionAccessData, SessionData, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{times, verify, when}
import pages.{ConfirmSubmitAFTAmendmentValueChangeTypePage, EmailQuery}
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.SchemeService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import utils.AFTConstants._
import utils.DateHelper.formatSubmittedDate
import views.html.{ConfirmationAmendDecreaseView, ConfirmationAmendIncreaseView, ConfirmationNoChargeView, ConfirmationView}

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, ZoneId, ZonedDateTime}
import scala.concurrent.Future

class ConfirmationControllerSpec extends ControllerSpecBase with JsonMatchers {

  import ConfirmationControllerSpec._

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val mockSchemeService = mock[SchemeService]
  private val mockFinancialStatementConnector: FinancialStatementConnector = mock[FinancialStatementConnector]

  private val extraModules: Seq[GuiceableModule] = Seq(
    bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction),
    bind[SchemeService].toInstance(mockSchemeService),
    bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector)
  )

  override def fakeApplication(): Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val viewPaymentsUrl = "/manage-pension-scheme-accounting-for-tax/accounting-for-tax/aa/2020-04-01/payments-and-charges"

  private val schemeFSResponseWithDataForDifferentYear: SchemeFS =
    SchemeFS(
      seqSchemeFSDetail = Seq(
        SchemeFSDetail(
          index = 0,
          chargeReference = "XY002610150184",
          chargeType = PSS_AFT_RETURN,
          dueDate = Some(LocalDate.parse("2021-02-15")),
          totalAmount = 12345.00,
          outstandingAmount = 56049.08,
          stoodOverAmount = 25089.08,
          amountDue = 1029.05,
          accruedInterestTotal = 23000.55,
          periodStartDate = Some(LocalDate.parse("2021-04-01")),
          periodEndDate = Some(LocalDate.parse("2021-06-30")),
          formBundleNumber = None,
          version = None,
          receiptDate = None,
          sourceChargeRefForInterest = None,
          sourceChargeInfo = None,
          documentLineItemDetails = Nil
        )
      )
    )


  override def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(mockUserAnswersCacheConnector)
    Mockito.reset(mockAllowAccessActionProvider)
    when(mockAllowAccessActionProvider.apply(any(), any(), any(), any(), any())).thenReturn(FakeActionFilter)
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))

  }

  "Confirmation Controller" must {

    "return OK and the correct view for submission for a GET when financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(SampleData.version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.
        set(EmailQuery, email).getOrElse(UserAnswers())))

      val result = route(app, request).value
      status(result) mustEqual OK

      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      val viewModel = ConfirmationViewModel(
        "Return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(false),
        Some(viewPaymentsUrl),
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for amendment for a GET when financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.setOrException(EmailQuery, email)))

      val result = route(app, request).value
      status(result) mustEqual OK

      val viewModel = ConfirmationViewModel(
        "Amended return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(true),
        Some(viewPaymentsUrl),
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for amendment for a GET when value decreased and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(EmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeDecrease)
        ))

      val result = route(app, request).value
      status(result) mustEqual OK

      val viewModel = ConfirmationViewModel(
        "Amended return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(true),
        Some(viewPaymentsUrl),
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationAmendDecreaseView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for amendment for a GET when value increased and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(EmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeIncrease)
        ))

      val result = route(app, request).value
      status(result) mustEqual OK

      val viewModel = ConfirmationViewModel(
        "Amended return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(true),
        Some(viewPaymentsUrl),
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationAmendIncreaseView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view for amendment for a GET when value not changed and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(EmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeSame)
        ))

      val result = route(app, request).value
      status(result) mustEqual OK

      val viewModel = ConfirmationViewModel(
        "Amended return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(true),
        Some(viewPaymentsUrl),
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationNoChargeView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "return OK but don't include financial info link when no financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(SampleData.version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.
        set(EmailQuery, email).getOrElse(UserAnswers())))

      when(mockFinancialStatementConnector.getSchemeFS(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(schemeFSResponseWithDataForDifferentYear))

      val result = route(app, request).value
      status(result) mustEqual OK

      val viewModel = ConfirmationViewModel(
        "Return submitted",
        HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>"""),
        email,
        rows(false),
        None,
        controllers.routes.AFTOverviewController.onPageLoad(srn).url,
        schemeName,
        submitUrl.url
      )

      val view = app.injector.instanceOf[ConfirmationView].apply(
        viewModel
      )(request, messages)

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page when there is no scheme name or pstr or quarter" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val result = route(app, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}

object ConfirmationControllerSpec {
  private val quarterEndDate = QUARTER_END_DATE.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
  private val quarterStartDate = QUARTER_START_DATE.format(DateTimeFormatter.ofPattern("d MMMM"))
  private val email = "test@test.com"
  private val versionNumber = 2

  private def submitUrl = Call("GET", s"/manage-pension-scheme-accounting-for-tax/${SampleData.startDate}/${SampleData.srn}/sign-out")

  private def rows(hasVersion: Boolean)(implicit messages: Messages) = Seq(SummaryListRow(
    key = Key(Text(messages("confirmation.table.scheme.label")), classes = "govuk-!-font-weight-regular"),
    value = Value(Text(SampleData.schemeName))
  ),
    SummaryListRow(
      key = Key(Text(messages("confirmation.table.accounting.period.label")), classes = "govuk-!-font-weight-regular"),
      value = Value(Text(messages("confirmation.table.accounting.period.value", quarterStartDate, quarterEndDate))),
    ),
    SummaryListRow(
      key = Key(Text(messages("confirmation.table.data.submitted.label")), classes = "govuk-!-font-weight-regular"),
      value = Value(Text(formatSubmittedDate(ZonedDateTime.now(ZoneId.of("Europe/London"))))),
    )
  ) ++ (if (hasVersion) {
    Seq(SummaryListRow(
      key = Key(Text(messages("confirmation.table.submission.number.label")), classes = "govuk-!-font-weight-regular"),
      value = Value(Text(s"$versionNumber")),
    ))
  } else {
    Nil
  })
}
