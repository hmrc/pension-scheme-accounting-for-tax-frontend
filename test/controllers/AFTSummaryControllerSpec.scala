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

import controllers.actions.{DataSetupAction, MutableFakeDataRetrievalAction, MutableFakeDataSetupAction}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import forms.{AFTSummaryFormProvider, MemberSearchFormProvider}
import helpers.{AFTSummaryHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{AFTQuarter, AccessMode, MemberDetails, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.ArgumentMatchers
import org.scalatest.BeforeAndAfterEach
import pages._
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Call
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.{Html => TwirlHtml}
import services.MemberSearchService.MemberRow
import services.{AFTService, MemberSearchService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{ActionItem, Actions, Key, SummaryListRow, Value}
import utils.AFTConstants.QUARTER_END_DATE
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.{AFTSummaryViewModel, Radios}
import views.html.AFTSummaryView
import uk.gov.hmrc.govukfrontend.views.html.components.{Hint => GovukHint}
import utils.TwirlMigration

import scala.concurrent.Future

class AFTSummaryControllerSpec extends ControllerSpecBase with JsonMatchers with BeforeAndAfterEach {

  import AFTSummaryControllerSpec._

  private val mockAFTService = mock[AFTService]
  private val mockSchemeService = mock[SchemeService]
  private val mockMemberSearchService = mock[MemberSearchService]
  private val mockAFTSummaryHelper = mock[AFTSummaryHelper]
  private val fakeDataSetupAction: MutableFakeDataSetupAction = new MutableFakeDataSetupAction()

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AFTService].toInstance(mockAFTService),
      bind[DataSetupAction].toInstance(fakeDataSetupAction),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[MemberSearchService].toInstance(mockMemberSearchService),
      bind[AFTSummaryHelper].toInstance(mockAFTSummaryHelper)
    )

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUserAnswersCacheConnector)
    reset(mockRenderer)
    reset(mockAFTService)
    reset(mockMemberSearchService)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(TwirlHtml("")))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(schemeDetails))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(testManagePensionsUrl.url)
    when(mockAFTSummaryHelper.summaryListData(any(), any(), any(), any(), any())(any())).thenReturn(Nil)
  }

  private val viewModel = AFTSummaryViewModel(
    aftSummaryURL = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, versionInt).url,
    returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
    searchHint = GovukHint(content = Text(messages("aft.summary.search.hint"))),
    searchUrl = controllers.routes.AFTSummaryController.onSearchMember(srn, startDate, accessType, versionInt),
    schemeName = schemeName,
    submitCall = routes.AFTSummaryController.onSubmit(srn, startDate, accessType, versionInt),
  )

  "AFTSummary Controller" when {

    "calling onPageLoad" must {
      "return OK and the correct view without view all amendments link when compiling initial draft and " +
        "there are no submitted versions available where no version is present in the request" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 1,
              accessMode = AccessMode.PageAccessModeCompile
            )
          )
        )

        val result = route(application, httpGETRequest(httpPathGET)).value

        val view = application.injector.instanceOf[AFTSummaryView].apply(
          btnText = messages("aft.summary.search.button"),
          canChange = true,
          form = form,
          memberSearchForm = memberSearchForm,
          summaryList = mockAFTSummaryHelper.summaryListData(userAnswersWithSchemeNamePstrQuarter, srn, startDate, accessType, versionInt),
          membersList = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44")),
          quarterEndDate = QUARTER_END_DATE.format(dateFormatterDMY),
          quarterStartDate = startDate.format(dateFormatterStartDate),
          radios = TwirlMigration.toTwirlRadios(Radios.yesNo(form("value"))),
          submissionNumber = "Big Scheme",
          summarySearchHeadingText = "",
          viewAllAmendmentsLink = None,
          viewModel
        )(httpGETRequest(httpPathGET), messages)

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }

      "return OK and the correct view without view all amendments link when compiling initial draft and " +
        "there are no submitted versions available where a version is present in the request" in {
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 1,
              accessMode = AccessMode.PageAccessModeCompile)
          )
        )

        val result = route(application, httpGETRequest(httpPathGET)).value

        val view = application.injector.instanceOf[AFTSummaryView].apply(
          btnText = messages("aft.summary.search.button"),
          canChange = true,
          form = form,
          memberSearchForm = memberSearchForm,
          summaryList = mockAFTSummaryHelper.summaryListData(userAnswersWithSchemeNamePstrQuarter, srn, startDate, accessType, versionInt),
          membersList = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44")),
          quarterEndDate = QUARTER_END_DATE.format(dateFormatterDMY),
          quarterStartDate = startDate.format(dateFormatterStartDate),
          radios = TwirlMigration.toTwirlRadios(Radios.yesNo(form("value"))),
          submissionNumber = "Big Scheme",
          summarySearchHeadingText = "",
          viewAllAmendmentsLink = None,
          viewModel
        )(httpGETRequest(httpPathGET), messages)

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }

      "include the view all amendments link in json passed to page when there are submitted versions available" in {
        val viewAllAmendmentsUrl = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, startDate, accessType, version2Int).url

        val linkText = messages("allAmendments.view.changes.draft.link")

        val viewAllAmendmentsLink = TwirlHtml(s"""<a id=view-amendments-link href=$viewAllAmendmentsUrl class="govuk-link"> $linkText</a>""".stripMargin)

        when(mockAFTSummaryHelper.viewAmendmentsLink(any(), any(), any(), any())(any(), any())).thenReturn(viewAllAmendmentsLink)
        mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setDataToReturn(Some(userAnswersWithSchemeNamePstrQuarter))
        fakeDataSetupAction.setSessionData(
          SampleData.sessionData(
            sessionAccessData = SampleData.sessionAccessData(
              version = 2,
              accessMode = AccessMode.PageAccessModeCompile,
              areSubmittedVersionsAvailable = true
            )
          )
        )

        val result = route(application, httpGETRequest(httpPathGET)).value

        val view = application.injector.instanceOf[AFTSummaryView].apply(
          btnText = messages("aft.summary.search.button"),
          canChange = true,
          form = form,
          memberSearchForm = memberSearchForm,
          summaryList = mockAFTSummaryHelper.summaryListData(userAnswersWithSchemeNamePstrQuarter, srn, startDate, accessType, version2Int),
          membersList = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44")),
          quarterEndDate = QUARTER_END_DATE.format(dateFormatterDMY),
          quarterStartDate = startDate.format(dateFormatterStartDate),
          radios = TwirlMigration.toTwirlRadios(Radios.yesNo(form("value"))),
          submissionNumber = "Draft",
          summarySearchHeadingText = "",
          viewAllAmendmentsLink = Some(viewAllAmendmentsLink),
          viewModel
        )(httpGETRequest(httpPathGET), messages)

        status(result) mustEqual OK

        compareResultAndView(result, view)
      }

    }

    "calling onSubmit" when {
      "redirect to next page when user selects yes" in {
        when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(AFTSummaryPage), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(SampleData.dummyCall)
        when(mockAFTSummaryHelper.viewAmendmentsLink(any(), any(), any(), any())(any(), any())).thenReturn(emptyHtml)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataSetupAction.setDataToReturn(userAnswers)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, never).removeAll(any())(any(), any())

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
        verify(mockAFTService, never).isSubmissionDisabled(any())
      }

      "redirect to next page when user selects no" in {
        when(mockAFTService.isSubmissionDisabled(any())).thenReturn(false)
        when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(AFTSummaryPage), any(), any(), any(), any(), any(), any())(any()))
          .thenReturn(SampleData.dummyCall)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataSetupAction.setDataToReturn(userAnswers)

        val result = route(application, httpPOSTRequest(httpPathPOST, Map("value" -> Seq("false")))).value

        status(result) mustEqual SEE_OTHER

        verify(mockUserAnswersCacheConnector, never).removeAll(any())(any(), any())

        redirectLocation(result) mustBe Some(SampleData.dummyCall.url)
      }

      "return a BAD REQUEST when invalid data is submitted" in {
        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataSetupAction.setDataToReturn(userAnswers)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

        status(result) mustEqual BAD_REQUEST

        verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
      }

      "redirect to Session Expired page for a POST when there is no data" in {
        mutableFakeDataRetrievalAction.setDataToReturn(None)
        fakeDataSetupAction.setDataToReturn(None)

        val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

        status(result) mustEqual SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
        application.stop()
      }
    }

    "calling onSearchMember" when {
      "display search results when Search is triggered and not display view amendments link" in {
        val searchResult: Seq[MemberRow] = searchResultsMemberDetailsChargeD(SampleData.memberDetails, BigDecimal("83.44"))

        when(mockMemberSearchService.search(any(), any(), any(), any(), any(), any())(any(), any()))
          .thenReturn(searchResult)

        mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
        fakeDataSetupAction.setDataToReturn(userAnswers)

        val fakeRequest = httpPOSTRequest("/", Map("searchText" -> Seq("Search")))

        val controllerInstance = application.injector.instanceOf[AFTSummaryController]

        val result = controllerInstance.onSearchMember(SampleData.srn, startDate, accessType, versionInt).apply(fakeRequest)

        status(result) mustEqual OK

        val view = application.injector.instanceOf[AFTSummaryView].apply(
          btnText = messages("aft.summary.searchAgain.button"),
          canChange = true,
          form = form,
          memberSearchForm = memberSearchForm.bind(Map("searchText" -> "Search")),
          summaryList = mockAFTSummaryHelper.summaryListData(userAnswersWithSchemeNamePstrQuarter, srn, startDate, accessType, versionInt),
          membersList = searchResult,
          quarterEndDate = QUARTER_END_DATE.format(dateFormatterDMY),
          quarterStartDate = startDate.format(dateFormatterStartDate),
          radios = TwirlMigration.toTwirlRadios(Radios.yesNo(form("value"))),
          submissionNumber = "Big Scheme",
          summarySearchHeadingText = messages("aft.summary.heading.search.results") + " ",
          viewAllAmendmentsLink = None,
          viewModel
        )(httpGETRequest(httpPathGET), messages)

        compareResultAndView(result, view)
      }
    }
  }
}

object AFTSummaryControllerSpec {
  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))
  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))
  private val testManagePensionsUrl = Call("GET", "/scheme-summary")
  private val uaGetAFTDetails = UserAnswers().set(QuarterPage, AFTQuarter("2000-04-01", "2000-05-31")).toOption.get
  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNamePstrQuarter)
  private val form = new AFTSummaryFormProvider()()
  private val memberSearchForm = new MemberSearchFormProvider()()
  private val emptyHtml = TwirlHtml("")

  private def httpPathGET: String =
    controllers.routes.AFTSummaryController.onPageLoad(SampleData.srn, startDate, accessType, versionInt).url

  private def httpPathPOST: String = controllers.routes.AFTSummaryController.onSubmit(SampleData.srn, startDate, accessType, versionInt).url

  private def searchResultsMemberDetailsChargeD(memberDetails: MemberDetails, totalAmount: BigDecimal, index: Int = 0)(implicit messages: Messages) = Seq(
    MemberRow(
      memberDetails.fullName,
      Seq(
        SummaryListRow(
          Key(Text(messages("memberDetails.nino")), "govuk-!-width-three-quarters"),
          Value(Text(memberDetails.nino), "govuk-!-width-one-quarter govuk-table__cell--numeric")
        ),
        SummaryListRow(
          Key(Text(messages("aft.summary.search.chargeType")), "govuk-!-width-three-quarters"),
          Value(Text(messages("aft.summary.lifeTimeAllowance.description")), "govuk-!-width-one-quarter govuk-table__cell--numeric")
        ),
        SummaryListRow(
          Key(Text(messages("aft.summary.search.amount")), "govuk-!-width-three-quarters"),
          Value(Text(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
            classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
        )
      ),
      Seq(
        Actions(
          items = Seq(ActionItem(
            content = HtmlContent(s"<span aria-hidden=true >${messages("site.view")}</span>"),
            href = controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, versionInt, index).url
          ))
        ),
        Actions(
          items = Seq(ActionItem(
            content = Text(messages("site.remove")),
            href = controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, versionInt, index).url
          ))
        )
      )
    )
  )
}
