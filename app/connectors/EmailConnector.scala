/*
 * Copyright 2025 HM Revenue & Customs
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
import models.{AdministratorOrPractitioner, JourneyType, SendEmailRequest}
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.crypto.{ApplicationCrypto, PlainText}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import scala.concurrent.{ExecutionContext, Future}

sealed trait EmailStatus
case object EmailSent extends EmailStatus
case object EmailNotSent extends EmailStatus

class EmailConnector @Inject()(
                                appConfig: FrontendAppConfig,
                                http: HttpClientV2,
                                crypto: ApplicationCrypto
                              ) {
  private val logger = Logger(classOf[EmailConnector])

  private def callBackUrl(
                           schemeAdministratorType: AdministratorOrPractitioner,
                           requestId: String,
                           journeyType: JourneyType.Value,
                           psaOrPspId: String,
                           email: String
                         ): String = {
    val encryptedPsaOrPspId = crypto.QueryParameterCrypto.encrypt(PlainText(psaOrPspId)).value
    val encryptedEmail = crypto.QueryParameterCrypto.encrypt(PlainText(email)).value
    appConfig.aftEmailCallback(schemeAdministratorType, journeyType, requestId, encryptedEmail, encryptedPsaOrPspId)
  }

  private def createSendEmailRequest(
                                      emailAddress: String,
                                      schemeAdministratorType: AdministratorOrPractitioner,
                                      requestId: String,
                                      psaOrPspId: String,
                                      journeyType: JourneyType.Value,
                                      templateName: String,
                                      templateParams: Map[String, String]
                                    ): SendEmailRequest = {
    SendEmailRequest(
      List(emailAddress),
      templateName,
      templateParams,
      appConfig.emailSendForce,
      callBackUrl(schemeAdministratorType, requestId, journeyType, psaOrPspId, emailAddress)
    )
  }

  def sendEmail(
                 schemeAdministratorType: AdministratorOrPractitioner,
                 requestId: String,
                 psaOrPspId: String,
                 journeyType: JourneyType.Value,
                 emailAddress: String,
                 templateName: String,
                 templateParams: Map[String, String]
               )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Future[EmailStatus] = {
    val emailApiUrl = url"${appConfig.emailApiUrl}/hmrc/email"
    val sendEmailReq = createSendEmailRequest(
      emailAddress,
      schemeAdministratorType,
      requestId,
      psaOrPspId,
      journeyType,
      templateName,
      templateParams
    )

    http.post(emailApiUrl).withBody(Json.toJson(sendEmailReq)).execute[HttpResponse].map { response =>
      response.status match {
        case ACCEPTED =>
          logger.info(
            s"[EmailConnector] Email sent successfully for journeyType='$journeyType', " +
              s"requestId='$requestId', psaOrPspId='$psaOrPspId'."
          )
          EmailSent
        case otherStatus =>
          logger.warn(
            s"[EmailConnector] Email sending failed for journeyType='$journeyType', " +
              s"requestId='$requestId', psaOrPspId='$psaOrPspId'. " +
              s"Response status: $otherStatus"
          )
          EmailNotSent
      }
    }.recoverWith {
      case throwable: Throwable =>
        logger.error(
          s"[EmailConnector] Failed to send email due to an exception for journeyType='$journeyType', " +
            s"requestId='$requestId', psaOrPspId='$psaOrPspId'. Exception: ${throwable.getMessage}",
          throwable
        )
        Future.successful(EmailNotSent)
    }
  }
}