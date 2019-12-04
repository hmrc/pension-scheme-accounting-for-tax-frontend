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

import java.time.{LocalDate, ZoneOffset}

import base.SpecBase
import behaviours.ControllerBehaviours
import forms.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeF.ChargeDetails
import models.{GenericViewModel, NormalMode}
import pages.ChargeDetailsPage
import play.api.libs.json.Json
import play.api.mvc.Call
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

class ChargeDetailsControllerSpec extends SpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val pageToBeRendered = "chargeF/chargeDetails.njk"
  private val formProvider = new ChargeDetailsFormProvider()

  private def form = formProvider()

  private def onwardRoute = Call("GET", "/foo")

  private val validAnswer = LocalDate.now(ZoneOffset.UTC)

  private def chargeDetailsRoute = controllers.chargeF.routes.ChargeDetailsController.onPageLoad(NormalMode, srn).url

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

  private def viewModel = GenericViewModel(
    submitUrl = controllers.chargeF.routes.ChargeDetailsController.onSubmit(NormalMode, srn).url,
    returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(srn),
    schemeName = schemeName)

  private def jsonForPage = Json.obj(
    "form" -> form,
    "viewModel" -> viewModel,
    "date" -> DateInput.localDate(form("deregistrationDate"))
  )

  private val chargeDetails = ChargeDetails(LocalDate.of(2003, 4, 3), BigDecimal(33.44))

  "ChargeDetails Controller" must {
    behave like controllerWithGET(
      path = chargeDetailsRoute,
      form = form,
      pageToBeRendered = pageToBeRendered,
      data = chargeDetails,
      page = ChargeDetailsPage,
      jsonForPage = jsonForPage)

    behave like controllerWithPOST(
      path = chargeDetailsRoute,
      form = form,
      pageToBeRendered = pageToBeRendered,
      data = chargeDetails,
      page = ChargeDetailsPage,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
