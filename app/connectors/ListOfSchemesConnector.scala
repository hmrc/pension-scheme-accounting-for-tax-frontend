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

import com.google.inject.{Inject, Singleton, ImplementedBy}
import config.FrontendAppConfig
import models.ListOfSchemes
import play.api.Logger
import play.api.http.Status._
import play.api.libs.json.{JsResultException, JsError, JsSuccess, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HttpClient, HttpResponse, HeaderCarrier}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ListOfSchemesConnectorImpl])
trait ListOfSchemesConnector {

  def getListOfSchemes(psaId: String)
                      (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]]

  def getListOfSchemesForPsp(pspId: String)
                            (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]]
}

@Singleton
class ListOfSchemesConnectorImpl @Inject()(
                                            http: HttpClient,
                                            config: FrontendAppConfig
                                          ) extends ListOfSchemesConnector {

  private val logger = Logger(classOf[ListOfSchemesConnectorImpl])

  override def getListOfSchemes(psaId: String)
                               (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]] = {
      val (url, schemeHc) = (config.listOfSchemesUrl, hc.withExtraHeaders("idType" -> "psaid", "idValue" -> psaId))
      listOfSchemes(url)(schemeHc, ec)
  }

  override def getListOfSchemesForPsp(pspId: String)
                                     (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, ListOfSchemes]] = {
    val schemeHc = hc.withExtraHeaders("idType" -> "PSP", "idValue" -> pspId)
    listOfSchemes(config.listOfSchemesUrl)(schemeHc, ec)
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
          logger.error(response.body)
          Left(response)
      }
    }
  }
}
