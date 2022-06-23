/*
 * Copyright 2022 HM Revenue & Customs
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
import models.fileUpload.FileUploadOutcome
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

class FileUploadEventsLogConnector @Inject()(config: FrontendAppConfig, http: HttpClient) {

  private def url = s"${config.fileUploadEventsLogStatusUrl}"

  def getOutcome(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[FileUploadOutcome]] = {

    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.GET[HttpResponse](url)(implicitly, hc, implicitly)
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status match {
          case OK =>
            response.json.asOpt[FileUploadOutcome]
          case _ => None
        }
      }
  }

  def setOutcome(outcome: FileUploadOutcome)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Int] = {
    val headers: Seq[(String, String)] = Seq(("Content-Type", "application/json"))
    val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    http.POST[JsValue, HttpResponse](url, Json.toJson(outcome))
      .recoverWith(mapExceptionsToStatus)
      .map { response =>
        response.status
      }
  }

  private def mapExceptionsToStatus: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
        Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}
