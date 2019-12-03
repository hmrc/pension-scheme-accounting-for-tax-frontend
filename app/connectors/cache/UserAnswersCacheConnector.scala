/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.ws.WSClient
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.crypto.PlainText
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}

import scala.concurrent.{ExecutionContext, Future}

class UserAnswersCacheConnectorImpl @Inject()(
                                               config: FrontendAppConfig,
                                               http: WSClient
                                             ) extends UserAnswersCacheConnector {

  override protected def url = s"${config.aftUrl}/pension-scheme-accounting-for-tax/journey-cache/aft"

  override def fetch(id: String)(implicit
                                 ec: ExecutionContext,
                                 hc: HeaderCarrier
  ): Future[Option[JsValue]] = {
    http.url(url)
      .withHttpHeaders(hc.headers: _*)
      .get()
      .flatMap {
        response =>
          response.status match {
            case NOT_FOUND =>
              Future.successful(None)
            case OK =>
              Future.successful(Some(Json.parse(response.body)))
            case _ =>
              Future.failed(new HttpException(response.body, response.status))
          }
      }
  }

  override def save(id: String, value: JsValue)(implicit
                                                ec: ExecutionContext,
                                                hc: HeaderCarrier
  ): Future[JsValue] = {
    http.url(url)
      .withHttpHeaders(hc.withExtraHeaders(("content-type", "application/json")).headers: _*)
      .post(PlainText(Json.stringify(value)).value).flatMap {
      response =>
        response.status match {
          case CREATED =>
            Future.successful(value)
          case _ =>
            Future.failed(new HttpException(response.body, response.status))
        }
    }
  }

  override def removeAll(id: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {
    http.url(url)
      .withHttpHeaders(hc.headers: _*)
      .delete().map(_ => Ok)
  }
}

trait UserAnswersCacheConnector {

  protected def url: String

  def fetch(cacheId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[JsValue]]

  def save(cacheId: String, value: JsValue)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue]

  def removeAll(cacheId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result]
}


