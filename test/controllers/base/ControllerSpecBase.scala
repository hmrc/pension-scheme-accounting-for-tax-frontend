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

package controllers.base

import base.SpecBase
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import models.UserAnswers
import navigators.CompoundNavigator
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import play.api.mvc.{AnyContentAsEmpty, AnyContentAsFormUrlEncoded}
import play.api.test.Helpers.{GET, POST}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.nunjucks.NunjucksRenderer

trait ControllerSpecBase extends SpecBase with BeforeAndAfterEach with MockitoSugar {

  override def beforeEach: Unit = Mockito.reset(mockRenderer, mockUserAnswersCacheConnector, mockCompoundNavigator)

  protected def mockDataRetrievalAction: DataRetrievalAction = mock[DataRetrievalAction]
  protected val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  protected val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  protected val mockCompoundNavigator: CompoundNavigator = mock[CompoundNavigator]
  protected val mockRenderer: NunjucksRenderer = mock[NunjucksRenderer]

  def modules(userAnswers: Option[UserAnswers]): Seq[GuiceableModule] = Seq(
    bind[DataRequiredAction].to[DataRequiredActionImpl],
    bind[IdentifierAction].to[FakeIdentifierAction],
    bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(userAnswers)),
    bind[NunjucksRenderer].toInstance(mockRenderer),
    bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
    bind[CompoundNavigator].toInstance(mockCompoundNavigator)
  )

  def modules2(fakeDataRetrievalAction: FakeDataRetrievalAction2): Seq[GuiceableModule] = Seq(
    bind[DataRequiredAction].to[DataRequiredActionImpl],
    bind[IdentifierAction].to[FakeIdentifierAction],
    bind[DataRetrievalAction].toInstance(fakeDataRetrievalAction),
    bind[NunjucksRenderer].toInstance(mockRenderer),
    bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
    bind[CompoundNavigator].toInstance(mockCompoundNavigator)
  )

  protected def applicationBuilder(userAnswers: Option[UserAnswers] = None): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules(userAnswers): _*
      )

  protected def applicationBuilder2(fakeDataRetrievalAction: FakeDataRetrievalAction2): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules2(fakeDataRetrievalAction): _*
      )

  protected def httpGETRequest(path: String): FakeRequest[AnyContentAsEmpty.type] = FakeRequest(GET, path)

  protected def httpPOSTRequest(path: String, values: Map[String, Seq[String]]): FakeRequest[AnyContentAsFormUrlEncoded] =
    FakeRequest
      .apply(
        method = POST,
        uri = path,
        headers = FakeHeaders(Seq(HeaderNames.HOST -> "localhost")),
        body = AnyContentAsFormUrlEncoded(values))
}
