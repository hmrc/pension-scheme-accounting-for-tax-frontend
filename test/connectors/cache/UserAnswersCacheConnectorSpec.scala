/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors.cache

import com.github.tomakehurst.wiremock.client.WireMock._
import data.SampleData
import models.{SessionAccessData, LockDetail, AccessMode}
import org.scalatest._
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Results._
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import utils.WireMockHelper

class UserAnswersCacheConnectorSpec extends AsyncWordSpec with MustMatchers with WireMockHelper with OptionValues with RecoverMethods {

  private implicit lazy val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.pension-scheme-accounting-for-tax.port"

  private lazy val connector: UserAnswersCacheConnector = injector.instanceOf[UserAnswersCacheConnector]
  private val aftReturnUrl = "/pension-scheme-accounting-for-tax/journey-cache/aft"
  private val sessionUrl = "/pension-scheme-accounting-for-tax/journey-cache/aft/session-data"

  private val isLockedUrl = s"/pension-scheme-accounting-for-tax/journey-cache/aft/lock"

  private val sessionAccessData: SessionAccessData =
    SessionAccessData(1, AccessMode.PageAccessModeCompile, areSubmittedVersionsAvailable = false)

  ".fetch" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(aftReturnUrl))
          .willReturn(
            notFound
          )
      )

      connector.fetch(cacheId = "testId") map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      server.stubFor(
        get(urlEqualTo(aftReturnUrl))
          .willReturn(
            ok(Json.obj(fields = "testId" -> "data").toString())
          )
      )

      connector.fetch(cacheId = "testId") map {
        result =>
          result.value mustEqual Json.obj(fields = "testId" -> "data")
      }
    }

    "return a failed future on upstream error" in {
      server.stubFor(
        get(urlEqualTo(aftReturnUrl))
          .willReturn(
            serverError
          )
      )

      recoverToExceptionIf[Upstream5xxResponse] {
        connector.fetch(cacheId = "testId")
      } map {
        _.upstreamResponseCode mustEqual Status.INTERNAL_SERVER_ERROR
      }
    }
  }




  ".getSessionData" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(sessionUrl))
          .willReturn(
            notFound
          )
      )

      connector.getSessionData(id = "testId") map {
        result =>
          result mustNot be(defined)
      }
    }

    "return data if data is present in the collection" in {
      val sd = SampleData.sessionData()
      val responseBodyFromDES = Json.toJson(sd)

      server.stubFor(
        get(urlEqualTo(sessionUrl))
          .willReturn(
            ok(responseBodyFromDES.toString())
          )
      )

      connector.getSessionData(id = "testId") map {
        result =>
          result.value mustEqual sd
      }
    }

    "return a failed future on upstream error" in {
      server.stubFor(
        get(urlEqualTo(sessionUrl))
          .willReturn(
            serverError
          )
      )

      recoverToExceptionIf[Upstream5xxResponse] {
        connector.getSessionData(id = "testId")
      } map {
        _.upstreamResponseCode mustEqual Status.INTERNAL_SERVER_ERROR
      }
    }
  }



  ".save" must {
    val json = Json.obj(
      fields = "testId" -> "foobar"
    )
    "save the data in the collection" in {
      server.stubFor(
        post(urlEqualTo(aftReturnUrl))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(201)
          )
      )

      connector.save(cacheId = "testId", json) map {
        _ mustEqual json
      }
    }

    "return a failed future on upstream error" in {

      server.stubFor(
        post(urlEqualTo(aftReturnUrl))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            serverError()
          )
      )
      recoverToExceptionIf[Upstream5xxResponse] {
        connector.save(cacheId = "testId", json)
      } map {
        _.upstreamResponseCode mustEqual Status.INTERNAL_SERVER_ERROR
      }
    }
  }

  ".save" must {
    val lockUrl = s"/pension-scheme-accounting-for-tax/journey-cache/aft/session-data-lock"
    val json = Json.obj(
      fields = "testId" -> "lock"
    )
    "set lock in the collection" in {
      server.stubFor(
        post(urlEqualTo(lockUrl))
          .withRequestBody(equalTo(Json.stringify(json)))
          .willReturn(
            aResponse.withStatus(201)
          )
      )

      connector.saveAndLock("testId", json, sessionAccessData, lockReturn = true) map {
        _ mustEqual json
      }
    }
  }

  ".removeAll" must {

    "return OK after removing all the data from the collection" in {
      server.stubFor(delete(urlEqualTo(aftReturnUrl)).
        willReturn(ok)
      )
      connector.removeAll(cacheId = "testId").map {
        _ mustEqual Ok
      }
    }
  }

  ".lockDetail" must {

    "return `None` when there is no data in the collection" in {
      server.stubFor(
        get(urlEqualTo(isLockedUrl))
          .willReturn(
            notFound
          )
      )

      connector.lockDetail(srn = "srn", startDate = "2020-04-01") map {
        result =>
          result mustBe None
      }
    }

    "return some value if status is OK and data is present in the collection" in {
      val expectedLockDetail = LockDetail("joe", SampleData.psaId)
      val lockDetailJson = Json.toJson(expectedLockDetail).toString

      server.stubFor(
        get(urlEqualTo(isLockedUrl))
          .willReturn(
            ok(lockDetailJson)
          )
      )

      connector.lockDetail(srn = "srn", startDate = "2020-04-01") map {
        result =>
          result mustBe Some(expectedLockDetail)
      }
    }

    "return a failed future on upstream error" in {
      server.stubFor(
        get(urlEqualTo(isLockedUrl))
          .willReturn(
            serverError
          )
      )

      recoverToExceptionIf[Upstream5xxResponse] {
        connector.lockDetail(srn = "srn", startDate = "2020-04-01")
      } map {
        _.upstreamResponseCode mustEqual Status.INTERNAL_SERVER_ERROR
      }
    }
  }
}
