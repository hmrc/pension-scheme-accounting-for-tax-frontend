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

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData.sponsoringOrganisationDetails
import forms.chargeC.SponsoringEmployerAddressFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringEmployerAddress
import models.{GenericViewModel, NormalMode, UserAnswers}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringEmployerAddressPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class SponsoringEmployerAddressControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringEmployerAddress.njk"
  private val form = new SponsoringEmployerAddressFormProvider()()
  private def httpPathGET: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(NormalMode, SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "line1" -> Seq("line1"),
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")

  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "line1" -> Seq.empty,
    "line2" -> Seq("line2"),
    "line3" -> Seq("line3"),
    "line4" -> Seq("line4"),
    "country" -> Seq("UK"),
    "postcode" -> Seq("ZZ1 1ZZ")
  )

  private def jsonToPassToTemplate(sponsorName:String):Form[SponsoringEmployerAddress]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "sponsorName" -> sponsorName
  )

  private val userAnswersIndividual: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNameAndIndividual)
  private val userAnswersOrganisation: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeNameAndOrganisation)

  "SponsoringEmployerAddress Controller with individual sponsor" must {
    behave like controllerWithGETSavedData(
      httpPath = httpPathGET,
      page = SponsoringEmployerAddressPage,
      data = SampleData.sponsoringEmployerAddress,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(sponsorName = "First Last"),
      userAnswers = userAnswersIndividual
    )
  }

  "SponsoringEmployerAddress Controller with organisation sponsor" must {
    behave like controllerWithGETSavedData(
      httpPath = httpPathGET,
      page = SponsoringEmployerAddressPage,
      data = SampleData.sponsoringEmployerAddress,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate(sponsorName = SampleData.companyName),
      userAnswers = userAnswersOrganisation
    )

    behave like controllerWithPOSTWithJson(
      httpPath = httpPathPOST,
      page = SponsoringEmployerAddressPage,
      expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          SponsoringOrganisationDetailsPage.toString -> sponsoringOrganisationDetails,
          IsSponsoringEmployerIndividualPage.toString -> false,
          SponsoringEmployerAddressPage.toString -> Json.toJson(SampleData.sponsoringEmployerAddress)
        )
      ),
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid,
      userAnswers = userAnswersOrganisation
    )
  }
}
