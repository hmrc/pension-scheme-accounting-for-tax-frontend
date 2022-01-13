/*
 * Copyright 2022 HM Revenue & Customs
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
import data.SampleData._
import forms.AddMembersFormProvider
import helpers.{DeleteChargeHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.requests.IdentifierRequest
import models.{Employer, GenericViewModel, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeC._
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import services.{ChargePaginationService, PaginatedMembersInfo, PaginationStats}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY
import viewmodels.Link

import java.time.LocalDate
import scala.concurrent.Future

class AddEmployersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "chargeC/addEmployers.njk"
  private val form = new AddMembersFormProvider()("chargeC.addEmployers.error")
  private def httpPathGET: String = controllers.chargeC.routes.AddEmployersController.onPageLoad(srn, startDate, accessType, versionInt).url
  private def httpPathGETWithPageNo(pageNo:Int): String =
    controllers.chargeC.routes.AddEmployersController.onPageLoadWithPageNo(srn, startDate, accessType, versionInt, pageNo).url
  private def httpPathPOST(pageNo:Int): String = controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty
  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"
  private def table = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Sponsoring employer"),
      Json.obj("text" -> "Total", "classes" -> "govuk-table__header--numeric"),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.viewSponsoringEmployer")}</span>"""),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.removeSponsoringEmployer")}</span>""")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "first last","classes" -> cssHalfWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class=govuk-link id=employer-0-view href=viewlink1><span aria-hidden=true >View</span><span class= govuk-visually-hidden>View first last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class=govuk-link id=employer-0-remove href=removelink1><span aria-hidden=true >Remove</span><span class= govuk-visually-hidden>Remove first last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Joe Bloggs","classes" -> cssHalfWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class=govuk-link id=employer-1-view href=viewlink2><span aria-hidden=true >View</span><span class= govuk-visually-hidden>View Joe Bloggs’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class=govuk-link id=employer-1-remove href=removelink2><span aria-hidden=true >Remove</span><span class= govuk-visually-hidden>Remove Joe Bloggs’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Total charge amount for this quarter", "classes" -> "govuk-!-font-weight-bold govuk-table__header--numeric"),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(66.88)),"classes" -> s"govuk-!-font-weight-bold govuk-table__header--numeric"),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    ),
    "attributes" -> Map("role" -> "table"),
  )


  private def jsonToPassToTemplate(pageNo:Int):Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
    "quarterEnd" -> LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
    "table" -> table,
    "pageLinksSeq" -> dummyPagerNavSeq
  )

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
    Employer(1, "Joe Bloggs", BigDecimal(33.44), "viewlink2", "removelink2")
  )

  private val expectedPaginatedEmployersInfo:Option[PaginatedMembersInfo] =
    Some(PaginatedMembersInfo(
      itemsForCurrentPage = Right(expectedMembers),
      paginationStats = PaginationStats(
        currentPage = 1,
        startMember = 0,
        lastMember = 0,
        totalMembers = 1,
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

  private val dummyPagerNavSeq = Seq(Link(id = s"test-id", url = "test-target", linkText = Literal("test-text"), hiddenText = None))

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockMemberPaginationService
      .getItemsPaginated(any(), any(), any(), any(), any()))
      .thenReturn(expectedPaginatedEmployersInfo)
    when(mockMemberPaginationService.pagerNavSeq(any(), any()))
      .thenReturn(dummyPagerNavSeq)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val pageCaptor = ArgumentCaptor.forClass(classOf[Int])

  "AddMembers Controller" must {
    "return OK and the correct view for a GET and get first page" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedEmployersInfo)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(pageNo = 1).apply(form))
      pageCaptor.getValue mustBe 1
      verify(mockMemberPaginationService, times(1)).pagerNavSeq(any(), any())
    }

    "return OK and the correct view for a GET with page no 2" in {
      when(mockMemberPaginationService
        .getItemsPaginated(pageCaptor.capture(), any(), any(), any(), any()))
        .thenReturn(expectedPaginatedEmployersInfo)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGETWithPageNo(pageNo = 2))).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(pageNo = 2).apply(form))
      pageCaptor.getValue mustBe 2
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

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpPOSTRequest(httpPathPOST(pageNo = 1), valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpPOSTRequest(httpPathPOST(pageNo = 1), valuesInvalid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
