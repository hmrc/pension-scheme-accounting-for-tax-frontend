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
import models.SchemeDetails
import play.api.http.Status._
import play.api.libs.json.{JsError, JsResultException, JsSuccess, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class SchemeDetailsConnector @Inject()(httpClientV2: HttpClientV2, config: FrontendAppConfig)
  extends HttpResponseHelper {

  def getSchemeDetails(psaId: String, idNumber: String, schemeIdType: String)
                      (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[SchemeDetails] = {

    val url = url"${config.schemeDetailsUrl.format(idNumber)}"

    val headers: Seq[(String, String)] =
      Seq(
        ("idNumber", idNumber),
        ("schemeIdType", schemeIdType),
        ("psaId", psaId)
      )

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    httpClientV2
      .get(url)(hc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map {
      response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[SchemeDetails](SchemeDetails.readsPsa) match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url.toString)(response)
        }
    }
  }

  def getPspSchemeDetails(pspId: String, srn: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SchemeDetails] = {

    val url = url"${config.pspSchemeDetailsUrl.format(srn)}"
    val headers: Seq[(String, String)] = Seq("srn" -> srn, "pspId" -> pspId)
    val schemeHc = hc.withExtraHeaders(headers:_*)

    httpClientV2
      .get(url)(schemeHc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[SchemeDetails](SchemeDetails.readsPsp) match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url.toString)(response)
        }
    }
  }

  def checkForAssociation(
                           psaId: String,
                           srn: String,
                           idType: String
                         )(
                           implicit headerCarrier: HeaderCarrier,
                           ec: ExecutionContext
                         ): Future[Boolean] = {

    val url = url"${config.checkAssociationUrl}"

    val headers: Seq[(String, String)] =
      Seq((idType, psaId), ("schemeReferenceNumber", srn), ("Content-Type", "application/json"))

    implicit val hc: HeaderCarrier = headerCarrier.withExtraHeaders(headers: _*)

    httpClientV2
      .get(url)(hc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map {
      response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[Boolean] match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url.toString)(response)
        }
    }
  }
}

