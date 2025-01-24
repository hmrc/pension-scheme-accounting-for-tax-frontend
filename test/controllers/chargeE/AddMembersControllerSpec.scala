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

package controllers.chargeE

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AddMembersFormProvider
import helpers.{DeleteChargeHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{AddMembersViewModel, Member, UserAnswers, YearRange}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeE._
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import services.{ChargePaginationService, PaginatedMembersInfo, PaginationStats}
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY
import viewmodels.Link
import views.html.chargeE.AddMembersView

import java.time.LocalDate
import scala.concurrent.Future

class AddMembersControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val form = new AddMembersFormProvider()("chargeD.addMembers.error")

  private def httpPathGET: String = controllers.chargeE.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpPathPOST(pageNo: Int): String = controllers.chargeE.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty
  private val cssQuarterWidth = "govuk-!-width-one-quarter"

  private def tableTemp: Table = Table(
    head = Some(Seq(
      HeadCell(Text("Member")),
      HeadCell(Text("National Insurance number")),
      HeadCell(Text("Charge amount"), classes = "govuk-table__header--numeric"),
      HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>""")),
      HeadCell(HtmlContent(s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>"""))
    )),
    rows = Seq(
      Seq(
        TableRow(Text("first last"), classes = cssQuarterWidth),
        TableRow(Text("AB123456C"), classes = cssQuarterWidth),
        TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44))), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
        TableRow(HtmlContent(s"<a class= govuk-link id=member-0-view href=viewlink1><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View first last’s annual allowance charge</span> </a>"), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
        TableRow(HtmlContent(s"<a class= govuk-link id=member-0-remove href=removelink1><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove first last’s annual allowance charge</span> </a>"), classes = cssQuarterWidth)
      ),
      Seq(
        TableRow(Text("Joe Bloggs"), classes =cssQuarterWidth),
        TableRow(Text("AB123456C"), classes = cssQuarterWidth),
        TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44))), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
        TableRow(HtmlContent(s"<a class= govuk-link id=member-1-view href=viewlink2><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View Joe Bloggs’s annual allowance charge</span> </a>"), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
        TableRow(HtmlContent(s"<a class= govuk-link id=member-1-remove href=removelink2><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove Joe Bloggs’s annual allowance charge</span> </a>"), classes = cssQuarterWidth)
      ),
      Seq(
        TableRow(Text("")),
        TableRow(Text("Total charge amount for this quarter"), classes = "govuk-!-font-weight-bold govuk-!-width-one-half"),
        TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(66.88))), classes = s"govuk-!-font-weight-bold govuk-table__header--numeric"),
        TableRow(Text("")),
        TableRow(Text(""))
      )
    ),
    attributes = Map("role" -> "table")
  )

  private def getAddMembersViewModel(pageNo: Int): AddMembersViewModel = {
    AddMembersViewModel(
      schemeName,
      returnUrl= controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      quarterStart = LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
      quarterEnd= LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
      canChange= true,
      paginationStatsStartMember= pageNo,
      paginationStatsLastMember= 2,
      paginationStatsTotalMembers= 3,
      radios= utils.Radios.yesNo(form("value"))
    )
  }

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(MemberDetailsPage(1), memberDetails2).toOption.get
    .set(AnnualAllowanceYearPage(0), YearRange.currentYear).toOption.get
    .set(AnnualAllowanceYearPage(1), YearRange.currentYear).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(ChargeDetailsPage(1), chargeEDetails).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get


  private val expectedMembers = Seq(
    Member(0, "first last", "AB123456C", BigDecimal(33.44), "viewlink1", "removelink1"),
    Member(1, "Joe Bloggs", "AB123456C", BigDecimal(33.44), "viewlink2", "removelink2")
  )

  private val expectedPaginatedMembersInfo: Option[PaginatedMembersInfo] =
    Some(PaginatedMembersInfo(
      itemsForCurrentPage = Left(expectedMembers),
      paginationStats = PaginationStats(
        currentPage = 1,
        startMember = 0,
        lastMember = 0,
        totalMembers = 1,
        totalPages = 1,
        totalAmount = BigDecimal(66.88)
      )
    ))

  val expectedJson: JsObject = ua.set(AddMembersPage, true).get.data

  private val mockMemberPaginationService = mock[ChargePaginationService]
  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[DeleteChargeHelper].toInstance(mockDeleteChargeHelper),
    bind[ChargePaginationService].toInstance(mockMemberPaginationService)
  )

  private val dummyPagerNavSeq = Seq(Link(id = s"test-id", url = "test-target", linkText = Text("test-text"), hiddenText = None))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockMemberPaginationService
      .getItemsPaginated(any(), any(), any(), any(), any()))
      .thenReturn(expectedPaginatedMembersInfo)
    when(mockMemberPaginationService.pagerNavSeq(any(), any())(any()))
      .thenReturn(dummyPagerNavSeq)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val pageCaptor = ArgumentCaptor.forClass(classOf[Int])

  "AddMembers Controller" must {
    "return OK and the correct view for a GET and get first page" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedMembersInfo)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = httpGETRequest(httpPathGET)

      val  viewModel = getAddMembersViewModel(1)
      val view = application.injector.instanceOf[AddMembersView].apply(
        form,
        viewModel,
        controllers.chargeD.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt, 1),
        table = tableTemp, pageLinksSeq = dummyPagerNavSeq
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET with page no 2" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedMembersInfo)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val request = httpGETRequest(httpPathGET)

      val  viewModel = getAddMembersViewModel(2)
      val view = application.injector.instanceOf[AddMembersView].apply(
        form,
        viewModel,
        controllers.chargeD.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt, 1),
        table = tableTemp, pageLinksSeq = dummyPagerNavSeq
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return NOT_FOUND when paginated info not available" in {
      when(mockMemberPaginationService
        .getItemsPaginated(any(), any(), any(), any(), any()))
        .thenReturn(None)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual NOT_FOUND
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(AddMembersPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST(pageNo = 1), valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpPOSTRequest(httpPathPOST(pageNo = 1), valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpPOSTRequest(httpPathPOST(pageNo = 1), valuesInvalid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
