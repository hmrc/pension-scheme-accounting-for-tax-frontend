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

import behaviours.CheckYourAnswersBehaviour
import controllers.base.ControllerSpecBase
import data.SampleData
import matchers.JsonMatchers
import models.UserAnswers
import org.mockito.{ArgumentCaptor, Matchers}
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import pages.chargeC._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper
import play.api.test.Helpers._

class CheckYourAnswersControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers with CheckYourAnswersBehaviour {

  private val templateToBeRendered = "check-your-answers.njk"

  private def httpPathGET: String = controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(SampleData.srn).url
  private def httpOnClickRoute: String = controllers.chargeC.routes.CheckYourAnswersController.onClick(SampleData.srn).url

  private def ua = SampleData.userAnswersWithSchemeName
    .set(ChargeCDetailsPage, SampleData.chargeCDetails).toOption.get
    .set(IsSponsoringEmployerIndividualPage, true).toOption.get
    .set(SponsoringIndividualDetailsPage, SampleData.sponsoringIndividualDetails).toOption.get
    .set(SponsoringEmployerAddressPage, SampleData.sponsoringEmployerAddress).toOption.get

  private val helper = new CheckYourAnswersHelper(ua, SampleData.srn)

  private val jsonToPassToTemplate: JsObject = Json.obj(
    "list" -> helper.chargeCDetails
  )

  private val userAnswers: Option[UserAnswers] = Some(ua)

  "CheckYourAnswers Controller" must {
    "return OK and the correct view for a GET" in {
      val application = applicationBuilder(userAnswers = userAnswers).build()
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockCompoundNavigator.nextPage(Matchers.eq(CheckYourAnswersPage), any(), any(), any())).thenReturn(SampleData.dummyCall)

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplate)

      application.stop()
    }

    behave like controllerWithOnClick(
      httpPath = httpOnClickRoute,
      page = CheckYourAnswersPage
    )
  }
}
