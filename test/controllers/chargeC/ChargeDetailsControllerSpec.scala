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
import forms.chargeC.ChargeDetailsFormProvider
import matchers.JsonMatchers
import models.chargeC.ChargeCDetails
import models.{GenericViewModel, NormalMode}
import pages.chargeC.{ChargeCDetailsPage, IsSponsoringEmployerIndividualPage, SponsoringOrganisationDetailsPage}
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport

class ChargeDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  private val templateToBeRendered = "chargeC/chargeDetails.njk"
  private val form = new ChargeDetailsFormProvider()()
  private def httpPathGET: String = controllers.chargeC.routes.ChargeDetailsController.onPageLoad(NormalMode, SampleData.srn).url
  private def httpPathPOST: String = controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq("3"),
    "paymentDate.month" -> Seq("4"),
    "paymentDate.year" -> Seq("2019"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "paymentDate.day" -> Seq.empty,
    "paymentDate.month" -> Seq("4"),
    "paymentDate.year" -> Seq("2019"),
    "amountTaxDue" -> Seq("33.44")
  )

  private val jsonToPassToTemplate:Form[ChargeCDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeC.routes.ChargeDetailsController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  "ChargeDetails Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = httpPathGET,
      page = ChargeCDetailsPage,
      data = SampleData.chargeCDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers = Some(SampleData.userAnswersWithSchemeNameAndOrganisation)
    )

    behave like controllerWithPOSTWithJson(
      httpPath = httpPathPOST,
      page = ChargeCDetailsPage,
      expectedJson = Json.obj(
        "chargeCDetails" -> Json.obj(
          SponsoringOrganisationDetailsPage.toString -> SampleData.sponsoringOrganisationDetails,
          IsSponsoringEmployerIndividualPage.toString -> false,
          ChargeCDetailsPage.toString -> Json.toJson(SampleData.chargeCDetails)
        )
      ),
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid,
      userAnswers = Some(SampleData.userAnswersWithSchemeNameAndOrganisation))
  }
}