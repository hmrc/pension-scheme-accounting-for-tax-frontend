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

package controllers.chargeC

import java.time.LocalDate

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.AddMembersFormProvider
import helpers.FormatHelper
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import pages.chargeC._
import play.api.Application
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{redirectLocation, route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTConstants._
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.Future

class AddEmployersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with FormatHelper {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeC/addEmployers.njk"
  private val form = new AddMembersFormProvider()("chargeC.addEmployers.error")
  private def httpPathGET: String = controllers.chargeC.routes.AddEmployersController.onPageLoad(srn, startDate).url
  private def httpPathPOST: String = controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map.empty

  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"

  private def table = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Sponsoring employer", "classes" -> cssHalfWidth),
      Json.obj("text" -> "Total", "classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
      Json.obj("text" -> ""),
      Json.obj("text" -> "")
    ),
    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "First Last","classes" -> cssHalfWidth),
        Json.obj("text" -> formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a id=employer-0-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/authorised-surplus-payments-charge/1/check-your-answers> View<span class= govuk-visually-hidden>First Last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a id=employer-0-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/authorised-surplus-payments-charge/1/remove-charge> Remove<span class= govuk-visually-hidden>First Last’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Big Company","classes" -> cssHalfWidth),
        Json.obj("text" -> formatCurrencyAmountAsString(BigDecimal(33.44)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("html" -> s"<a id=employer-1-view href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/authorised-surplus-payments-charge/2/check-your-answers> View<span class= govuk-visually-hidden>Big Company’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a id=employer-1-remove href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/authorised-surplus-payments-charge/2/remove-charge> Remove<span class= govuk-visually-hidden>Big Company’s authorised surplus payments charge</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Total", "classes" -> "govuk-table__header--numeric"),
        Json.obj("text" -> formatCurrencyAmountAsString(BigDecimal(66.88)),"classes" -> s"$cssQuarterWidth govuk-table__header--numeric"),
        Json.obj("text" -> ""),
        Json.obj("text" -> "")
      )
    )
  )

  private val jsonToPassToTemplate:Form[Boolean]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.AddEmployersController.onSubmit(srn, startDate).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName),
    "radios" -> Radios.yesNo(form("value")),
    "quarterStart" -> LocalDate.parse(QUARTER_START_DATE).format(dateFormatterDMY),
    "quarterEnd" -> LocalDate.parse(QUARTER_END_DATE).format(dateFormatterDMY),
    "table" -> table
  )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
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

  "AddEmployers Controller" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(ua))

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
      mutableFakeDataRetrievalAction.setDataToReturn(Option(ua))

      when(mockCompoundNavigator.nextPage(Matchers.eq(AddEmployersPage), any(), any(), any(), any())).thenReturn(dummyCall)

      val application = applicationBuilder(userAnswers = Some(ua)).build()

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).save(any(), jsonCaptor.capture, any(), any())(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)

    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Option(ua))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).save(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(UserAnswers()))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
