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

import models.TolerantAddress
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpException
import uk.gov.hmrc.http.HttpResponse
import com.google.inject.Inject
import config.FrontendAppConfig
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.Reads
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AddressLookupConnector @Inject()(http: HttpClient, config: FrontendAppConfig){
  def addressLookupByPostCode(postCode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[TolerantAddress]] = {
    val schemeHc = hc.withExtraHeaders("X-Hmrc-Origin" -> "PODS")

    val addressLookupUrl = s"${config.addressLookUp}/v2/uk/addresses?postcode=$postCode"

    implicit val reads: Reads[Seq[TolerantAddress]] = TolerantAddress.postCodeLookupReads

    http.GET[HttpResponse](addressLookupUrl)(implicitly, schemeHc, implicitly) flatMap {
      case response if response.status equals OK => Future.successful {
        response.json.as[Seq[TolerantAddress]]
          .filterNot(a=>a.addressLine1.isEmpty && a.addressLine2.isEmpty && a.addressLine3.isEmpty && a.addressLine4.isEmpty)
      }
      case response =>
        val message = s"Address Lookup failed with status ${response.status} Response body :${response.body}"
        Future.failed(new HttpException(message, response.status))
    } recoverWith logExceptions
  }

  private def logExceptions: PartialFunction[Throwable, Future[Seq[TolerantAddress]]] = {
    case (t: Throwable) => {
      Logger.error("Exception in AddressLookup", t)
      Future.failed(t)
    }
  }
}
