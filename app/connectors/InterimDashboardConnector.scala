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
import models.ToggleDetails
import play.api.http.Status.{NO_CONTENT, OK}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpException, HttpReads, HttpResponse}
import utils.HttpResponseHelper

import scala.concurrent.{ExecutionContext, Future}

class InterimDashboardConnector @Inject()(http: HttpClient, config: FrontendAppConfig)
  extends HttpResponseHelper {
  def getInterimDashboardToggle()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[ToggleDetails] = {
    val endPoint = config.newPensionsSchemeFeatureToggleUrl("interim-dashboard")

    http.GET[HttpResponse](endPoint)(HttpReads.Implicits.readRaw, hc, ec).map { response =>
      val toggleOpt = response.status match {
        case NO_CONTENT => None
        case OK =>
          Some(response.json.as[ToggleDetails])
        case _ =>
          throw new HttpException(response.body, response.status)
      }

      toggleOpt match {
        case None => ToggleDetails("interim-dashboard", None, isEnabled = false)
        case Some(a) => a
      }
    }
  }
}
