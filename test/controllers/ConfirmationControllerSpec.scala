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

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import controllers.actions.{AllowSubmissionAction, MutableFakeDataRetrievalAction, FakeAllowSubmissionAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.ValueChangeType.ChangeTypeDecrease
import models.ValueChangeType.ChangeTypeIncrease
import models.{SessionAccessData, GenericViewModel, UserAnswers, SessionData, AccessMode}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, when, verify}
import org.mockito.{ArgumentCaptor, Mockito}
import pages.ConfirmSubmitAFTAmendmentValueChangeTypePage
import pages.PSAEmailQuery
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.Call
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Value, Row}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels._
import utils.AFTConstants._

import scala.concurrent.Future

class ConfirmationControllerSpec extends ControllerSpecBase with JsonMatchers {

  import ConfirmationControllerSpec._

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val extraModules: Seq[GuiceableModule] = Seq(bind[AllowSubmissionAction].toInstance(new FakeAllowSubmissionAction))
  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

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
      schemeName = SampleData.schemeName)
  )

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    Mockito.reset(mockRenderer, mockUserAnswersCacheConnector, mockAllowAccessActionProvider)
    when(mockAllowAccessActionProvider.apply(any(), any(), any(), any(), any())).thenReturn(FakeActionFilter)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAppConfig.yourPensionSchemesUrl).thenReturn(testManagePensionsUrl.url)
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))
  }

  "Confirmation Controller" must {

    "return OK and the correct view for submission for a GET" in {
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

    "return OK and the correct view for amendment for a GET" in {
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

    "return OK and the correct view for amendment for a GET when value decreased" in {
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

    "return OK and the correct view for amendment for a GET when value increased" in {
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
      value = Value(Literal(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mm a").format(ZonedDateTime.now(ZoneId.of("Europe/London")))), classes = Nil),
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
