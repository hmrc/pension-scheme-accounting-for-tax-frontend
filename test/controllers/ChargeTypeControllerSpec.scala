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

package controllers

import behaviours.ControllerBehaviours
import data.SampleData
import forms.ChargeTypeFormProvider
import models.ChargeType.ChargeTypeAnnualAllowance
import models.{ChargeType, Enumerable, GenericViewModel, NormalMode}
import org.scalatest.BeforeAndAfterEach
import pages.ChargeTypePage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}

class ChargeTypeControllerSpec extends ControllerBehaviours with BeforeAndAfterEach with Enumerable.Implicits {

  private val template = "chargeType.njk"
  private def form = new ChargeTypeFormProvider()()

  private def chargeTypeGetRoute: String = controllers.routes.ChargeTypeController.onPageLoad(NormalMode, SampleData.srn).url
  private def chargeTypePostRoute: String = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq(ChargeTypeAnnualAllowance.toString)
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("Unknown Charge")
  )

  private val jsonToTemplate: Form[ChargeType] => JsObject = form => Json.obj(
    fields = "form" -> form,
    "radios" -> ChargeType.radios(form),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.ChargeTypeController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "ChargeDetails Controller" must {

    behave like controllerWithGET(
      httpPath = chargeTypeGetRoute,
      page = ChargeTypePage,
      data = ChargeTypeAnnualAllowance,
      form = form,
      templateToBeRendered = template,
      jsonToPassToTemplate = jsonToTemplate
    )

    behave like controllerWithPOST(
      httpPath = chargeTypePostRoute,
      page = ChargeTypePage,
      data = ChargeTypeAnnualAllowance,
      form = form,
      templateToBeRendered = template,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
