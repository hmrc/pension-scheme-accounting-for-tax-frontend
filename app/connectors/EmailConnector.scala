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

package connectors

import com.google.inject.Inject
import config.FrontendAppConfig
import models.{SendEmailRequest, SchemeAdministratorType}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{Json, JsValue}
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpResponse, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{Future, ExecutionContext}

sealed trait EmailStatus

case object EmailSent extends EmailStatus

case object EmailNotSent extends EmailStatus

class EmailConnector @Inject()(
                                appConfig: FrontendAppConfig,
                                http: HttpClient,
                                crypto: ApplicationCrypto
                              ) {
  private def callBackUrl(schemeAdministratorType: SchemeAdministratorType,
    requestId: String, journeyType: String, psaOrPspId: String, email: String): String = {
    val encryptedPsaOrPspId = crypto.QueryParameterCrypto.encrypt(PlainText(psaOrPspId)).value
    val encryptedEmail = crypto.QueryParameterCrypto.encrypt(PlainText(email)).value

    appConfig.aftEmailCallback(schemeAdministratorType, journeyType, requestId, encryptedEmail, encryptedPsaOrPspId)
  }

  //scalastyle:off parameter.number
  def sendEmail(
                 schemeAdministratorType: SchemeAdministratorType,
                 requestId: String,
                 psaOrPspId: String,
                 journeyType: String,
                 emailAddress: String,
                 templateName: String,
                 templateParams: Map[String, String]
               )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[EmailStatus] = {
    val emailServiceUrl = s"${appConfig.emailApiUrl}/hmrc/email"

    val sendEmailReq = SendEmailRequest(List(emailAddress), templateName, templateParams, appConfig.emailSendForce,
      callBackUrl(schemeAdministratorType, requestId, journeyType, psaOrPspId, emailAddress))

    val jsonData = Json.toJson(sendEmailReq)

    http.POST[JsValue, HttpResponse](emailServiceUrl, jsonData).map { response =>
      response.status match {
        case ACCEPTED =>
          Logger.debug(s"Email sent successfully for $journeyType")
          EmailSent
        case status =>
          Logger.warn(s"Sending Email failed for $journeyType with response status $status")
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
