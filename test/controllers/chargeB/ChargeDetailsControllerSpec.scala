/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.chargeB

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeB.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeB.ChargeBDetails
import models.{GenericViewModel, NormalMode}
import pages.chargeB.ChargeBDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeB/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider()()
  private def chargeDetailsGetRoute: String = controllers.chargeB.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def chargeDetailsPostRoute: String = controllers.chargeB.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "numberOfDeceased" -> Seq("4"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "numberOfDeceased" -> Seq("1.5"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val jsonToPassToTemplate:Form[ChargeBDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeB.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "ChargeDetails Controller" must {
    behave like controllerWithGET(
      httpPath = chargeDetailsGetRoute,
      page = ChargeBDetailsPage,
      data = SampleData.chargeBDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = chargeDetailsPostRoute,
      page = ChargeBDetailsPage,
      data = SampleData.chargeBDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
