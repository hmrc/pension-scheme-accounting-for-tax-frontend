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
import models.TolerantAddress
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpException, HttpResponse, StringContextOps}
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

class AddressLookupConnector @Inject()(http: HttpClientV2, config: FrontendAppConfig) {
  private val logger = Logger(classOf[AddressLookupConnector])

  def addressLookupByPostCode(postcode: String)
                             (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TolerantAddress]] = {
    val schemeHc = Seq("X-Hmrc-Origin" -> "PODS")

    val addressLookupUrl = url"${config.addressLookUp}/lookup"

    implicit val reads: Reads[Seq[TolerantAddress]] = TolerantAddress.postcodeLookupReads

    val lookupAddressByPostcode =Json.obj("postcode"->postcode)
    http.post(addressLookupUrl).withBody(lookupAddressByPostcode).setHeader(schemeHc*).execute[HttpResponse].flatMap {
      case response if response.status `equals` OK => Future.successful {
        response.json.as[Seq[TolerantAddress]]
          .filterNot(
            a => a.addressLine1.isEmpty && a.addressLine2.isEmpty && a.townOrCity.isEmpty && a.county.isEmpty
          )
      }
      case response =>
        val message = s"Address Lookup failed with status ${response.status} Response body :${response.body}"
        Future.failed(new HttpException(message, response.status))
    } recoverWith logExceptions
  }

  private def logExceptions: PartialFunction[Throwable, Future[Seq[TolerantAddress]]] = {
    case t: Throwable =>
      logger.error("Exception in AddressLookup", t)
      Future.failed(t)
  }
}
