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

import connectors.AddressLookupConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeC.SponsoringEmployerAddressResultsFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.SponsoringEmployerTypeIndividual
import models.requests.IdentifierRequest
import models.{NormalMode, TolerantAddress, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC._
import play.api.Application
import play.api.i18n.Messages
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import utils.TwirlMigration
import views.html.chargeC.SponsoringEmployerAddressResultsView

import scala.concurrent.Future

class SponsoringEmployerAddressResultsControllerSpec extends ControllerSpecBase
  with MockitoSugar with JsonMatchers with OptionValues with TryValues {

  private val mockAddressLookupConnector = mock[AddressLookupConnector]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val form = new SponsoringEmployerAddressResultsFormProvider()()
  private val index = 0
  private val firstAddress = TolerantAddress(Some("first1"), Some("first2"), Some("first3"), Some("first4"), Some("firstpost"), Some("UK"))
  private val secondAddress = TolerantAddress(
    Some(sponsoringEmployerAddress.line1),
    Some(sponsoringEmployerAddress.line2),
    sponsoringEmployerAddress.line3,
    sponsoringEmployerAddress.line4,
    sponsoringEmployerAddress.postcode,
    Some(sponsoringEmployerAddress.country)
  )
  private val seqAddresses =
    Seq[TolerantAddress](
      firstAddress,
      secondAddress
    )

  private val emptySeqAddresses =
    Seq[TolerantAddress]()

  private val userAnswersIndividual: Option[UserAnswers] = Some(
    userAnswersWithSchemeNameAndIndividual.setOrException(SponsoringEmployerAddressSearchPage(index), seqAddresses)
  )

  private def httpPathGET: String = controllers.chargeC.routes.SponsoringEmployerAddressResultsController.
    onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url

  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringEmployerAddressResultsController.
    onSubmit(NormalMode, srn, startDate, accessType, versionInt, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("1")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("")
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockAppConfig.validCountryCodes).thenReturn(Seq("UK"))
  }

  "SponsoringEmployerAddressResults Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      val seqAddresses =
        Seq[TolerantAddress](
          secondAddress,
          firstAddress
        )
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

      val request = httpGETRequest(httpPathGET)
      val submitCall = controllers.chargeC.routes.SponsoringEmployerAddressResultsController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url
      val view = application.injector.instanceOf[SponsoringEmployerAddressResultsView].apply(
        form,
        schemeName,
        submitCall,
        returnUrl,
        s"${sponsoringIndividualDetails.firstName} ${sponsoringIndividualDetails.lastName}",
        Messages(s"chargeC.employerType.${SponsoringEmployerTypeIndividual.toString}"),
        routes.SponsoringEmployerAddressController.
          onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url,
        TwirlMigration.convertToRadioItems(seqAddresses)
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
          "employers" -> Json.arr(
            Json.obj(
              SponsoringIndividualDetailsPage.toString -> sponsoringIndividualDetails,
              WhichTypeOfSponsoringEmployerPage.toString -> "individual",
              SponsoringEmployerAddressPage.toString -> sponsoringEmployerAddress
            ))
        ),

        SponsoringEmployerAddressSearchPage.toString -> seqAddresses
      )

      when(mockCompoundNavigator.nextPage(
        ArgumentMatchers.eq(SponsoringEmployerAddressResultsPage(index)), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)
      when(mockAddressLookupConnector.addressLookupByPostCode(any())(any(), any())).thenReturn(Future.successful(seqAddresses))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)

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
