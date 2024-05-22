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

package connectors.admin

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.EitherValues
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.running
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import utils.WireMockHelper
import scala.concurrent.ExecutionContext.Implicits.global
class FeatureToggleConnectorSpec extends AnyFreeSpec
  with Matchers
  with WireMockHelper
  with ScalaFutures
  with EitherValues
  with IntegrationPatience
  with MockitoSugar{

  implicit private lazy val hc: HeaderCarrier = HeaderCarrier()
  override protected def portConfigKey: String = "microservice.services.pensions-scheme.port"

  private def application: Application =
    new GuiceApplicationBuilder()
      .configure(
        "microservice.services.pensions-scheme.port" -> server.port
      )
      .build()

  private val happyJsonToggle1: String = {
    s"""
       |{"toggleName" : "interim-dashboard", "toggleDescription": "interim dashboard toggle", "isEnabled" : true }
     """.stripMargin
  }

  private val toggleDetails1 = ToggleDetails("interim-dashboard", Some("interim dashboard toggle"), isEnabled = true)
  private val getFeatureTogglePath = "/admin/get-toggle"

  "when calling the getFeatureToggle method" - {
    "and valid json is returned" - {
      "must return a SuccessResponse" in {
        val app = application
        running(app) {
          val connector = app.injector.instanceOf[FeatureToggleConnector]
          server.stubFor(
            get(urlEqualTo(getFeatureTogglePath + "/interim-dashboard")).willReturn(ok(happyJsonToggle1))
          )
          val result = connector.getNewPensionsSchemeFeatureToggle("interim-dashboard").futureValue
          result mustBe toggleDetails1
        }
      }
    }

    "and an error is returned" - {
      "must return a internal server error" in {
        val app = application
        running(app) {
          val connector = app.injector.instanceOf[FeatureToggleConnector]
          server.stubFor(
            get(urlEqualTo(getFeatureTogglePath + "/interim-dashboard")).willReturn(serverError())
          )
          recoverToExceptionIf[UpstreamErrorResponse](connector.getNewPensionsSchemeFeatureToggle("interim-dashboard")) map {
            _.reportAs mustBe INTERNAL_SERVER_ERROR
          }
        }
      }
    }
  }

}
