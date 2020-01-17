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
import forms.chargeC.SponsoringOrganisationDetailsFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringOrganisationDetails
import models.{GenericViewModel, NormalMode}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringEmployerAddressPage, SponsoringOrganisationDetailsPage}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class SponsoringOrganisationDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringOrganisationDetails.njk"
  private val form = new SponsoringOrganisationDetailsFormProvider()()
  private def httpPathGET: String = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "name" -> Seq("Big Company"),
    "crn" -> Seq("AB121212")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "name" -> Seq(""),
    "crn" -> Seq("V")
  )

  private val jsonToPassToTemplate:Form[SponsoringOrganisationDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringOrganisationDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "SponsoringOrganisationDetails Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = httpPathGET,
      page = SponsoringOrganisationDetailsPage,
      data = SampleData.sponsoringOrganisationDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )
    behave like controllerWithPOSTWithJson(
      httpPath = httpPathPOST,
      page = SponsoringOrganisationDetailsPage,
      expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          SponsoringOrganisationDetailsPage.toString -> Json.toJson(SampleData.sponsoringOrganisationDetails)
        )
      ),
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid)
  }
}
