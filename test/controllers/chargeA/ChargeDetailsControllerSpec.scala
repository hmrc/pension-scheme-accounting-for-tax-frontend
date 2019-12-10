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

package controllers.chargeA

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeA.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeA.ChargeDetails
import models.{GenericViewModel, NormalMode}
import pages.chargeA.ChargeDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeA/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider()()
  private def chargeDetailsGetRoute: String = controllers.chargeA.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def chargeDetailsPostRoute: String = controllers.chargeA.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url
  private val valuesValid: Map[String, Seq[String]] = Map(
    "numberOfMembers" -> Seq("44"),
    "totalAmtOfTaxDueAtLowerRate" -> Seq("33.44"),
    "totalAmtOfTaxDueAtHigherRate" -> Seq("34.34"),
    "totalAmount" -> Seq("67.78")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "numberOfMembers" -> Seq("999999999999999999999999999999999999999"),
    "totalAmtOfTaxDueAtLowerRate" -> Seq("33.44"),
    "totalAmtOfTaxDueAtHigherRate" -> Seq("34.34")
  )

  private val jsonToPassToTemplate:Form[ChargeDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeA.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "ChargeDetails Controller" must {
    behave like controllerWithGET(
      httpPath = chargeDetailsGetRoute,
      page = ChargeDetailsPage,
      data = SampleData.chargeAChargeDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = chargeDetailsPostRoute,
      page = ChargeDetailsPage,
      data = SampleData.chargeAChargeDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
