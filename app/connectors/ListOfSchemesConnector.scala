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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import config.FrontendAppConfig
import models.ListOfSchemes
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsError, JsResultException, JsSuccess, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ListOfSchemesConnectorImpl])
trait ListOfSchemesConnector {

  def getListOfSchemes(psaId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]]
  def getListOfSchemesForPsp(pspId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]]
}

@Singleton
class ListOfSchemesConnectorImpl @Inject()(http: HttpClient, config: FrontendAppConfig) extends ListOfSchemesConnector {

  override def getListOfSchemes(psaId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]] = {
    val (url, schemeHc) = if(config.listOfSchemesIFEnabled) {
      (config.listOfSchemesIFUrl, hc.withExtraHeaders("idType" -> "PSA", "idValue" -> psaId))
    } else {
      (config.listOfSchemesUrl, hc.withExtraHeaders("psaId" -> psaId))
    }

    listOfSchemes(url)(schemeHc, ec)
  }

  override def getListOfSchemesForPsp(pspId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]] = {
    val schemeHc = hc.withExtraHeaders("idType" -> "PSP", "idValue" -> pspId)
    listOfSchemes(config.listOfSchemesIFUrl)(schemeHc, ec)
  }

  private def listOfSchemes(url: String)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]] = {
    http.GET[HttpResponse](url).map { response =>
      response.status match {
        case OK => val json = Json.parse(response.body)
          json.validate[ListOfSchemes] match {
            case JsSuccess(value, _) => Right(value)
            case JsError(errors) => throw JsResultException(errors)
          }
        case _ =>
          Logger.error(response.body)
          Left(response)
      }
    }
  }
}
