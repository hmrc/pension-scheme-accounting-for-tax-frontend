/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.{ImplementedBy, Inject}
import config.FrontendAppConfig
import models.UserAnswers
import play.api.libs.json.JsObject
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AFTConnectorImpl])
trait AFTConnector {

  def submitAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit]
}

class AFTConnectorImpl @Inject()(http: HttpClient, config: FrontendAppConfig) extends AFTConnector {

  override def submitAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val url = config.submitAftReturn
    val aftHc = hc.withExtraHeaders(headers = "pstr" -> pstr)
    http.POST[JsObject, HttpResponse](url, answers.data)(implicitly, implicitly, aftHc, implicitly).map(_ => ())
  }
}
