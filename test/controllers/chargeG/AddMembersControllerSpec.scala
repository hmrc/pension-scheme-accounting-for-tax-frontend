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

package controllers.chargeG

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.AddMembersFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, YearRange}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeG._
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

class AddMembersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeG/addMembers.njk"
  private val form = new AddMembersFormProvider()("chargeD.addMembers.error")
  private def httpPathGET: String = controllers.chargeG.routes.AddMembersController.onPageLoad(SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeG.routes.AddMembersController.onSubmit(SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty

  private val cssQuarterWidth = "govuk-!-width-one-quarter"

  private def table = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Member", "classes" -> cssQuarterWidth),
      Json.obj("text" -> "National Insurance number", "classes" -> cssQuarterWidth),
      Json.obj("text" -> "Tax due", "classes" -> cssQuarterWidth),
      Json.obj("text" -> ""),
      Json.obj("text" -> "")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "first last","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> "£50.00","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=member-0-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/overseas-transfer-charge/1/check-your-answers> View<span class= govuk-visually-hidden>first last’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=member-0-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/overseas-transfer-charge/1/remove-charge> Remove<span class= govuk-visually-hidden>first last’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Joe Bloggs","classes" -> cssQuarterWidth),
        Json.obj("text" -> "AB123456C","classes" -> cssQuarterWidth),
        Json.obj("text" -> "£50.00","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=member-1-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/overseas-transfer-charge/2/check-your-answers> View<span class= govuk-visually-hidden>Joe Bloggs’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> "<a id=member-1-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/overseas-transfer-charge/2/remove-charge> Remove<span class= govuk-visually-hidden>Joe Bloggs’s overseas transfer charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> ""),
        Json.obj("text" -> "Total", "classes" -> "govuk-table__header--numeric"),
        Json.obj("text" -> "£100.00","classes" -> cssQuarterWidth),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    )
  )

  private val jsonToPassToTemplate:Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeG.routes.AddMembersController.onSubmit(SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> "1 April 2020",
    "quarterEnd" -> "30 June 2020",
    "table" -> table
  )

  private def ua = SampleData.userAnswersWithSchemeName
    .set(MemberDetailsPage(0), SampleData.memberGDetails).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberGDetails2).toOption.get
    .set(ChargeAmountsPage(0), SampleData.chargeAmounts).toOption.get
    .set(ChargeAmountsPage(1), SampleData.chargeAmounts2).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get
  val expectedJson: JsObject = ua.set(AddMembersPage, true).get.data

  "AddMembers Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddMembersPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
