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

package connectors

import com.google.inject.Inject
import config.FrontendAppConfig
import models.UserAnswers
import play.api.http.Status
import play.api.libs.json.JsObject
import play.api.libs.json.JsValue
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AFTConnector @Inject()(http: HttpClient, config: FrontendAppConfig) {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val url = config.aftFileReturn
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr)
    http.POST[JsObject, HttpResponse](url, answers.data)(implicitly, implicitly, aftHc, implicitly).map(_ => ())
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext,
                                                                         hc: HeaderCarrier): Future[JsValue] = {
    val url = config.getAftDetails
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr, "startDate" -> startDate, "aftVersion" -> aftVersion)
    http.GET[JsValue](url)(implicitly, aftHc, implicitly)
  }

  def getListOfVersions(pstr: String, startDate: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Int]] = {
    val url = config.aftListOfVersions
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr, "startDate" -> startDate)
    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      require(response.status == Status.OK)
      response.json.as[Seq[Int]]
    }
  }
}
