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

package connectors

import com.google.inject.Inject
import config.FrontendAppConfig
import models.requests.IdentifierRequest
import play.api.http.Status._
import play.api.libs.json._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, _}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class MinimalConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {

  import MinimalConnector._

  def getMinimalDetails[A](
                            implicit hc: HeaderCarrier,
                            ec: ExecutionContext,
                            request: IdentifierRequest[A]
                          ): Future[MinimalDetails] = {

    val hcWithId: HeaderCarrier =
      (request.psaId, request.pspId) match {
        case (Some(_), _) => hc.withExtraHeaders("loggedInAsPsa" -> "true")
        case (_, Some(_)) => hc.withExtraHeaders("loggedInAsPsa" -> "false")
        case _ => throw new Exception("Could not retrieve ID from request")
      }

    minDetails(hcWithId)
  }

  def getMinimalPsaDetails()
                          (implicit hc: HeaderCarrier,
                           ec: ExecutionContext
                          ): Future[MinimalDetails] = {

    val hcWithId: HeaderCarrier = hc.withExtraHeaders("loggedInAsPsa" -> "true")
    minDetails(hcWithId)
  }

  def getPsaOrPspName[A](implicit hc: HeaderCarrier, ec: ExecutionContext, request: IdentifierRequest[A]): Future[String] =
    getMinimalDetails.map(_.name)

  private def minDetails(hcWithId: HeaderCarrier)
                        (implicit ec: ExecutionContext): Future[MinimalDetails] = {

    val url = config.minimalPsaDetailsUrl

    http.GET[HttpResponse](url)(implicitly, hcWithId, implicitly) map {
      response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[MinimalDetails] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case FORBIDDEN if response.body.contains(delimitedErrorMsg) => throw new DelimitedAdminException
          case _ =>
            handleErrorResponse("GET", url)(response)
        }
    }
  }

  val delimitedErrorMsg: String = "DELIMITED_PSAID"
}

object MinimalConnector {

  case class MinimalDetails(
                             email: String,
                             isPsaSuspended: Boolean,
                             organisationName: Option[String],
                             individualDetails: Option[IndividualDetails],
                             rlsFlag: Boolean,
                             deceasedFlag: Boolean
                           ) {

    def name: String = {
      individualDetails
        .map(_.fullName)
        .orElse(organisationName)
        .getOrElse("Pension Scheme Administrator")
    }
  }

  object MinimalDetails {
    implicit val format: Format[MinimalDetails] = Json.format[MinimalDetails]
  }

  case class IndividualDetails(firstName: String,
                               middleName: Option[String],
                               lastName: String) {

    def fullName: String = middleName match {
      case Some(middle) => s"$firstName $middle $lastName"
      case _ => s"$firstName $lastName"
    }
  }

  object IndividualDetails {
    implicit val format: Format[IndividualDetails] = Json.format[IndividualDetails]
  }

}

class DelimitedAdminException extends
  Exception("The administrator has already de-registered. The minimal details API has returned a DELIMITED PSA response")