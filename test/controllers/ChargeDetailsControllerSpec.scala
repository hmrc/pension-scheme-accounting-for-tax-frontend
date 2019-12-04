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

import java.time.LocalDate

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeF.ChargeDetails
import models.{GenericViewModel, NormalMode}
import pages.ChargeDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeF/chargeDetails.njk"
  private def form = new ChargeDetailsFormProvider()()
  private def chargeDetailsRoute: String = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "deregistrationDate.day" -> Seq("3"),
    "deregistrationDate.month" -> Seq("4"),
    "deregistrationDate.year" -> Seq("2003"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "deregistrationDate.day" -> Seq("32"),
    "deregistrationDate.month" -> Seq("13"),
    "deregistrationDate.year" -> Seq("2003"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val jsonToPassToTemplate:Form[ChargeDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "date" -> DateInput.localDate(form("deregistrationDate"))
  )

  private val chargeDetails = ChargeDetails(LocalDate.of(2003, 4, 3), BigDecimal(33.44))

  "ChargeDetails Controller" must {
    behave like controllerWithGET(
      httpPath = chargeDetailsRoute,
      page = ChargeDetailsPage,
      data = chargeDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = chargeDetailsRoute,
      page = ChargeDetailsPage,
      data = chargeDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
