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

import controllers.actions.{DataUpdateAction, MutableFakeDataRetrievalAction, MutableFakeDataUpdateAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.AFTSummaryFormProvider
import helpers.{AFTSummaryHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.{AccessMode, GenericViewModel, MemberDetails, Quarter, UserAnswers}
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import pages._
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.MemberSearchService.MemberRow
import services.{AFTService, MemberSearchService, SchemeService}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import uk.gov.hmrc.viewmodels.SummaryList.{Action, Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.{Literal, Message}
import utils.AFTConstants.QUARTER_END_DATE
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.Future

class AFTSummaryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with BeforeAndAfterEach {

  import AFTSummaryControllerSpec._

  private val mockAllowAccessService = mock[AllowAccessService]
  private val mockAFTService = mock[AFTService]
  private val mockSchemeService = mock[SchemeService]
  private val mockMemberSearchService = mock[MemberSearchService]
  private val mockAFTSummaryHelper = mock[AFTSummaryHelper]
  private val fakeDataUpdateAction: MutableFakeDataUpdateAction = new MutableFakeDataUpdateAction()

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AllowAccessService].toInstance(mockAllowAccessService),
      bind[AFTService].toInstance(mockAFTService),
      bind[DataUpdateAction].toInstance(fakeDataUpdateAction),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[MemberSearchService].toInstance(mockMemberSearchService),
      bind[AFTSummaryHelper].toInstance(mockAFTSummaryHelper)
    )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  private val templateCaptor = ArgumentCaptor.forClass(classOf[String])
  private val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

  override def beforeEach: Unit = {
    super.beforeEach()
    reset(mockAllowAccessService, mockUserAnswersCacheConnector, mockRenderer, mockAFTService, mockAppConfig, mockMemberSearchService)
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAllowAccessService.filterForIllegalPageAccess(any(), any(), any(), any(), any(), any())(any())).thenReturn(Future.successful(None))
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(testManagePensionsUrl.url)
    when(mockAFTSummaryHelper.summaryListData(any(), any(), any(), any(), any())(any())).thenReturn(Nil)
    when(mockAFTSummaryHelper.viewAmendmentsLink(any(), any(), any(), any())(any(), any())).thenReturn(emptyHtml)
  }

  private def jsonToPassToTemplate(version: Option[String], includeReturnHistoryLink: Boolean, isAmendment:Boolean): Form[Boolean] => JsObject = { form =>
    val returnHistoryJson = if (includeReturnHistoryLink) {
      Json.obj("returnHistoryURL" -> controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url)
    } else {
      Json.obj()
    }

    val amendmentsLink = if (isAmendment) Json.obj("viewAllAmendmentsLink" -> emptyHtml.toString()) else Json.obj()

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "list" -> Nil,
      "isAmendment" -> isAmendment,
      "viewModel" -> GenericViewModel(
        submitUrl = routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, accessType, versionInt).url,
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        schemeName = SampleData.schemeName
      ),
      "quarterStartDate" -> startDate.format(dateFormatterStartDate),
      "quarterEndDate" -> QUARTER_END_DATE.format(dateFormatterDMY),
      "canChange" -> true,
      "radios" -> Radios.yesNo(form("value"))
    ) ++ returnHistoryJson ++ amendmentsLink
  }

  "AFTSummary Controller" when {

    "calling onPageLoad" must {
      "return OK and the correct view without view all amendments link when compiling initial draft and " +
        "there are no submitted versions available where no version is present in the request" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 1,
              accessMode = AccessMode.PageAccessModeCompile
            )
          )
        )
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGET(None))).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered
        jsonCaptor.getValue must containJson(
          jsonToPassToTemplate(version = None, includeReturnHistoryLink = false, isAmendment = false).apply(form))
      }

      "return OK and the correct view without view all amendments link when compiling initial draft and " +
        "there are no submitted versions available where a version is present in the request" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 1,
              accessMode = AccessMode.PageAccessModeCompile)
          )
        )
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGET(Some(SampleData.version)))).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered
        jsonCaptor.getValue must containJson(
          jsonToPassToTemplate(version = Some(version), includeReturnHistoryLink = false, isAmendment = false).apply(form))
      }

      "include the view all amendments link in json passed to page when there are submitted versions available" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataUpdateAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 2,
              accessMode = AccessMode.PageAccessModeCompile,
              areSubmittedVersionsAvailable = true
            )
          )
        )
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGET(None))).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered
        jsonCaptor.getValue must containJson(
          jsonToPassToTemplate(version = None, includeReturnHistoryLink = true, isAmendment = true).apply(form))
      }

    }

    "calling onSubmit" when {
      "redirect to next page when user selects yes" in {
        when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(SampleData.dummyCall)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataUpdateAction.setDataToReturn(userAnswers)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
        verify(mockAFTService, never).isSubmissionDisabled(any())
      }

      "redirect to next page when user selects no" in {
        when(mockAFTService.isSubmissionDisabled(any())).thenReturn(false)
        when(mockCompoundNavigator.nextPage(Matchers.eq(AFTSummaryPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(SampleData.dummyCall)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataUpdateAction.setDataToReturn(userAnswers)

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("false")))).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, never()).removeAll(any())(any(), any())

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
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
    }

    "calling onSearchMember" when {
      "display search results when Search is triggered and not display view amendments link" in {
        val searchResult: Seq[MemberRow] = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44"))

        when(mockMemberSearchService.search(any(), any(), any(), any(), any(), any())(any(), any()))
          .thenReturn(searchResult)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataUpdateAction.setDataToReturn(userAnswers)

        val fakeRequest = httpPOSTRequest("/", Map("searchText" -> Seq("Search")))

        val controllerInstance = application.injector.instanceOf[AFTSummaryController]

        val result = controllerInstance.onSearchMember(SampleData.srn, startDate, accessType, versionInt).apply(fakeRequest)

        status(result) mustEqual OK

        verify(mockMemberSearchService, times(1)).search(any(), any(), any(), Matchers.eq("Search"), any(), any())(any(), any())
        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
        templateCaptor.getValue mustBe templateToBeRendered

        val expectedJson = Json.obj(
          "list" ->
            Json.toJson(searchResult))
        jsonCaptor.getValue must containJson(expectedJson)
        (jsonCaptor.getValue \ "viewAllAmendmentsLink").isEmpty mustBe true
      }
    }
  }
}

object AFTSummaryControllerSpec {
  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))
  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))
  private val testManagePensionsUrl = Call("GET", "/scheme-summary")
  private val uaGetAFTDetails = UserAnswers().set(QuarterPage, Quarter("2000-04-01", "2000-05-31")).toOption.get
  private val templateToBeRendered = "aftSummary.njk"
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)
  private val form = new AFTSummaryFormProvider()()
  private val emptyHtml = Html("")

  private def httpPathGET(version: Option[String]): String =
    controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, accessType, versionInt).url

  private def searchResultsMemberDetailsChargeD(memberDetails: MemberDetails, totalAmount: BigDecimal, index: Int = 0) = Seq(
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
          controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url,
          None
        ),
        Action(
          Message("site.remove"),
          controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, versionInt, index).url,
          None
        )
      )
    )
  )
}
