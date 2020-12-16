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

package connectors.admin

import com.google.inject.Inject
import config.FrontendAppConfig
import models.FeatureToggle
import play.api.Logger
import play.api.http.Status._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class FeatureToggleConnector @Inject()(http: HttpClient, config: FrontendAppConfig) extends HttpResponseHelper {
  def get(name: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[FeatureToggle] = {
    val endPoint = config.featureToggleUrl(name)
    http.GET[HttpResponse](endPoint) map {
      response =>
        response.status match {
        case OK => response.json.as[FeatureToggle]
        case _ => handleErrorResponse("GET", endPoint)(response)
      }
    } andThen {
      case Failure(t: Throwable) => Logger.warn("Unable to get toggle value", t)
    }
  }
}
