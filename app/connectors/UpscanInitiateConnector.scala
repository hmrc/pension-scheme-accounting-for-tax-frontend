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

package connectors

import config.FrontendAppConfig
import models.{UpscanFileReference, UpscanInitiateResponse}
import play.api.libs.json.{Json, OFormat, Reads, Writes}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

sealed trait UpscanInitiateRequest

// TODO expectedContentType is also an optional value
case class UpscanInitiateRequestV2(
  callbackUrl: String,
  successRedirect: Option[String] = None,
  errorRedirect: Option[String]   = None,
  minimumFileSize: Option[Int]    = None,
  maximumFileSize: Option[Int]    = Some(512),
  expectedContentType: Option[String] = None)
    extends UpscanInitiateRequest

case class UploadForm(href: String, fields: Map[String, String])

case class Reference(reference: String) extends AnyVal

object Reference {
  implicit val referenceReader: Reads[Reference] = Reads.StringReads.map(Reference(_))
  implicit val referenceWrites = Json.writes[Reference]
}

case class PreparedUpload(reference: Reference, uploadRequest: UploadForm)

object UpscanInitiateRequestV2 {
  implicit val format: OFormat[UpscanInitiateRequestV2] = Json.format[UpscanInitiateRequestV2]
}

object PreparedUpload {

  implicit val uploadFormFormat: Reads[UploadForm] = Json.reads[UploadForm]

  implicit val format: Reads[PreparedUpload] = Json.reads[PreparedUpload]
}

class UpscanInitiateConnector @Inject()(httpClient: HttpClient, appConfig: FrontendAppConfig)(implicit ec: ExecutionContext) {

  private val headers = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  def initiateV2(redirectOnSuccess: Option[String], redirectOnError: Option[String])
                (implicit hc: HeaderCarrier): Future[UpscanInitiateResponse] = {
    val request = UpscanInitiateRequestV2(
      callbackUrl = appConfig.urlInThisService(controllers.fileUpload.routes.UploadCallbackController.callback.url),
      successRedirect = redirectOnSuccess,
      errorRedirect = redirectOnError,
      maximumFileSize = Some(appConfig.maxUploadFileSize  * (1024 * 1024))
    )
    initiate(appConfig.initiateV2Url, request)
  }

  private def initiate[T](url: String, request: T)(
    implicit hc: HeaderCarrier,
    wts: Writes[T]): Future[UpscanInitiateResponse] =
    for {
      response <- httpClient.POST[T, PreparedUpload](url, request, headers.toSeq)
      fileReference = UpscanFileReference(response.reference.reference)
      postTarget    = response.uploadRequest.href
      formFields    = response.uploadRequest.fields
    } yield UpscanInitiateResponse(fileReference, postTarget, formFields)

  def download(downloadUrl: String)(implicit hc: HeaderCarrier) = {
    httpClient.GET(s"${appConfig.upscanUrl}$downloadUrl")
  }
}