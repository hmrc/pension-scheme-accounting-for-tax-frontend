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
import models.SendEmailRequest
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

sealed trait EmailStatus

case object EmailSent extends EmailStatus

case object EmailNotSent extends EmailStatus

class EmailConnector @Inject()(
    appConfig: FrontendAppConfig,
    http: HttpClient
) {
  def callbackUrl: String = s"${appConfig.aftUrl}/pension-scheme-accounting-for-tax/email-response"

  def sendEmail(
      journeyType: String,
      emailAddress: String,
      templateName: String,
      templateParams: Map[String, String]
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[EmailStatus] = {
    val emailServiceUrl = s"${appConfig.emailApiUrl}/hmrc/email"

    val sendEmailReq = SendEmailRequest(List(emailAddress), templateName, templateParams, appConfig.emailSendForce, callbackUrl)

    val jsonData = Json.toJson(sendEmailReq)

    http.POST(emailServiceUrl, jsonData).map { response =>
      response.status match {
        case ACCEPTED =>
          Logger.debug("Email sent successfully for AFT Submission")
          EmailSent
        case status =>
          Logger.warn(s"Sending Email failed for AFT Submission with response status $status")
          EmailNotSent
      }
    } recoverWith logExceptions
  }

  private def logExceptions: PartialFunction[Throwable, Future[EmailStatus]] = {
    case t: Throwable =>
      Logger.warn("Unable to connect to Email Service", t)
      Future.successful(EmailNotSent)
  }
}
