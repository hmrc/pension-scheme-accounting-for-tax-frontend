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

package controllers

import behaviours.ControllerBehaviours
import connectors.AFTConnector
import data.SampleData
import forms.AFTSummaryFormProvider
import models.{Enumerable, GenericViewModel, NormalMode, UserAnswers}
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import pages.{AFTSummaryPage, PSTRQuery, SchemeNameQuery}
import play.api.data.Form
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.libs.json.{JsObject, Json}
import services.SchemeService
import uk.gov.hmrc.viewmodels.Radios
import utils.AFTSummaryHelper

import scala.concurrent.Future

class AFTSummaryControllerSpec extends ControllerBehaviours with BeforeAndAfterEach with Enumerable.Implicits{

  private val mockSchemeService = mock[SchemeService]

  private val mockAftConnector: AFTConnector = mock[AFTConnector]

  override protected def applicationBuilder(userAnswers: Option[UserAnswers] = None): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules(userAnswers) ++ Seq[GuiceableModule](
          bind[SchemeService].toInstance(mockSchemeService),
          bind[AFTConnector].toInstance(mockAftConnector)
        ): _*
      )

  private val templateToBeRendered = "aftSummary.njk"
  private val form = new AFTSummaryFormProvider()()

  private def aftSummaryGetRoute: String = controllers.routes.AFTSummaryController.onPageLoad(NormalMode, SampleData.srn).url

  private def aftSummaryPostRoute: String = controllers.routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn).url

  private val valuesValid: Map[String, Seq[String]] = Map("value" -> Seq("true"))

  private val valuesInvalid: Map[String, Seq[String]] = Map("value" -> Seq("xyz"))

  private val summaryHelper = new AFTSummaryHelper

  private val schemeName = "scheme"
  private val schemePSTR = "pstr"

  private val uaGetAFTDetails = UserAnswers()
  private val uaGetAFTDetailsPlusSchemeDetails = uaGetAFTDetails
    .set(SchemeNameQuery, schemeName).toOption.getOrElse(uaGetAFTDetails)
    .set(PSTRQuery, schemePSTR).toOption.getOrElse(uaGetAFTDetails)

  override def beforeEach: Unit = {
    Mockito.reset(mockSchemeService, mockAftConnector)
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockAftConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(uaGetAFTDetails.data))
    super.beforeEach()
  }


  private val jsonToPassToTemplate: Form[Boolean] => JsObject = form => Json.obj(
    "form" -> form,
    "list" -> summaryHelper.summaryListData(UserAnswers(), SampleData.srn),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(NormalMode, SampleData.srn).url,
      returnUrl = frontendAppConfig.managePensionsSchemeSummaryUrl.format(SampleData.srn),
      schemeName = SampleData.schemeName),
    "radios" -> Radios.yesNo(form("value"))
  )

  "AFTSummary Controller" must {

    behave like controllerWithGETNeverFilledFormNoSessionExpiredTest(
      httpPath = aftSummaryGetRoute,
      form = form,
      templateToBeRendered = templateToBeRendered,
      jsonToPassToTemplate = jsonToPassToTemplate
    )

    behave like controllerWithPOST(
      httpPath = aftSummaryPostRoute,
      page = AFTSummaryPage,
      data = true,
      form = form,
      templateToBeRendered = templateToBeRendered,
      requestValuesValid = valuesValid,
      requestValuesInvalid = valuesInvalid
    )
  }
}
