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

package upscan.callback

import connectors.Reference
import play.api.libs.json._
import play.api.mvc._
import services.fileUpload.UpscanCallbackDispatcher
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import utils.HttpUrlFormat

import java.net.URL
import java.time.Instant
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

sealed trait CallbackBody {
  def reference : Reference
}

case class ReadyCallbackBody(
                              reference: Reference,
                              downloadUrl: URL,
                              uploadDetails: UploadDetails
                            ) extends CallbackBody

case class FailedCallbackBody(
                               reference: Reference,
                               failureDetails: ErrorDetails
                             ) extends CallbackBody

object CallbackBody {
  // must be in scope to create Reads for ReadyCallbackBody
  private implicit val urlFormat: Format[URL] = HttpUrlFormat.format

  implicit val uploadDetailsReads: Reads[UploadDetails] = Json.reads[UploadDetails]

  implicit val errorDetailsReads: Reads[ErrorDetails] = Json.reads[ErrorDetails]

  implicit val readyCallbackBodyReads: Reads[ReadyCallbackBody] = Json.reads[ReadyCallbackBody]

  implicit val failedCallbackBodyReads: Reads[FailedCallbackBody] = Json.reads[FailedCallbackBody]

  implicit val reads: Reads[CallbackBody] = (json: JsValue) => json \ "fileStatus" match {
    case JsDefined(JsString("READY")) => implicitly[Reads[ReadyCallbackBody]].reads(json)
    case JsDefined(JsString("FAILED")) => implicitly[Reads[FailedCallbackBody]].reads(json)
    case JsDefined(value) => JsError(s"Invalid type distriminator: $value")
    case JsUndefined() => JsError(s"Missing type distriminator")
    case _ => JsError(s"Missing type distriminator")
  }
}

case class UploadDetails(uploadTimestamp: Instant,
  checksum: String,
  fileMimeType: String,
  fileName: String,
  size: Long)

case class ErrorDetails(failureReason: String, message: String)

@Singleton
class UploadCallbackController @Inject()(
  upscanCallbackDispatcher : UpscanCallbackDispatcher,
  mcc: MessagesControllerComponents)
 (implicit ec : ExecutionContext) extends FrontendController(mcc) {

  def callback: Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[CallbackBody] { feedback: CallbackBody =>
      upscanCallbackDispatcher.handleCallback(feedback).map(_ => Ok)
    }
  }
}