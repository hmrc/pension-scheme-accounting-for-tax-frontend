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

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.Enumerable
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.AFTPartialService
import play.api.inject.bind
import services.AFTPartialServiceSpec.allTypesMultipleReturnsModel
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PartialControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.routes.PartialController.aftPartial(srn).url


  val mockAftPartialService: AFTPartialService = mock[AFTPartialService]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[AFTPartialService].toInstance(mockAftPartialService)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()



  private val templateToBeRendered = "partials/overview.njk"
  private val jsonToPassToTemplate: JsObject = Json.obj("aftModels" -> Json.toJson(allTypesMultipleReturnsModel))

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAftPartialService, mockRenderer)
    when(mockAftPartialService.retrieveOptionAFTViewModel(any(), any())(any(), any()))
      .thenReturn(Future.successful(allTypesMultipleReturnsModel))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

  }

  "Partial Controller" when {
    "on a GET" must {

      "return the html with information received from overview api" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        when(mockAftPartialService.retrieveOptionAFTViewModel(any(), any())(any(), any()))
          .thenReturn(Future.successful(allTypesMultipleReturnsModel))
        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(jsonToPassToTemplate)
      }

    }

  }
}
