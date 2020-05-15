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

import scala.concurrent.ExecutionContext.Implicits.global
import config.FrontendAppConfig
import controllers.actions.DataRequiredActionImpl
import controllers.actions.DataUpdateAction
import controllers.actions.FakeIdentifierAction
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.actions.MutableFakeDataUpdateAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.AFTSummaryFormProvider
import forms.MemberSearchFormProvider
import helpers.FormatHelper
import matchers.JsonMatchers
import models.Enumerable
import models.GenericViewModel
import models.Quarter
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.ArgumentCaptor
import org.mockito.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import pages._
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Call
import play.api.mvc.Results
import play.api.test.Helpers.route
import play.api.test.Helpers.status
import play.api.test.Helpers._
import play.twirl.api.Html
import services.AFTService
import services.AllowAccessService
import services.MemberSearchService
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Radios
import utils.AFTSummaryHelper

import scala.concurrent.Future
import models.LocalDateBinder._
import models.MemberDetails
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import services.MemberSearchService.MemberRow
import services.RequestCreationService
import uk.gov.hmrc.viewmodels.SummaryList.Action
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.Text.Message

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach
  with Enumerable.Implicits with Results with ScalaFutures {

  private val mockAllowAccessService = mock[AllowAccessService]
  private val mockAFTService = mock[AFTService]
  private val mockSchemeService = mock[SchemeService]
  private val mockMemberSearchService = mock[MemberSearchService]
  private val fakeDataUpdateAction: MutableFakeDataUpdateAction = new MutableFakeDataUpdateAction()

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AllowAccessService].toInstance(mockAllowAccessService),
      bind[AFTService].toInstance(mockAFTService),
      bind[DataUpdateAction].toInstance(fakeDataUpdateAction),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[MemberSearchService].toInstance(mockMemberSearchService)
    )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val templateToBeRendered = "aftSummary.njk"
  private val form = new AFTSummaryFormProvider()()

  private def httpPathGETNoVersion: String = controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, None).url

  private def httpPathGETVersion: String = controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, Some(SampleData.version)).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, None).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val testManagePensionsUrl = Call("GET", "/scheme-summary")

  private val uaGetAFTDetails = UserAnswers().set(QuarterPage, Quarter("2000-04-01", "2000-05-31")).toOption.get

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])

  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  private val startDateAsString = "2020-04-01"

  private def controllerInstance:AFTSummaryController = {
    val config: FrontendAppConfig=mockAppConfig
    new AFTSummaryController(messagesApi,
      mockUserAnswersCacheConnector,
      mockCompoundNavigator,
      injector.instanceOf[FakeIdentifierAction],
      mutableFakeDataRetrievalAction,
      fakeDataUpdateAction,
      mockAllowAccessActionProvider,
      injector.instanceOf[DataRequiredActionImpl],
      injector.instanceOf[AFTSummaryFormProvider],
      injector.instanceOf[MemberSearchFormProvider],
      injector.instanceOf[MessagesControllerComponents],
      new Renderer(config, mockRenderer),
      config,
      injector.instanceOf[AFTSummaryHelper],
      mockAFTService,
      mockAllowAccessService,
      injector.instanceOf[RequestCreationService],
      mockSchemeService,
      mockMemberSearchService)
  }

  private def searchResultsMemberDetailsChargeD(memberDetails: MemberDetails, totalAmount:BigDecimal, index:Int = 0) = Seq(
    MemberRow(
      memberDetails.fullName,
      Seq(
        Row(
          Key(Message("memberDetails.nino"), Seq("govuk-!-width-three-quarters")),
          Value(Literal(memberDetails.nino), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.chargeType"), Seq("govuk-!-width-three-quarters")),
          Value(Message("aft.summary.lifeTimeAllowance.description"), Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))
        ),
        Row(
          Key(Message("aft.summary.search.amount"), Seq("govuk-!-width-three-quarters")),

          Value(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric"))

        )
      ),
      Seq(
        Action(
          Message("site.view"),
          controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row"))
        ),
        Action(
          Message("site.remove"),
          controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDateAsString, index).url,
          Some(Message("aft.summary.lifeTimeAllowance.visuallyHidden.row"))
        )
      )
    )
  )

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAllowAccessService, mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig, mockMemberSearchService)
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAllowAccessService.filterForIllegalPageAccess(any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(None))
    when(mockSchemeService.retrieveSchemeDetails(any(),any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(testManagePensionsUrl.url)
  }


  private def jsonToPassToTemplate(version: Option[String]): Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), SampleData.srn, startDate),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)

  "AFTSummary Controller" must {
    "return OK and the correct view for a GET where no version is present in the request and call the aft service" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      fakeDataUpdateAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETNoVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = None).apply(form))
    }

    "return OK and the correct view for a GET where a version is present in the request" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      fakeDataUpdateAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETVersion)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(version = Some(version)).apply(form))
    }

    "redirect to next page when user selects yes" in {
      when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any(), any())).thenReturn(SampleData.dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      fakeDataUpdateAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      verify(mockAFTService, never).isSubmissionDisabled(any())
    }

    "redirect to next page when user selects no with submission enabled" in {
      when(mockAFTService.isSubmissionDisabled(any())).thenReturn(false)
      when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any(), any())).thenReturn(SampleData.dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      fakeDataUpdateAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("false")))).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      verify(mockAFTService, times(1)).isSubmissionDisabled(any())
    }

    "remove all data and redirect to scheme summary page when user selects no and submission is disabled" in {
      when(mockAFTService.isSubmissionDisabled(any())).thenReturn(true)
      when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      fakeDataUpdateAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("false")))).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result) mustBe Some(testManagePensionsUrl.url)
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())
      verify(mockAFTService, times(1)).isSubmissionDisabled(any())
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      fakeDataUpdateAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      fakeDataUpdateAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }

    "display search results when Search is triggered" in {
      val searchResult:Seq[MemberRow] = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44"))

      when(mockMemberSearchService.search(any(),any(),any(),any())(any()))
        .thenReturn(searchResult)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      fakeDataUpdateAction.setDataToReturn(userAnswers)

      val fakeRequest = httpPOSTRequest("/", Map("searchText" -> Seq("Search")))

      val result = controllerInstance.onSearchMember(SampleData.srn, startDate, None).apply(fakeRequest)

      status(result) mustEqual OK

      verify(mockMemberSearchService, times(1)).search(any(),any(),any(), Matchers.eq("Search"))(any())
      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      templateCaptor.getValue mustBe templateToBeRendered

      val expectedJson = Json.obj( "list" ->
        Json.toJson(searchResult)
      )
      jsonCaptor.getValue must containJson(expectedJson)
    }
  }
}


