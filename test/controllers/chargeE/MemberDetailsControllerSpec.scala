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

package controllers.chargeE

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.MemberDetailsFormProvider
import matchers.JsonMatchers
import models.{GenericViewModel, MemberDetails, NormalMode, UserAnswers}
import pages.chargeE.MemberDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import play.api.test.Helpers._

class MemberDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  val templateToBeRendered = "memberDetails.njk"
  val formProvider = new MemberDetailsFormProvider()
  val form: Form[MemberDetails] = formProvider()

  lazy val httpPathGET: String =
    controllers.chargeE.routes.MemberDetailsController.onPageLoad(NormalMode, SampleData.srn, 0).url
  lazy val httpPathPOST: String =
    controllers.chargeE.routes.MemberDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url

  private val jsonToPassToTemplate: Form[MemberDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeE.routes.MemberDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName)
  )

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("first"),
    "lastName" -> Seq("last"),
    "nino" -> Seq("AB123456C")
  )

  private val expectedJson: JsObject = Json.obj(
    "pstr" -> "pstr",
    "chargeEDetails" -> Json.obj(
    "members" -> Json.arr(
      Json.obj(
        "memberDetails" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last",
          "nino" -> "AB123456C",
          "isDeleted" -> false
        )
      )
    )),
    "schemeName" -> "Big Scheme"
  )

  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("***"),
    "lastName" -> Seq("***"),
    "nino" -> Seq("***")
  )

  private val userAnswers: Option[UserAnswers] = Some(SampleData.userAnswersWithSchemeName)

  "MemberDetails Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = httpPathGET,
      page = MemberDetailsPage(0),
      data = SampleData.memberDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate,
      userAnswers
    )

    behave like controllerWithPOSTWithJson(
      httpPath = httpPathPOST,
      page = MemberDetailsPage(0),
      expectedJson = expectedJson,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid,
      userAnswers
    )
  }
}
