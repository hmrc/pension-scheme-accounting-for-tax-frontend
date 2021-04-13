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

package controllers.chargeE

import java.time.LocalDate

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AddMembersFormProvider
import helpers.FormatHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{GenericViewModel, UserAnswers, YearRange}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeE._
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.Future

class AddMembersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeE/addMembers.njk"
  private val form = new AddMembersFormProvider()("chargeD.addMembers.error")
  private def httpPathGET: String = controllers.chargeE.routes.AddMembersController.onPageLoad(srn, startDate, accessType, versionInt).url
  private def httpPathPOST: String = controllers.chargeE.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt).url

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
      Json.obj("text" -> "Charge amount", "classes" -> "govuk-table__header--numeric"),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.viewMember")}</span>"""),
      Json.obj("html" -> s"""<span class=govuk-visually-hidden>${messages("addMember.link.hiddenText.header.removeMember")}</span>""")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "first last","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class= govuk-link id=member-0-view href=/manage-pension-scheme-accounting-for-tax/aa/$QUARTER_START_DATE/$accessType/$versionInt/annual-allowance-charge/1/check-your-answers><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View first last’s annual allowance charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class= govuk-link id=member-0-remove href=/manage-pension-scheme-accounting-for-tax/aa/$QUARTER_START_DATE/$accessType/$versionInt/annual-allowance-charge/1/remove-charge><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove first last’s annual allowance charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Joe Bloggs","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a class= govuk-link id=member-1-view href=/manage-pension-scheme-accounting-for-tax/aa/$QUARTER_START_DATE/$accessType/$versionInt/annual-allowance-charge/2/check-your-answers><span aria-hidden=true>View</span><span class= govuk-visually-hidden>View Joe Bloggs’s annual allowance charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a class= govuk-link id=member-1-remove href=/manage-pension-scheme-accounting-for-tax/aa/$QUARTER_START_DATE/$accessType/$versionInt/annual-allowance-charge/2/remove-charge><span aria-hidden=true>Remove</span><span class= govuk-visually-hidden>Remove Joe Bloggs’s annual allowance charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> ""),
        Json.obj("text" -> "Total", "classes" -> "govuk-table__header--numeric"),
        Json.obj("text" -> FormatHelper.formatCurrencyAmountAsString(BigDecimal(66.88)),"classes" -> s"govuk-table__header--numeric"),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    ),
    "attributes" -> Map("role" -> "table")
  )

  private val jsonToPassToTemplate:Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeE.routes.AddMembersController.onSubmit(srn, startDate, accessType, versionInt).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, QUARTER_START_DATE, accessType, versionInt).url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
    "quarterEnd" -> LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
    "table" -> table
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  private def ua: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(MemberDetailsPage(0), memberDetails).toOption.get
    .set(MemberDetailsPage(1), memberDetails2).toOption.get
    .set(AnnualAllowanceYearPage(0), YearRange.currentYear).toOption.get
    .set(AnnualAllowanceYearPage(1), YearRange.currentYear).toOption.get
    .set(ChargeDetailsPage(0), chargeEDetails).toOption.get
    .set(ChargeDetailsPage(1), chargeEDetails).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get
  val expectedJson: JsObject = ua.set(AddMembersPage, true).get.data

  "AddMembers Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))
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

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
