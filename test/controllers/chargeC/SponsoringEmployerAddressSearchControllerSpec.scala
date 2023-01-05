/*
 * Copyright 2023 HM Revenue & Customs
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

import audit.{AddressLookupAuditEvent, AuditService, StartNewAFTAuditEvent}
import connectors.AddressLookupConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.chargeC.SponsoringEmployerAddressSearchFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.requests.IdentifierRequest
import models.{GenericViewModel, NormalMode, SponsoringEmployerType, TolerantAddress, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{SponsoringEmployerAddressSearchPage, SponsoringOrganisationDetailsPage, WhichTypeOfSponsoringEmployerPage}
import play.api.Application
import play.api.data.Form
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class SponsoringEmployerAddressSearchControllerSpec
  extends ControllerSpecBase
    with MockitoSugar
    with NunjucksSupport
    with JsonMatchers
    with OptionValues
    with TryValues {
  private val userAnswersIndividual: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndIndividual)
  private val userAnswersOrganisation: Option[UserAnswers] = Some(userAnswersWithSchemeNameAndOrganisation)
  private val mockAddressLookupConnector = mock[AddressLookupConnector]
  private val mockAuditService = mock[AuditService]
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val application: Application =
    applicationBuilderMutableRetrievalAction(
      mutableFakeDataRetrievalAction,
      extraModules = Seq(
        bind[AddressLookupConnector].toInstance(mockAddressLookupConnector),
        bind[AuditService].toInstance(mockAuditService)
      )
    ).build()
  private val templateToBeRendered = "chargeC/sponsoringEmployerAddressSearch.njk"
  private val form = new SponsoringEmployerAddressSearchFormProvider()()
  private val index = 0
  private val postcode = "ZZ1 1ZZ"
  private val seqAddresses =
    Seq[TolerantAddress](TolerantAddress(Some("addr1"), Some("addr2"), Some("addr3"), Some("addr4"), Some("postcode"), Some("UK")))

  private def httpPathGET: String =
    controllers.chargeC.routes.SponsoringEmployerAddressSearchController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url

  private def httpPathPOST: String =
    controllers.chargeC.routes.SponsoringEmployerAddressSearchController.onSubmit(NormalMode, srn, startDate, accessType, versionInt, index).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(postcode)
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("")
  )

  private def jsonToPassToTemplate(sponsorName: String, isSelected: Boolean = false, sponsorType: SponsoringEmployerType): Form[String] => JsObject =
    form =>
      Json.obj(
        "form" -> form,
        "viewModel" -> GenericViewModel(
          submitUrl = controllers.chargeC.routes.SponsoringEmployerAddressSearchController
            .onSubmit(NormalMode, srn, startDate, accessType, versionInt, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
          schemeName = schemeName
        ),
        "sponsorName" -> sponsorName,
        "employerType" -> Messages(s"chargeC.employerType.${sponsorType.toString}"),
        "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(NormalMode, srn, startDate, accessType, versionInt, index).url
      )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditService)
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockAppConfig.validCountryCodes).thenReturn(Seq("UK"))
  }

  "EnterPostcode Controller with individual sponsor" must {
    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersIndividual)
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(
        jsonToPassToTemplate(sponsorName = s"${sponsoringIndividualDetails.firstName} ${sponsoringIndividualDetails.lastName}",
          sponsorType = SponsoringEmployerTypeIndividual).apply(form))
    }

    "redirect to Session Expired page for a GET when there is no data" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }

    "EnterPostcode Controller with organisation sponsor" must {
      "return OK and the correct view for a GET" in {
        mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplate(sponsorName = companyName,
          sponsorType = SponsoringEmployerTypeOrganisation).apply(form))
      }
    }

    "Save data to user answers and redirect to next page when valid data is submitted and send audit event" in {
      val eventCaptor = ArgumentCaptor.forClass(classOf[StartNewAFTAuditEvent])
      val expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          "employers" -> Json.arr(
            Json.obj(
              SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
              WhichTypeOfSponsoringEmployerPage.toString -> "organisation"
            ))
        ),
        SponsoringEmployerAddressSearchPage.toString -> seqAddresses
      )

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(SponsoringEmployerAddressSearchPage(index)), any(), any(), any(), any(), any(), any())(any()))
        .thenReturn(dummyCall)
      when(mockAddressLookupConnector.addressLookupByPostCode(any())(any(), any())).thenReturn(Future.successful(seqAddresses))

      mutableFakeDataRetrievalAction.setDataToReturn(userAnswersOrganisation)

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())

      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(dummyCall.url)

      verify(mockAuditService, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      eventCaptor.getValue mustEqual AddressLookupAuditEvent(postcode)
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
