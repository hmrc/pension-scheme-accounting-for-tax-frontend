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
import models.CreditAccessType
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class FinancialInfoCreditAccessConnector @Inject()(http: HttpClientV2, config: FrontendAppConfig)
  extends HttpResponseHelper {

  def creditAccessForSchemePsa(psaId: String, srn: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CreditAccessType]] = {

    val url = url"${config.financialInfoCreditAccessSchemePsaUrl(psaId, srn)}"

    http.get(url).execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.asOpt[CreditAccessType]
        case NOT_FOUND => None
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

  def creditAccessForSchemePsp(pspId: String, srn: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CreditAccessType]] = {

    val url = url"${config.financialInfoCreditAccessSchemePspUrl(pspId, srn)}"

    http.get(url).execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.asOpt[CreditAccessType]
        case NOT_FOUND => None
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

  def creditAccessForPsa(psaId: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[CreditAccessType]] = {

    val url = url"${config.financialInfoCreditAccessPsaUrl(psaId)}"

    http.get(url).execute[HttpResponse].map { response =>
      response.status match {
        case OK => response.json.asOpt[CreditAccessType]
        case NOT_FOUND => None
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

}
