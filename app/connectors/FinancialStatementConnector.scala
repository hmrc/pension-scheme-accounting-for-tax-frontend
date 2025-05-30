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
import models.financialStatement._
import org.apache.pekko.http.scaladsl.model.Uri
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class FinancialStatementConnector @Inject()(httpClientV2: HttpClientV2, config: FrontendAppConfig)
  extends HttpResponseHelper {

  def getPsaFS(psaId: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PsaFS] = {

    val url = url"${config.psaFinancialStatementUrl}"
    val schemeHc = hc.withExtraHeaders("psaId" -> psaId)

    httpClientV2
      .get(url)(schemeHc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          val psaFS = response.json.as[PsaFS]
          PsaFS(
            inhibitRefundSignal = psaFS.inhibitRefundSignal,
            seqPsaFSDetail = psaFS.seqPsaFSDetail.filterNot(_.chargeType == PsaFSChargeType.PAYMENT_ON_ACCOUNT))
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

  def getPsaFSWithPaymentOnAccount(psaId: String)
                                  (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[PsaFS] = {

    val url = url"${config.psaFinancialStatementUrl}"
    val schemeHc = hc.withExtraHeaders("psaId" -> psaId)

    httpClientV2
      .get(url)(schemeHc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          response.json.as[PsaFS]
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }

  def getSchemeFS(pstr: String, srn: String, loggedInAsPsa: Boolean)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SchemeFS] = {
    val url = url"${Uri({config.schemeFinancialStatementUrl.format(srn)})
              .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr)
    httpClientV2
      .get(url)(schemeHc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map{ response => response.status match {
        case OK =>
          val schemeFS = response.json.as[SchemeFS]
          SchemeFS(
            inhibitRefundSignal = schemeFS.inhibitRefundSignal,
            seqSchemeFSDetail = schemeFS.seqSchemeFSDetail.filterNot(_.chargeType == SchemeFSChargeType.PAYMENT_ON_ACCOUNT)
          )
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
        }
      }
  }

  def getSchemeFSPaymentOnAccount(pstr: String, srn: String, loggedInAsPsa: Boolean)
                                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SchemeFS] = {
    val url = url"${Uri(config.schemeFinancialStatementUrl.format(srn))
                  .withQuery(Uri.Query("loggedInAsPsa" -> s"$loggedInAsPsa"))}"
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr)
    httpClientV2
      .get(url)(schemeHc)
      .transform(_.withRequestTimeout(config.ifsTimeout))
      .execute[HttpResponse].map { response =>
      response.status match {
        case OK =>
          response.json.as[SchemeFS]
        case _ =>
          handleErrorResponse("GET", url.toString)(response)
      }
    }
  }
}
