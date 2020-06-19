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
import controllers.actions.AllowAccessActionProvider
import controllers.actions._
import models.UserAnswers
import models.requests.DataRequest
import navigators.CompoundNavigator
import org.mockito.Matchers.any
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceableModule
import play.api.mvc.ActionFilter
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.AnyContentAsFormUrlEncoded
import play.api.mvc.Result
import play.api.test.Helpers.GET
import play.api.test.Helpers.POST
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import uk.gov.hmrc.nunjucks.NunjucksRenderer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait ControllerSpecBase extends SpecBase with BeforeAndAfterEach with MockitoSugar {

  val FakeActionFilter: ActionFilter[DataRequest] = new ActionFilter[DataRequest] {
    override protected def executionContext: ExecutionContext = global

    override protected def filter[A](request: DataRequest[A]): Future[Option[Result]] = Future.successful(None)
  }

  override def beforeEach: Unit = {
    Mockito.reset(mockRenderer, mockUserAnswersCacheConnector, mockCompoundNavigator, mockAllowAccessActionProvider)
    when(mockAllowAccessActionProvider.apply(any(), any(), any(), any(), any())).thenReturn(FakeActionFilter)
  }

  protected def mockDataRetrievalAction: DataRetrievalAction = mock[DataRetrievalAction]

  protected val mockAppConfig: FrontendAppConfig = mock[FrontendAppConfig]

  protected val mockUserAnswersCacheConnector: UserAnswersCacheConnector = mock[UserAnswersCacheConnector]
  protected val mockCompoundNavigator: CompoundNavigator = mock[CompoundNavigator]
  protected val mockRenderer: NunjucksRenderer = mock[NunjucksRenderer]

  protected val mockAllowAccessActionProvider: AllowAccessActionProvider = mock[AllowAccessActionProvider]

  def modules: Seq[GuiceableModule] = Seq(
    bind[DataRequiredAction].to[DataRequiredActionImpl],
    bind[IdentifierAction].to[FakeIdentifierAction],
    bind[NunjucksRenderer].toInstance(mockRenderer),
    bind[FrontendAppConfig].toInstance(mockAppConfig),
    bind[UserAnswersCacheConnector].toInstance(mockUserAnswersCacheConnector),
    bind[CompoundNavigator].toInstance(mockCompoundNavigator),
    bind[AllowAccessActionProvider].toInstance(mockAllowAccessActionProvider)
  )

  protected def applicationBuilder(userAnswers: Option[UserAnswers] = None,
                                   extraModules: Seq[GuiceableModule] = Seq.empty): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules ++ extraModules ++ Seq[GuiceableModule](
          bind[DataRetrievalAction].toInstance(new FakeDataRetrievalAction(userAnswers))
        ): _*
      )

  protected def applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction,
                                                         extraModules: Seq[GuiceableModule] = Seq.empty): GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .overrides(
        modules ++ extraModules ++ Seq[GuiceableModule](
          bind[DataRetrievalAction].toInstance(mutableFakeDataRetrievalAction)
        ): _*
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
