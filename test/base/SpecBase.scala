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

package base

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import models.UserAnswers
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterEach, TryValues}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice._
import play.api.i18n.{Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.{Injector, bind}
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.domain.PsaId
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nunjucks.NunjucksRenderer

import scala.concurrent.Future

trait SpecBase extends PlaySpec with GuiceOneAppPerSuite with TryValues with ScalaFutures with IntegrationPatience with MockitoSugar with BeforeAndAfterEach {

  override def beforeEach {
    Mockito.reset(mockRenderer)
  }

  protected implicit val hc: HeaderCarrier = HeaderCarrier()

  protected val userAnswersId = "id"

  protected val psaId = "A0000000"

  protected val srn = "aa"

  protected val schemeName = "Big Scheme"

  protected def userAnswersWithSchemeName = UserAnswers(Json.obj("schemeName" -> schemeName))

  protected def injector: Injector = app.injector

  protected def frontendAppConfig: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]

  protected def messagesApi: MessagesApi = injector.instanceOf[MessagesApi]

  protected def fakeRequest = FakeRequest("", "")

  protected def mockDataRetrievalAction: DataRetrievalAction = mock[DataRetrievalAction]

  protected val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]

  val mockRenderer: NunjucksRenderer = mock[NunjucksRenderer]

  protected implicit def messages: Messages = messagesApi.preferred(fakeRequest)

  protected def applicationBuilder(userAnswers: Option[UserAnswers] = None, psaId: String = psaId): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        bind[DataRequiredAction].to[DataRequiredActionImpl],
        bind[IdentifierAction].to[FakeIdentifierAction],
        bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(userAnswers, PsaId(psaId))),
        bind[NunjucksRenderer].toInstance(mockRenderer),
        bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector)
      )
}
