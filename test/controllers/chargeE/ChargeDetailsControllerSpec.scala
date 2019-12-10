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

package controllers.chargeE

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeE.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeE.ChargeEDetails
import models.{GenericViewModel, NormalMode}
import org.mockito.Matchers.any
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Mockito.{times, verify, when}
import pages.chargeE.ChargeDetailsPage
import play.api.test.Helpers._
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, route, status}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport, Radios}

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeE/chargeDetails.njk"
  private val dynamicErrorMsg: String = "The date you received notice to pay the charge must be between 1 April 2020 and 30 June 2020"
  private val form = new ChargeDetailsFormProvider()(dynamicErrorMsg)
  private def chargeDetailsGetRoute: String = controllers.chargeE.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn, 0).url
  private def chargeDetailsPostRoute: String = controllers.chargeE.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "chargeAmount" -> Seq("33.44"),
  "dateNoticeReceived.day" -> Seq("3"),
  "dateNoticeReceived.month" -> Seq("4"),
  "dateNoticeReceived.year" -> Seq("2020"),
    "isPaymentMandatory" -> Seq("true")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "chargeAmount" -> Seq("33.44"),
  "dateNoticeReceived.day" -> Seq("32"),
  "dateNoticeReceived.month" -> Seq("13"),
  "dateNoticeReceived.year" -> Seq("2003"),
    "isPaymentMandatory" -> Seq(true.toString)
  )

  private val jsonToPassToTemplate:Form[ChargeEDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeE.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "date" -> DateInput.localDate(form("dateNoticeReceived")),
    "radios" -> Radios.yesNo(form("isPaymentMandatory")),
    "memberName" -> "Temporary name"
  )

  "ChargeDetails Controller" must {
    behave like controllerWithGET(
      httpPath = chargeDetailsGetRoute,
      page = ChargeDetailsPage(0),
      data = SampleData.chargeEDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = chargeDetailsPostRoute,
      page = ChargeDetailsPage(0),
      data = SampleData.chargeEDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
