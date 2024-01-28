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

package base

import config.FrontendAppConfig
import data.SampleData
import models.requests.DataRequest
import models.{SessionAccessData, UserAnswers}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.Injector
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.http.HeaderCarrier

trait SpecBase extends PlaySpec with GuiceOneAppPerSuite {

  protected implicit val hc: HeaderCarrier = HeaderCarrier()

  protected def injector: Injector = app.injector

  protected def frontendAppConfig: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]

  protected def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]

  protected def fakeRequest = FakeRequest("", "")

  protected implicit def messages: Messages = messagesApi.preferred(fakeRequest)

  protected implicit def request(ua: UserAnswers = UserAnswers(),
    sessionAccessData: SessionAccessData = SampleData.sessionAccessDataCompile,
    psaId: Option[String] = Some(SampleData.psaId),
    pspId: Option[String] = Some(SampleData.pspId)
  ): DataRequest[AnyContent] =
    DataRequest(
      request = fakeRequest,
      internalId = "",
      psaId = psaId.map(PsaId(_)),
      pspId = pspId.map(PspId(_)),
      userAnswers = ua,
      sessionData = SampleData.sessionData(name = None, sessionAccessData = sessionAccessData)
    )
}
