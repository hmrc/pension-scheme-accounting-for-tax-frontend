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

package controllers.financialStatement

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import controllers.base.ControllerSpecBase
import matchers.JsonMatchers
import models.Enumerable
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{Json, JsObject}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message
import viewmodels.{Link, PspDashboardAftViewModel}

import scala.concurrent.Future

class PenaltiesPartialControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers
  with BeforeAndAfterEach with Enumerable.Implicits with Results with ScalaFutures {

  private def httpPathGET: String = controllers.financialStatement.routes.PenaltiesPartialController.penaltiesPartial().url


  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[FinancialStatementConnector].toInstance(mockFSConnector)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()

  private val templateToBeRendered = "partials/penalties.njk"
  private val jsonToPassToTemplate: PspDashboardAftViewModel => JsObject = display => Json.obj("viewModel" -> Json.toJson(display))

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockFSConnector, mockRenderer)
    when(mockFSConnector.getPsaFS(any())(any(), any()))
      .thenReturn(Future.successful(psaFSResponse))
    when(mockAppConfig.viewPenaltiesUrl).thenReturn(frontendAppConfig.viewPenaltiesUrl)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))

  }

  "PenaltiesPartial Controller" when {
    "on a GET" must {

      "return the html with the link when data is received from PSA financial statement api" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered
/*
"viewModel":{"subHeadings":[{"total":"£0.00","span":"Total amount due:"},{"total":"£2,058.10","span":"Total overdue payments:"}],
"links":[{"id":"aft-penalties-id","url":"http://localhost:8206/manage-pension-scheme-accounting-for-tax/2020/select-a-scheme",
"linkText":"View your penalties for 2020"}]}
 */
        val expectedSubheadings =
          Seq(
            Json.obj(
              "total" -> "£0.00",
              "span" -> "Total amount due:"
            ),
            Json.obj(
              "total" -> "£2,058.10",
              "span" -> "Total overdue payments:"
            )
          )

        //controllers.financialStatement.routes.SelectSchemeController.onPageLoad(year = "2020").url,
        def expectedLinks = Seq(
          Link(id = "aft-penalties-id",
            url = "http://localhost:8206/manage-pension-scheme-accounting-for-tax/2020/select-a-scheme",
            linkText = Message("psaPenaltiesCard.viewPenalties"),
            hiddenText = None)
        )

        def expectedViewModel = PspDashboardAftViewModel(subHeadings = expectedSubheadings, links = expectedLinks)

        jsonCaptor.getValue must containJson(jsonToPassToTemplate(expectedViewModel))
      }

      "return empty html when empty sequence is received from PSA financial statement api" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        when(mockFSConnector.getPsaFS(any())(any(), any()))
          .thenReturn(Future.successful(Seq.empty))

        val result = route(application, httpGETRequest(httpPathGET)).value

        status(result) mustEqual OK

        verify(mockRenderer, times(0)).render(any(), any())(any())
      }

    }

  }
}
