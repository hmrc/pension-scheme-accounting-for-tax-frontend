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

package controllers

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import connectors.FinancialStatementConnector
import controllers.actions.AllowSubmissionAction
import controllers.actions.FakeAllowSubmissionAction
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.ValueChangeType.ChangeTypeDecrease
import models.ValueChangeType.ChangeTypeIncrease
import models.ValueChangeType.ChangeTypeSame
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.PSS_AFT_RETURN
import models.AccessMode
import models.GenericViewModel
import models.SessionAccessData
import models.SessionData
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import pages.ConfirmSubmitAFTAmendmentValueChangeTypePage
import pages.PSAEmailQuery
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.AFTConstants._
import utils.DateHelper.formatSubmittedDate

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
  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val year = QUARTER_START_DATE.getYear

  private def json(isAmendment: Boolean): JsObject = Json.obj(
    fields = "srn" -> SampleData.srn,
    "panelHtml" -> Html(s"${Html(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>""")
      .toString()}").toString(),
    "email" -> email,
    "list" -> rows(isAmendment),
    "isAmendment" -> isAmendment,
    "pensionSchemesUrl" -> testManagePensionsUrl.url,
    "viewModel" -> GenericViewModel(
      submitUrl = submitUrl.url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = SampleData.schemeName),
    "viewPaymentsUrl" -> controllers.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, year).url
  )

  private val schemeFSResponseWithDataForDifferentYear: Seq[SchemeFS] = Seq(
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2021-02-15")),
      totalAmount = 12345.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 23000.55,
      periodStartDate = LocalDate.parse("2021-04-01"),
      periodEndDate = LocalDate.parse("2021-06-30")
    )
  )

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    Mockito.reset(mockRenderer, mockUserAnswersCacheConnector, mockAllowAccessActionProvider)
    when(mockAllowAccessActionProvider.apply(any(), any(), any(), any(), any())).thenReturn(FakeActionFilter)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.yourPensionSchemesUrl).thenReturn(testManagePensionsUrl.url)
    when(mockAppConfig.isFSEnabled).thenReturn(true)
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
    when(mockSchemeService.retrieveSchemeDetails(any(),any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponseAftAndOTC))
  }

  "Confirmation Controller" must {

    "return OK and the correct view for submission for a GET when financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(SampleData.version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.
        set(PSAEmailQuery, email).getOrElse(UserAnswers())))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      templateCaptor.getValue mustEqual "confirmation.njk"
      jsonCaptor.getValue must containJson(json(isAmendment = false))
    }

    "return OK and the correct view for amendment for a GET when financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.setOrException(PSAEmailQuery, email)))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmation.njk"
      jsonCaptor.getValue must containJson(json(isAmendment = true))
    }

    "return OK and the correct view for amendment for a GET when value decreased and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(PSAEmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeDecrease)
        ))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmationAmendDecrease.njk"
      jsonCaptor.getValue must containJson(json(isAmendment = true))
    }

    "return OK and the correct view for amendment for a GET when value increased and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(PSAEmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeIncrease)
        ))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmationAmendIncrease.njk"
      jsonCaptor.getValue must containJson(json(isAmendment = true))
    }

    "return OK and the correct view for amendment for a GET when value not changed and financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(versionNumber, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction
        .setDataToReturn(Some(
          userAnswersWithSchemeNamePstrQuarter
            .setOrException(PSAEmailQuery, email)
            .setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ChangeTypeSame)
        ))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmationNoChange.njk"
      jsonCaptor.getValue must containJson(json(isAmendment = true))
    }

    "return OK but don't include financial info link when no financial info exists for year" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(SampleData.version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.
        set(PSAEmailQuery, email).getOrElse(UserAnswers())))

      when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any()))
        .thenReturn(Future.successful(schemeFSResponseWithDataForDifferentYear))

      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmation.njk"
      (jsonCaptor.getValue \ "viewPaymentsUrl").toOption mustBe None
    }

    "return OK but don't include financial info link for a GET when financial info exists for year but toggle is off" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setSessionData(SessionData("", None,
        SessionAccessData(SampleData.version.toInt, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter.
        set(PSAEmailQuery, email).getOrElse(UserAnswers())))
      when(mockAppConfig.isFSEnabled).thenReturn(false)
      val result = route(application, request).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "confirmation.njk"
      (jsonCaptor.getValue \ "viewPaymentsUrl").toOption mustBe None
    }

    "redirect to Session Expired page when there is no scheme name or pstr or quarter" in {
      val request = FakeRequest(GET, routes.ConfirmationController.onPageLoad(SampleData.srn, QUARTER_START_DATE, accessType, versionInt).url)
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}

object ConfirmationControllerSpec {
  private val testManagePensionsUrl = Call("GET", "/scheme-summary")
  private val quarterEndDate = QUARTER_END_DATE.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
  private val quarterStartDate = QUARTER_START_DATE.format(DateTimeFormatter.ofPattern("d MMMM"))
  private val email = "test@test.com"
  private val versionNumber = 2

  private def submitUrl = Call("GET", s"/manage-pension-scheme-accounting-for-tax/${SampleData.startDate}/${SampleData.srn}/sign-out")

  private def rows(hasVersion: Boolean) = Seq(Row(
    key = Key(msg"confirmation.table.scheme.label", classes = Seq("govuk-!-font-weight-regular")),
    value = Value(Literal(SampleData.schemeName), classes = Nil),
    actions = Nil
  ),
    Row(
      key = Key(msg"confirmation.table.accounting.period.label", classes = Seq("govuk-!-font-weight-regular")),
      value = Value(msg"confirmation.table.accounting.period.value".withArgs(quarterStartDate, quarterEndDate), classes = Nil),
      actions = Nil
    ),
    Row(
      key = Key(msg"confirmation.table.data.submitted.label", classes = Seq("govuk-!-font-weight-regular")),
      value = Value(Literal(formatSubmittedDate(ZonedDateTime.now(ZoneId.of("Europe/London")))), classes = Nil),
      actions = Nil
    )
  ) ++ (if(hasVersion) {
    Seq(Row(
      key = Key(msg"confirmation.table.submission.number.label", classes = Seq("govuk-!-font-weight-regular")),
      value = Value(Literal(s"$versionNumber"), classes = Nil),
      actions = Nil
    ))
  } else {
    Nil
  })
}
