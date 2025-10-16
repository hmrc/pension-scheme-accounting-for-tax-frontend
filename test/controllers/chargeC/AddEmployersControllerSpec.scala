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

package controllers.chargeC

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData.*
import forms.AddMembersFormProvider
import helpers.{DeleteChargeHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder.*
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.requests.IdentifierRequest
import models.{AddEmployersViewModel, Employer, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeC.*
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, *}
import services.{ChargePaginationService, PaginatedMembersInfo, PaginationStats}
import uk.gov.hmrc.govukfrontend.views.Aliases.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.AFTConstants.*
import utils.DateHelper.dateFormatterDMY
import viewmodels.Link
import views.html.chargeC.AddEmployersView

import java.time.LocalDate
import scala.concurrent.Future

class AddEmployersControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val form = new AddMembersFormProvider()("chargeC.addEmployers.error")

  private def httpPathGET: String = controllers.chargeC.routes.AddEmployersController.onPageLoad(srn, startDate, accessType, versionInt).url

  private def httpPathPOST(pageNo: Int): String = controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty
  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"

  private def tableTemp: Table = Table(
    head = Some(Seq(
      HeadCell(Text(Messages("addEmployers.employer.header"))),
      HeadCell(Text(Messages("addEmployers.amount.header")), classes = "govuk-table__header--numeric"),
      HeadCell(HtmlContent(s"""<span class=\"govuk-visually-hidden\">${messages("addEmployers.hiddenText.header.viewSponsoringEmployer")}</span>""")),
      HeadCell(HtmlContent(s"""<span class=\"govuk-visually-hidden\">${messages("addEmployers.hiddenText.header.removeSponsoringEmployer")}</span>"""))
    )),
    rows = Seq(
        Seq(
          TableRow(Text("first last"), classes = cssHalfWidth),
          TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44))), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
          TableRow(HtmlContent(s"<a class=\"govuk-link\" id=\"employer-0-view\" href=\"viewlink1\"><span aria-hidden=\"true\">View</span><span class=\"govuk-visually-hidden\">View first last’s authorised surplus payments charge</span></a>"), classes = cssQuarterWidth),
          TableRow(HtmlContent( s"<a class=\"govuk-link\" id=\"employer-0-remove\" href=\"removelink1\"><span aria-hidden=\"true\">Remove</span><span class=\"govuk-visually-hidden\">Remove first last’s authorised surplus payments charge</span></a>"), classes = cssQuarterWidth)
        ),
      Seq(
          TableRow(Text("Joe Bloggs"), classes = cssHalfWidth),
          TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44))), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
          TableRow(HtmlContent( s"<a class=\"govuk-link\" id=\"employer-1-view\" href=\"viewlink2\"><span aria-hidden=\"true\">View</span><span class=\"govuk-visually-hidden\">View Joe Bloggs’s authorised surplus payments charge</span></a>"), classes = cssQuarterWidth),
          TableRow(HtmlContent( s"<a class=\"govuk-link\" id=\"employer-1-remove\" href=\"removelink2\"><span aria-hidden=\"true\">Remove</span><span class=\"govuk-visually-hidden\">Remove Joe Bloggs’s authorised surplus payments charge</span></a>"), classes =cssQuarterWidth)
        ),
      Seq(
        TableRow(Text("Joe Bloggs"), classes = cssHalfWidth),
        TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44))), classes = s"$cssQuarterWidth govuk-table__header--numeric"),
        TableRow(HtmlContent( s"<a class=\"govuk-link\" id=\"employer-2-view\" href=\"viewlink3\"><span aria-hidden=\"true\">View</span><span class=\"govuk-visually-hidden\">View Joe Bloggs’s authorised surplus payments charge</span></a>"), classes = cssQuarterWidth),
        TableRow(HtmlContent( s"<a class=\"govuk-link\" id=\"employer-2-remove\" href=\"removelink3\"><span aria-hidden=\"true\">Remove</span><span class=\"govuk-visually-hidden\">Remove Joe Bloggs’s authorised surplus payments charge</span></a>"), classes =cssQuarterWidth)
      ),
      Seq(
          TableRow(Text(Messages("addMembers.total")), classes ="govuk-!-font-weight-bold govuk-table__header--numeric"),
          TableRow(Text(FormatHelper.formatCurrencyAmountAsString(BigDecimal(66.88))), classes ="govuk-!-font-weight-bold govuk-table__header--numeric"),
          TableRow(Text("")),
          TableRow(Text(""))
        )
      ),
    attributes = Map("role" -> "table")
    )

  private def getAddEmployersViewModel(pageNo: Int): AddEmployersViewModel = {
    AddEmployersViewModel(
      schemeName,
      returnUrl= controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      quarterStart = LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
      quarterEnd= LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
      canChange= true,
      paginationStatsStartMember= pageNo,
      paginationStatsLastMember= 3,
      paginationStatsTotalMembers= 3,
      radios= utils.Radios.yesNo(form("value"))
    )
  }

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get
    .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), sponsoringOrganisationDetails).toOption.get
    .set(ChargeCDetailsPage(0), chargeCDetails).toOption.get
    .set(ChargeCDetailsPage(1), chargeCDetails).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get

  val expectedJson: JsObject = ua.set(AddEmployersPage, true).get.data

  private val expectedMembers = Seq(
    Employer(0, "first last", BigDecimal(33.44), "viewlink1", "removelink1"),
    Employer(1, "Joe Bloggs", BigDecimal(33.44), "viewlink2", "removelink2"),
    Employer(2, "Joe Bloggs", BigDecimal(33.44), "viewlink3", "removelink3")
  )

  private def expectedPaginatedEmployersInfo(startMember: Int = 1): Option[PaginatedMembersInfo] =
    Some(PaginatedMembersInfo(
      itemsForCurrentPage = Right(expectedMembers),
      paginationStats = PaginationStats(
        currentPage = 1,
        startMember = startMember,
        lastMember = 3,
        totalMembers = 3,
        totalPages = 1,
        totalAmount = BigDecimal(66.88)
      )
    ))


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
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockMemberPaginationService
      .getItemsPaginated(any(), any(), any(), any(), any()))
      .thenReturn(expectedPaginatedEmployersInfo())
    when(mockMemberPaginationService.pagerNavSeq(any(), any())(any()))
      .thenReturn(dummyPagerNavSeq)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val pageCaptor = ArgumentCaptor.forClass(classOf[Int])

  "AddEmployers Controller" must {
    "return OK and the correct view for a GET and get first page" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedEmployersInfo())
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val request = httpGETRequest(httpPathGET)

      val  viewModel = getAddEmployersViewModel(1)
      val view = application.injector.instanceOf[AddEmployersView].apply(
        form,
        viewModel,
        controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, 1),
        table = tableTemp, pageLinksSeq = dummyPagerNavSeq
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET with page no 2" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedEmployersInfo(2))
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val request = httpGETRequest(httpPathGET)

      val  viewModel = getAddEmployersViewModel(2)
      val view = application.injector.instanceOf[AddEmployersView].apply(
        form,
        viewModel,
        controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, 1),
        table = tableTemp, pageLinksSeq = dummyPagerNavSeq
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)

    }

    "return OK and the correct view for a GET with onPageLoadWithPageNo" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedEmployersInfo())
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      def httpPathGET: String = controllers.chargeC.routes.AddEmployersController.onPageLoadWithPageNo(srn, startDate, accessType, versionInt, 2).url
      val request = httpGETRequest(httpPathGET)

      val  viewModel = getAddEmployersViewModel(1)
      val view = application.injector.instanceOf[AddEmployersView].apply(
        form,
        viewModel,
        controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, 2),
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

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(AddEmployersPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

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
