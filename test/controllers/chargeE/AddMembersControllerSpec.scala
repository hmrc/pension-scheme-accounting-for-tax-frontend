/*
 * Copyright 2019 HM Revenue & Customs
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

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeE.AddMembersFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, NormalMode, YearRange}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeE._
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

class AddMembersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeE/addMembers.njk"
  private val form = new AddMembersFormProvider()()
  private def addMembersGetRoute: String = controllers.chargeE.routes.AddMembersController.onPageLoad(SampleData.srn).url
  private def addMembersPostRoute: String = controllers.chargeE.routes.AddMembersController.onSubmit(SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty

  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"

  private def membersJson = Json.arr( Json.obj(
    "key" -> Json.obj(
      "text" -> "Member",
      "classes" -> cssHalfWidth),
      "value" -> Json.obj(
        "text" ->"Charge amount",
        "classes" -> cssQuarterWidth)
  ),
    Json.obj("key" -> Json.obj("text" -> "first last",
      "classes" -> cssHalfWidth),
      "value" -> Json.obj(
        "text" -> "£33.44",
        "classes" -> cssQuarterWidth),
      "actions" -> Json.obj("items" ->
        Json.arr(
          Json.obj(
            "href" -> controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(SampleData.srn, 0).url,
            "text" -> "View"),
          Json.obj("href" -> controllers.chargeE.routes.DeleteMemberController.onPageLoad(NormalMode, SampleData.srn, 0).url,
            "text" -> "Remove")))),
    Json.obj("key" -> Json.obj("text" -> "Joe Bloggs",
      "classes" -> cssHalfWidth),
      "value" -> Json.obj(
        "text" -> "£33.44",
        "classes" -> cssQuarterWidth),
      "actions" -> Json.obj("items" ->
        Json.arr(
          Json.obj(
            "href" -> controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(SampleData.srn, 1).url,
            "text" -> "View"),
          Json.obj("href" -> controllers.chargeE.routes.DeleteMemberController.onPageLoad(NormalMode, SampleData.srn, 1).url,
            "text" -> "Remove")))),
    Json.obj("key" -> Json.obj("text" -> "",
      "classes" -> cssHalfWidth),
      "value" -> Json.obj("text" -> "Total £66.88",
        "classes" -> cssQuarterWidth))
  )

  private val jsonToPassToTemplate:Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeE.routes.AddMembersController.onSubmit(SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> "1 April 2020",
    "quarterEnd" -> "30 June 2020",
    "members" -> membersJson
  )

  private def ua = SampleData.userAnswersWithSchemeName
    .set(MemberDetailsPage(0), SampleData.memberDetails).toOption.get
    .set(MemberDetailsPage(1), SampleData.memberDetails2).toOption.get
    .set(AnnualAllowanceYearPage(0), YearRange.CurrentYear).toOption.get
    .set(AnnualAllowanceYearPage(1), YearRange.CurrentYear).toOption.get
    .set(ChargeDetailsPage(0), SampleData.chargeEDetails).toOption.get
    .set(ChargeDetailsPage(1), SampleData.chargeEDetails).toOption.get
    .set(TotalChargeAmountPage, BigDecimal(66.88)).toOption.get
  val expectedJson: JsObject = ua.set(AddMembersPage, true).get.data

  "AddMembers Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(addMembersGetRoute)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate.apply(form))

      application.stop()
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpGETRequest(addMembersGetRoute)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url

      application.stop()
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddMembersPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(addMembersPostRoute, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture)(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(SampleData.dummyCall.url)

      application.stop()
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val result = route(application, httpPOSTRequest(addMembersPostRoute, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any())(any(), any())

      application.stop()
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      val application = applicationBuilder(userAnswers = None).build()

      val result = route(application, httpPOSTRequest(addMembersPostRoute, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
      application.stop()
    }
  }
}
