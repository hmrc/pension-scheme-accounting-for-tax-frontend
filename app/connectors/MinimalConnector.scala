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
import models.requests.IdentifierRequest
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import utils.HttpResponseHelper
import play.api.http.Status._

import scala.concurrent.{Future, ExecutionContext}

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
        case (Some(psa), _) => hc.withExtraHeaders("psaId" -> psa.id)
        case (_, Some(psp)) => hc.withExtraHeaders("pspId" -> psp.id)
        case _ => throw new Exception("Could not retrieve ID from request")
      }

    val url = config.minimalPsaDetailsUrl

    http.GET[HttpResponse](url)(implicitly, hcWithId, implicitly) map {
      response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[MinimalDetails] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url)(response)
        }
    }
  }
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
