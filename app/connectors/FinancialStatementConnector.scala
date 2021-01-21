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
import models.financialStatement.{PsaFS, SchemeFSChargeType, PsaFSChargeType, SchemeFS}
import play.api.http.Status._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpResponse, HeaderCarrier}
import uk.gov.hmrc.http.HttpClient
import utils.HttpResponseHelper

import scala.concurrent.{Future, ExecutionContext}

class FinancialStatementConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {

  def getPsaFS(psaId: String)
              (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[PsaFS]] = {

    val url = config.psaFinancialStatementUrl
    val schemeHc = hc.withExtraHeaders("psaId" -> psaId)

    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      response.status match {
        case OK =>
          response.json.as[Seq[PsaFS]].filter(_.chargeType != PsaFSChargeType.PAYMENT_ON_ACCOUNT)
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    }
  }

  def getSchemeFS(pstr: String)
                 (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[SchemeFS]] = {

    val url = config.schemeFinancialStatementUrl
    val schemeHc = hc.withExtraHeaders("pstr" -> pstr)

    http.GET[HttpResponse](url)(implicitly, schemeHc, implicitly).map { response =>
      response.status match {
        case OK =>
          response.json.as[Seq[SchemeFS]].filter(_.chargeType != SchemeFSChargeType.PAYMENT_ON_ACCOUNT)
        case _ =>
          handleErrorResponse("GET", url)(response)
      }
    }
  }
}
