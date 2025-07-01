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
import models.fileUpload.FileUploadOutcome
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, NotFoundException, StringContextOps}
import uk.gov.hmrc.http.HttpReads.Implicits._
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

class FileUploadOutcomeConnector @Inject()(config: FrontendAppConfig, http: HttpClientV2) {

  private val logger = Logger(classOf[FileUploadOutcomeConnector])
  private lazy val url = url"${config.fileUploadOutcomeUrl}"
  private val JsonHeaders: Seq[(String, String)] = Seq(("Content-Type", "application/json"))

  def getOutcome(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Option[FileUploadOutcome]] = {
    http.get(url).setHeader(JsonHeaders*).execute[HttpResponse]
      .recoverWith(handleNotFoundException)
      .map { response =>
        if (response.status == OK) response.json.asOpt[FileUploadOutcome] else None
      }
  }

  def setOutcome(outcome: FileUploadOutcome)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    http.post(url).withBody(Json.toJson(outcome)).setHeader(JsonHeaders*).execute[HttpResponse]
      .recoverWith(logFailure("Unable to post file upload outcome"))
      .map(_ => ())
  }

  def deleteOutcome(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Unit] = {
    http.delete(url).setHeader(JsonHeaders*).execute[HttpResponse]
      .recoverWith(logFailure("Unable to delete file upload outcome"))
      .map(_ => ())
  }

  private def logFailure(message: String): PartialFunction[Throwable, Future[HttpResponse]] = {
    case t: Throwable =>
      logger.warn(message, t)
      throw t
  }

  private def handleNotFoundException: PartialFunction[Throwable, Future[HttpResponse]] = {
    case _: NotFoundException =>
      Future.successful(HttpResponse(NOT_FOUND, "Not found"))
  }
}
