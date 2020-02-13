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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AddMembersFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeG._
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.Future

class AddMembersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeG/addMembers.njk"
  private val form = new AddMembersFormProvider()("chargeG.addMembers.error")
  private def httpPathGET: String = controllers.chargeG.routes.AddMembersController.onPageLoad(srn).url
  private def httpPathPOST: String = controllers.chargeG.routes.AddMembersController.onSubmit(srn).url

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
      submitUrl = controllers.chargeG.routes.AddMembersController.onSubmit(srn).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> "1 January 2020",
    "quarterEnd" -> "30 June 2020",
    "table" -> table
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
  }

  private def ua: UserAnswers = userAnswersWithSchemeName
    .set(MemberDetailsPage(0), memberGDetails).toOption.get
    .set(MemberDetailsPage(1), memberGDetails2).toOption.get
    .set(ChargeAmountsPage(0), chargeAmounts).toOption.get
    .set(ChargeAmountsPage(1), chargeAmounts2).toOption.get
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
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddMembersPage), any(), any(), any())).thenReturn(dummyCall)

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
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
