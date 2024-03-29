/*
 * Copyright 2024 HM Revenue & Customs
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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{GenericViewModel, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import pages.IsPublicServicePensionsRemedyPage
import pages.chargeE.WhatYouWillNeedPage
import play.api.Application
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers._
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class WhatYouWillNeedControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction).build()
  private val templateToBeRendered = "chargeE/whatYouWillNeed.njk"
  private val index = 0
  private val uaPsrTrue: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance,Some(0)), true)
  private val uaPsrFalse: UserAnswers = userAnswersWithSchemeNamePstrQuarter
    .setOrException(IsPublicServicePensionsRemedyPage(ChargeTypeAnnualAllowance,Some(0)), false)

  private def httpPathGET: String = controllers.chargeE.routes.WhatYouWillNeedController.onPageLoad(srn, startDate, accessType, versionInt, index).url

  private def jsonToPassToTemplate(optMessage: Option[String]) = Json.obj(
    "viewModel" -> GenericViewModel(
      submitUrl = dummyCall.url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      schemeName = schemeName),
    "psr" -> optMessage
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
  }

  "whatYouWillNeed Controller" must {
    "return OK and the correct view for a GET without extra paragraph for PSR" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrFalse))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(None))
    }
    "return OK and the correct view for a GET with extra paragraph for PSR" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(uaPsrTrue))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(ArgumentMatchers.eq(WhatYouWillNeedPage), any(), any(), any(), any(), any(), any())(any())).thenReturn(dummyCall)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate(Some("chargeE.whatYouWillNeed.li7")))
    }
  }
}
