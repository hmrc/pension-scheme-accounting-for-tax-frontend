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

package controllers.chargeG

import java.time.LocalDate
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AddMembersFormProvider
import helpers.{DeleteChargeHelper, FormatHelper}
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.chargeG.ChargeAmounts
import models.requests.IdentifierRequest
import models.{UserAnswers, Member, GenericViewModel}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, reset, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeG._
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, redirectLocation, status, _}
import play.twirl.api.Html
import services.{PaginationStats, MemberPaginationService, PaginatedMembersInfo}
import uk.gov.hmrc.viewmodels.{Radios, NunjucksSupport}
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.Future

class AddMembersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val templateToBeRendered = "chargeG/addMembers.njk"
  private val form = new AddMembersFormProvider()("chargeD.addMembers.error")
  private def httpPathGET: String = controllers.chargeG.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url
  private def httpPathGETWithPageNo(pageNo:Int): String =
    controllers.chargeG.routes.AddMembersController.onPageLoadWithPageNo(srn, startDate, accessType, versionInt, pageNo).url
  private def httpPathPOST(pageNo:Int): String = controllers.chargeG.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty
  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private def table = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Member"),
      Json.obj("text" -> "National Insurance number"),
      Json.obj("text" -> "Tax due", "classes" -> "govuk-table__header--numeric"),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>"""),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>""")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "first last","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class= govuk-link id=member-0-view href=viewlink1><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View first last’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class= govuk-link id=member-0-remove href=removelink1><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove first last’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Joe Bloggs","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class= govuk-link id=member-1-view href=viewlink2><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View Joe Bloggs’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class= govuk-link id=member-1-remove href=removelink2><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove Joe Bloggs’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> ""),
        Json.obj("text" -> "Total charge amount for this quarter", "classes" -> "govuk-table__header--numeric"),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(66.88)),"classes" -> s"govuk-table__header--numeric"),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    ),
    "attributes" -> Map("role" -> "table")
  )

  private def jsonToPassToTemplate(pageNo:Int):Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeG.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt, pageNo).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
    "quarterEnd" -> LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
    "table" -> table
  )

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberGDetails).toOption.get
    .set(MemberDetailsPage(1), memberGDetails2).toOption.get
    .set(ChargeAmountsPage(0), chargeAmounts).toOption.get
    .set(ChargeAmountsPage(1), chargeAmounts2).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get


  private val expectedMembers = Seq(
    Member(0, "first last", "AB123456C", BigDecimal(33.44), "viewlink1", "removelink1"),
    Member(1, "Joe Bloggs", "AB123456C", BigDecimal(33.44), "viewlink2", "removelink2")
  )

  private val expectedPaginatedMembersInfo:Option[PaginatedMembersInfo] =
    Some(PaginatedMembersInfo(
      membersForCurrentPage = expectedMembers,
      paginationStats = PaginationStats(
        currentPage = 1,
        startMember = 0,
        lastMember = 0,
        totalMembers = 1,
        totalPages = 1
      )
    ))

  val expectedJson: JsObject = ua.set(AddMembersPage, true).get.data

  private val mockMemberPaginationService = mock[MemberPaginationService]
  private val mockDeleteChargeHelper: DeleteChargeHelper = mock[DeleteChargeHelper]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[DeleteChargeHelper].toInstance(mockDeleteChargeHelper),
    bind[MemberPaginationService].toInstance(mockMemberPaginationService)
  )

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockDeleteChargeHelper)
    when(mockDeleteChargeHelper.isLastCharge(any())).thenReturn(false)
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockMemberPaginationService
      .getMembersPaginated[ChargeAmounts](any(), any(), any(), any(), any(), any(), any())(any()))
      .thenReturn(expectedPaginatedMembersInfo)
  }

  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()
  private val pageCaptor = ArgumentCaptor.forClass(classOf[Int])

  "AddMembers Controller" must {
    "return OK and the correct view for a GET and get first page" in {
      when(mockMemberPaginationService
        .getMembersPaginated[ChargeAmounts](pageCaptor.capture(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(expectedPaginatedMembersInfo)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(pageNo = 1).apply(form))
      pageCaptor.getValue mustBe 1
    }

    "return OK and the correct view for a GET with page no 2" in {
      when(mockMemberPaginationService
        .getMembersPaginated[ChargeAmounts](pageCaptor.capture(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(expectedPaginatedMembersInfo)
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
        .getMembersPaginated[ChargeAmounts](any(), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(None)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual NOT_FOUND
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddMembersPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

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
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
