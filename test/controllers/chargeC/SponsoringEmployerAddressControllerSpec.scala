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
import forms.chargeC.SponsoringEmployerAddressFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.SponsoringEmployerAddress
import models.requests.IdentifierRequest
import models.{NormalMode, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{SponsoringEmployerAddressPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import play.api.Application
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.govukfrontend.views.viewmodels.select.SelectItem
import views.html.chargeC.SponsoringEmployerAddressView

import scala.concurrent.Future

class SponsoringEmployerAddressControllerSpec extends ControllerSpecBase with MockitoSugar
  with JsonMatchers with OptionValues with TryValues {
  private val userAnswersIndividual: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndIndividual)
  private val userAnswersOrganisation: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndOrganisation)
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private def application: Application = registerApp(applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build())
  private val form = new SponsoringEmployerAddressFormProvider()()
  private val index = 0

  private def httpPathGET: String = controllers.chargeC.routes.SponsoringEmployerAddressController.
    onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url

  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringEmployerAddressController.
    onSubmit(NormalMode, srn, startDate, accessType, versionInt, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "line1" -> Seq("line1"),
    "line2" -> Seq("line2"),
    "townOrCity" -> Seq("town"),
    "county" -> Seq("county"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")

  )

  val sponsoringEmployerAddress: SponsoringEmployerAddress =
    SponsoringEmployerAddress(
      line1 = "line1",
      line2 = "line2",
      line3 = Some("line3"),
      line4 = Some("line4"),
      country = "UK",
      postcode = Some("ZZ1 1ZZ")
    )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "line1" -> Seq.empty,
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[?])).thenReturn(dummyCall.url)
    when(mockAppConfig.validCountryCodes).thenReturn(Seq("UK"))
  }

  "SponsoringEmployerAddress Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

      val request = httpGETRequest(httpPathGET)
      val submitCall = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[SponsoringEmployerAddressView].apply(
        form,
        schemeName,
        submitCall,
        returnUrl,
        "First Last",
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeIndividual.toString}"),
        Seq(SelectItem(Some("")), SelectItem(Some("UK"), "country.UK"))
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }


    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswersIndividual.map(_.set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val request = httpGETRequest(httpPathGET)
      val submitCall = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[SponsoringEmployerAddressView].apply(
        form.fill(sponsoringEmployerAddress),
        schemeName,
        submitCall,
        returnUrl,
        "First Last",
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeIndividual.toString}"),
        Seq(SelectItem(Some("")), SelectItem(Some("UK"), "country.UK"))
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
  }

  "SponsoringEmployerAddress Controller with organisation sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)

      val request = httpGETRequest(httpPathGET)
      val submitCall = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[SponsoringEmployerAddressView].apply(
        form,
        schemeName,
        submitCall,
        returnUrl,
        companyName,
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeOrganisation.toString}"),
        Seq(SelectItem(Some("")), SelectItem(Some("UK"), "country.UK"))
      )(request, messages)

      val result = route(application, request).value

      status(result) mustEqual OK

      compareResultAndView(result, view)
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      val ua = userAnswersOrganisation.map(_.set(SponsoringEmployerAddressPage(index), sponsoringEmployerAddress)).get.toOption.get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))

      val request = httpGETRequest(httpPathGET)
      val submitCall = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url

      val view = application.injector.instanceOf[SponsoringEmployerAddressView].apply(
        form.fill(sponsoringEmployerAddress),
        schemeName,
        submitCall,
        returnUrl,
        companyName,
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeOrganisation.toString}"),
        Seq(SelectItem(Some("")), SelectItem(Some("UK"), "country.UK"))
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
            SponsoringEmployerAddressPage.toString -> Json.toJson(sponsoringEmployerAddress)
          ))
        )
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(SponsoringEmployerAddressPage(index)), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(dummyCall)

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }

    "redirect to Session Expired page for a POST when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
