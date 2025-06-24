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

package connectors.cache

import com.google.inject.Inject
import config.FrontendAppConfig
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.client.HttpClientV2
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

class FinancialInfoCacheConnector @Inject()(
                                               config: FrontendAppConfig,
                                               http: HttpClientV2
                                             ) extends CacheConnector {

  def url = url"${config.aftUrl}/pension-scheme-accounting-for-tax/cache/financialInfo"

  override def fetch
    (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[JsValue]] = {

    http.get(url).execute[HttpResponse]
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            Some(Json.parse(response.body))
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  def save(value: JsValue)
          (implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[JsValue] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"))

    http.post(url).withBody(value).setHeader(headers*).execute[HttpResponse]
      .map { response =>
        response.status match {
          case CREATED =>
            value
          case _ =>
            throw new HttpException(response.body, response.status)
        }
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}

trait CacheConnector {

  def fetch(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[JsValue]]

  def save(value: JsValue)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

}
