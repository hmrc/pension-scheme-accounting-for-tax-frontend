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

package controllers.chargeG

import behaviours.ControllerBehaviours
import controllers.base.ControllerSpecBase
import data.SampleData
import forms.chargeG.MemberDetailsFormProvider
import matchers.JsonMatchers
import models.chargeG.MemberDetails
import models.{GenericViewModel, NormalMode}
import pages.chargeG.MemberDetailsPage
import play.api.data.Form
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

class MemberDetailsControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with ControllerBehaviours {
  val templateToBeRendered = "chargeG/memberDetails.njk"
  val formProvider = new MemberDetailsFormProvider()
  val form: Form[MemberDetails] = formProvider()

  lazy val memberDetailsRouteGetRoute: String =
    controllers.chargeG.routes.MemberDetailsController.onPageLoad(NormalMode, SampleData.srn, 0).url
  lazy val memberDetailsRoutePostRoute: String =
    controllers.chargeG.routes.MemberDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url

  private val jsonToPassToTemplate: Form[MemberDetails]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.chargeG.routes.MemberDetailsController.onSubmit(NormalMode, SampleData.srn, 0).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "date" -> DateInput.localDate(form("dob"))
  )

  private val valuesValid: Map[String, Seq[String]] = Map(
    "firstName" -> Seq("first"),
    "lastName" -> Seq("last"),
    "dob.day" -> Seq("3"),
    "dob.month" -> Seq("4"),
    "dob.year" -> Seq("2019"),
    "nino" -> Seq("AB123456C")
  )

  private val expectedJson: JsObject = Json.obj(
    "pstr" -> "pstr",
    "chargeGDetails" -> Json.obj(
    "members" -> Json.arr(
      Json.obj(
        "memberDetails" -> Json.obj(
          "firstName" -> "first",
          "lastName" -> "last",
          "dob" -> "2019-04-03",
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
    "dob.day" -> Seq("3"),
    "dob.month" -> Seq("15"),
    "dob.year" -> Seq("2020"),
    "nino" -> Seq("***")
  )

  "MemberDetails Controller" must {
    behave like controllerWithGETSavedData(
      httpPath = memberDetailsRouteGetRoute,
      page = MemberDetailsPage(0),
      data = SampleData.memberGDetails,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOSTWithJson(
      httpPath = memberDetailsRoutePostRoute,
      page = MemberDetailsPage(0),
      expectedJson = expectedJson,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
