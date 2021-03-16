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

import com.google.inject.Inject
import config.FrontendAppConfig
import play.api.libs.json._
import uk.gov.hmrc.http._
import play.api.http.Status._
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}

class SessionDataCacheConnector  @Inject()(
  config: FrontendAppConfig,
  http: WSClient
) {
  private def url(cacheId:String) = s"${config.pensionsAdministratorUrl}/pension-administrator/journey-cache/session-data/$cacheId"

  def fetch(id: String)(implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Option[JsValue]] = {
    http
      .url(url(id))
      .withHttpHeaders(hc.headers: _*)
      .get()
      .flatMap { response =>
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

  def removeAll(id: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    http
      .url(url(id))
      .withHttpHeaders(hc.headers: _*)
      .delete()
      .map(_ => ())
  }
}
