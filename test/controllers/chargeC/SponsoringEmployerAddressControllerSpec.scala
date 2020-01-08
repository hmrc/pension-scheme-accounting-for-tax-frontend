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
import forms.chargeC.SponsoringEmployerAddressFormProvider
import matchers.JsonMatchers
import models.chargeC.SponsoringEmployerAddress
import models.{GenericViewModel, NormalMode}
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import pages.chargeC.SponsoringEmployerAddressPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class SponsoringEmployerAddressControllerSpec extends ControllerSpecBase with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/sponsoringEmployerAddress.njk"
  private val form = new SponsoringEmployerAddressFormProvider()()
  private def getRoute: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onPageLoad(NormalMode, SampleData.srn).url
  private def postRoute: String = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url

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

  private val jsonToPassToTemplate:Form[SponsoringEmployerAddress]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.SponsoringEmployerAddressController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "companyName" -> SampleData.companyName
  )

  "SponsoringEmployerAddress Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = getRoute,
      page = SponsoringEmployerAddressPage,
      data = SampleData.sponsoringEmployerAddress,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = Some(SampleData.userAnswersWithSchemeNameAndOrganisation)
    )

    behave like controllerWithPOST(
      httpPath = postRoute,
      page = SponsoringEmployerAddressPage,
      data = SampleData.sponsoringEmployerAddress,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid,
      userAnswers = Some(SampleData.userAnswersWithSchemeNameAndOrganisation)
    )
  }
}
