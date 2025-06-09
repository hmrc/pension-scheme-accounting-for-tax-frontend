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
import models.{SchemeDetails, SchemeReferenceNumber}
import play.api.http.Status._
import play.api.libs.json.{JsError, JsResultException, JsSuccess, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http._
import utils.HttpResponseHelper

import java.net.{URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

class SchemeDetailsConnector @Inject()(httpClientV2: HttpClientV2, config: FrontendAppConfig)
  extends HttpResponseHelper {

  private def buildHeaders(headerCarrier: HeaderCarrier, headers: Seq[(String, String)]): HeaderCarrier =
    headerCarrier.withExtraHeaders(headers: _*)

  private def fetchData[T](url: URL, headers: Seq[(String, String)], reads: play.api.libs.json.Reads[T])
                          (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[T] = {
    val updatedHc = buildHeaders(hc, headers)

    httpClientV2
      .get(url)(updatedHc)
      .setHeader(headers: _*)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
        response.status match {
          case OK =>
            Json.parse(response.body).validate[T](reads) match {
              case JsSuccess(value, _) => value
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ =>
            handleErrorResponse("GET", url.toString)(response)
        }
      }
  }

  def getSchemeDetails(psaId: String, srn: SchemeReferenceNumber)
                      (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[SchemeDetails] = {
    val encodedSrnId = URLEncoder.encode(srn.id, StandardCharsets.UTF_8.toString)
    val url = url"${config.schemeDetailsUrl.format(encodedSrnId)}"
    val headers = Seq("idNumber" -> srn.id, "schemeIdType" -> "srn", "psaId" -> psaId)
    fetchData(url, headers, SchemeDetails.readsPsa)
  }

  def getPspSchemeDetails(pspId: String, srn: String)
                         (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SchemeDetails] = {
    val encodedSrn = URLEncoder.encode(srn, StandardCharsets.UTF_8.toString)
    val url = url"${config.pspSchemeDetailsUrl.format(encodedSrn)}"
    val headers = Seq("srn" -> srn, "pspId" -> pspId)
    fetchData(url, headers, SchemeDetails.readsPsp)
  }

  def checkForAssociation(psaId: String, srn: String, idType: String)
                         (implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    val encodedSrn = URLEncoder.encode(srn, StandardCharsets.UTF_8.toString)
    val url = url"${config.checkAssociationUrl.format(encodedSrn)}"
    val headers = Seq((idType, psaId), "schemeReferenceNumber" -> srn, "Content-Type" -> "application/json")
    fetchData(url, headers, implicitly[play.api.libs.json.Reads[Boolean]])
  }
}

