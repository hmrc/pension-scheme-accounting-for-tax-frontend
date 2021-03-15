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
import models.AdministratorOrPractitioner
import models.requests.IdentifierRequest
import play.api.libs.json._
import uk.gov.hmrc.http.{HttpClient, _}
import uk.gov.hmrc.http.HttpReads.Implicits._
import utils.HttpResponseHelper
import play.api.http.Status._

import scala.concurrent.{ExecutionContext, Future}

class AdministratorOrPractitionerConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {

  def getAdministratorOrPractitioner[A](id:String)(
                            implicit hc: HeaderCarrier,
                            ec: ExecutionContext,
                            request: IdentifierRequest[A]
                          ): Future[Option[AdministratorOrPractitioner]] = {

    val url = config.administratorOrPractitionerUrl(id)

    http.GET[HttpResponse](url)(implicitly, implicitly, implicitly) map {
      response =>
        response.status match {
          case NOT_FOUND =>
            None
          case OK =>
            (Json.parse(response.body) \ "administratorOrPractitioner").toOption
              .flatMap(_.validate[AdministratorOrPractitioner].asOpt)
          case _ =>
            handleErrorResponse("GET", url)(response)
        }
    }
  }
}
