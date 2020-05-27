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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.DeleteFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.{GenericViewModel, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, Matchers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.PSTRQuery
import pages.chargeC._
import play.api.Application
import play.api.data.Form
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.DeleteAFTChargeService
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.Future

class DeleteEmployerControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {

  val userAnswersWithSchemeNameAndTwoIndividuals: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
    .set(SponsoringIndividualDetailsPage(1), sponsoringIndividualDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeIndividual).toOption.get

  private val answersIndividual: UserAnswers = userAnswersWithSchemeNameAndTwoIndividuals
    .set(ChargeCDetailsPage(0), chargeCDetails).success.value
    .set(ChargeCDetailsPage(1), chargeCDetails).success.value
    .set(PSTRQuery, pstr).success.value

  private val userAnswersWithSchemeNameAndTwoOrganisations: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .set(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetails).toOption.get
    .set(SponsoringOrganisationDetailsPage(1), sponsoringOrganisationDetails).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).toOption.get
    .set(WhichTypeOfSponsoringEmployerPage(1), SponsoringEmployerTypeOrganisation).toOption.get

  private val answersOrg: UserAnswers = userAnswersWithSchemeNameAndTwoOrganisations
    .set(ChargeCDetailsPage(0), chargeCDetails).success.value
    .set(ChargeCDetailsPage(1), chargeCDetails).success.value
    .set(PSTRQuery, pstr).success.value

  private val mockDeleteAFTChargeService: DeleteAFTChargeService = mock[DeleteAFTChargeService]

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()

  private val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,
      Seq(bind[DeleteAFTChargeService].toInstance(mockDeleteAFTChargeService))).build()


  private def onwardRoute = Call("GET", "/foo")

  private val employerNameIndividual = "First Last"
  private val employerNameOrg = "Big Company"
  private val formProvider = new DeleteFormProvider()
  private val form: Form[Boolean] = formProvider(messages("deleteEmployer.chargeC.error.required", employerNameIndividual))

  private def httpPathGET: String = routes.DeleteEmployerController.onPageLoad(srn, startDate, 0).url

  private def httpPathPOST: String = routes.DeleteEmployerController.onSubmit(srn, startDate, 0).url

  private val viewModel = GenericViewModel(
    submitUrl = httpPathPOST,
    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
    schemeName = schemeName)

  "DeleteEmployer Controller" must {

    "return OK and the correct view for a GET on deleting an individual" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)

      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(answersIndividual))

      val request = FakeRequest(GET, httpPathGET)

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(form("value")),
        "employerName" -> employerNameIndividual
      )

      templateCaptor.getValue mustEqual "chargeC/deleteEmployer.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "return OK and the correct view for a GET on deleting an organisation" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)

      when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(answersOrg))

      val request = FakeRequest(GET, httpPathGET)

      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, request).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      val expectedJson = Json.obj(
        "form" -> form,
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(form("value")),
        "employerName" -> employerNameOrg
      )

      templateCaptor.getValue mustEqual "chargeC/deleteEmployer.njk"

      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to the next page when valid data is submitted and re-submit the data to DES with the deleted individual marked as deleted" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      when(mockDeleteAFTChargeService.deleteAndFileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(answersIndividual))

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      val expectedUA = userAnswersWithSchemeNamePstrQuarter
        .set(SponsoringIndividualDetailsPage(0), sponsoringIndividualDetails).toOption.get
        .set(ChargeCDetailsPage(0), chargeCDetails).success.value
        .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeIndividual).toOption.get
        .set(PSTRQuery, pstr).success.value
        .set(TotalChargeAmountPage, BigDecimal(33.44)).toOption.get

      verify(mockDeleteAFTChargeService, times(1)).deleteAndFileAFTReturn(Matchers.eq(pstr),
        Matchers.eq(expectedUA))(any(), any(), any())
    }

    "redirect to the next page when valid data is submitted and re-submit the data to DES with the deleted organisation marked as deleted" in {
      when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(onwardRoute.url)
      when(mockUserAnswersCacheConnector.save(any(), any(), any(), any())(any(), any())) thenReturn Future.successful(Json.obj())
      when(mockCompoundNavigator.nextPage(any(), any(), any(), any(), any())(any())).thenReturn(onwardRoute)
      when(mockDeleteAFTChargeService.deleteAndFileAFTReturn(any(), any())(any(), any(), any())).thenReturn(Future.successful(()))

      mutableFakeDataRetrievalAction.setDataToReturn(Some(answersOrg))

      val request =
        FakeRequest(POST, httpPathGET)
          .withFormUrlEncodedBody(("value", "true"))

      val result = route(application, request).value

      status(result) mustEqual SEE_OTHER

      redirectLocation(result).value mustEqual onwardRoute.url

      val expectedUA =
        userAnswersWithSchemeNamePstrQuarter
          .set(SponsoringOrganisationDetailsPage(0), sponsoringOrganisationDetails).toOption.get
          .set(WhichTypeOfSponsoringEmployerPage(0), SponsoringEmployerTypeOrganisation).toOption.get
          .set(ChargeCDetailsPage(0), chargeCDetails).success.value
          .set(PSTRQuery, pstr).success.value
          .set(TotalChargeAmountPage, BigDecimal(33.44)).toOption.get

      verify(mockDeleteAFTChargeService, times(1)).deleteAndFileAFTReturn(Matchers.eq(pstr),
        Matchers.eq(expectedUA))(any(), any(), any())
    }
  }
}
