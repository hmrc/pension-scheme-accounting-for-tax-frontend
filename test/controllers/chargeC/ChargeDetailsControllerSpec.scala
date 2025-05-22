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
import data.SampleData._
import forms.chargeC.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeOrganisation
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.chargeC.{ChargeCDetailsPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import play.api.Application
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import utils.AFTConstants.{QUARTER_END_DATE, QUARTER_START_DATE}
import views.html.chargeC.ChargeDetailsView

import scala.concurrent.Future

class ChargeDetailsControllerSpec extends ControllerSpecBase with JsonMatchers {
  private val userAnswers: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndOrganisation)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  private val form = new ChargeDetailsFormProvider().apply(QUARTER_START_DATE, QUARTER_END_DATE, minimumChargeValueAllowed = BigDecimal("0.01"))
  private val index = 0

  private def httpPathGET: String = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url

  private def httpPathPOST: String = controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "paymentDate.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "paymentDate.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesWithZeroAmount: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq(QUARTER_START_DATE.getDayOfMonth.toString),
    "paymentDate.month" -> Seq(QUARTER_START_DATE.getMonthValue.toString),
    "paymentDate.year" -> Seq(QUARTER_START_DATE.getYear.toString),
    "amountTaxDue" -> Seq("0.00")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq.empty,
    "paymentDate.month" -> Seq("4"),
    "paymentDate.year" -> Seq("2019"),
    "amountTaxDue" -> Seq("33.44")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }


  "ChargeDetails Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form,
        schemeName,
        controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        sponsorName = companyName,
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeOrganisation.toString}")
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswers.map(_.set(ChargeCDetailsPage(index), chargeCDetails)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Option(ua))

      val request = httpGETRequest(httpPathGET)

      val view = application.injector.instanceOf[ChargeDetailsView].apply(
        form.fill(chargeCDetails),
        schemeName,
        controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index),
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
        sponsorName = companyName,
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeOrganisation.toString}")
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "Save data to user answers and redirect to next page when valid data is submitted" in {

      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(Json.obj(
            SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
            WhichTypeOfSponsoringEmployerPage.toString -> "organisation",
            ChargeCDetailsPage.toString -> Json.toJson(chargeCDetails)
          )
          )
        )
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeCDetailsPage(index)), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "return a BAD REQUEST when zero amount is submitted and in precompile mode" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataPreCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual BAD_REQUEST
    }

    "Return a redirect when zero amount is submitted and new return flag is NOT set" in {
      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(ChargeCDetailsPage(index)), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswers)
      mutableFakeDataRetrievalAction.setSessionData(sessionData(sessionAccessData = sessionAccessDataCompile))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesWithZeroAmount)).value

      status(result) mustEqual SEE_OTHER
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
