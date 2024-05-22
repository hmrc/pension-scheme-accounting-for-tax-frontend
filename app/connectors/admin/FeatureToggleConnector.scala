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

import com.google.inject.Inject
import config.FrontendAppConfig
import models.FeatureToggle
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpResponse}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure

class FeatureToggleConnector @Inject()(http: HttpClient, config: FrontendAppConfig) (implicit ec: ExecutionContext)
  extends HttpResponseHelper {
  private val logger = Logger(classOf[FeatureToggleConnector])

  def get(name: String)
         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[FeatureToggle] = {
    val endPoint = config.featureToggleUrl(name)
    http.GET[HttpResponse](endPoint) map {
      response =>
        response.status match {
          case OK => response.json.as[FeatureToggle]
          case _ => handleErrorResponse("GET", endPoint)(response)
        }
    } andThen {
      case Failure(t: Throwable) => logger.warn("Unable to get toggle value", t)
    }
  }

  def getNewPensionsSchemeFeatureToggle(toggleName: String)(implicit hc: HeaderCarrier): Future[ToggleDetails] = {
    getNewFeatureToggle(config.pensionSchemeNewToggleUrl(toggleName), toggleName)
  }

  private def getNewFeatureToggle(configURL: String, toggleName: String)(implicit hc: HeaderCarrier): Future[ToggleDetails] = {
    http.GET[HttpResponse](configURL)(implicitly, hc, implicitly).map { response =>
      val toggleOpt = response.status match {
        case NO_CONTENT => None
        case OK =>
          Some(response.json.as[ToggleDetails])
        case _ =>
          throw new HttpException(response.body, response.status)
      }

      toggleOpt match {
        case None => ToggleDetails(toggleName, None, isEnabled = false)
        case Some(a) => a
      }
    }
  }
}

case class ToggleDetails(toggleName: String, toggleDescription: Option[String], isEnabled: Boolean)

object ToggleDetails {
  implicit val format: OFormat[ToggleDetails] = Json.format[ToggleDetails]
}
