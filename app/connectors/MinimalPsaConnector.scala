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
import play.api.libs.json._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.http.HttpReads.Implicits._
import utils.HttpResponseHelper
import play.api.http.Status._
import scala.concurrent.{ExecutionContext, Future}

class MinimalPsaConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {

  import MinimalPsaConnector._

  def getMinimalPsaDetails(psaId: String)
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[MinimalPSA] = {

    val psaHc = hc.withExtraHeaders("psaId" -> psaId)

    val url = config.minimalPsaDetailsUrl

    http.GET[HttpResponse](url)(implicitly, psaHc, implicitly) map {
      response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[MinimalPSA] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url)(response)
        }
    }
  }
}

object MinimalPsaConnector {

  case class MinimalPSA(email: String,
                        isPsaSuspended: Boolean,
                        organisationName: Option[String],
                        individualDetails: Option[IndividualDetails]) {

    def name: String = {
      individualDetails
        .map(_.fullName)
        .orElse(organisationName)
        .getOrElse("Pension Scheme Administrator")
    }
  }

  object MinimalPSA {
    implicit val format: Format[MinimalPSA] = Json.format[MinimalPSA]
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
