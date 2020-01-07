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
import forms.chargeC.SponsoringIndividualDetailsFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringIndividualDetails
import models.{GenericViewModel, NormalMode}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.SponsoringIndividualDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class SponsoringIndividualDetailsControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringIndividualDetails.njk"
  private val form = new SponsoringIndividualDetailsFormProvider()()
  private def getRoute: String = controllers.chargeC.routes.SponsoringIndividualDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def postRoute: String = controllers.chargeC.routes.SponsoringIndividualDetailsController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("Cyril"),
    "lastName" -> Seq("Wibble"),
    "nino" -> Seq("CS121212C")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq.empty,
    "lastName" -> Seq("Wibble"),
    "nino" -> Seq("CS121212C")
  )

  private val jsonToPassToTemplate:Form[SponsoringIndividualDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringIndividualDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "SponsoringIndividualDetails Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = getRoute,
      page = SponsoringIndividualDetailsPage,
      data = SampleData.sponsoringIndividualDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = postRoute,
      page = SponsoringIndividualDetailsPage,
      data = SampleData.sponsoringIndividualDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}