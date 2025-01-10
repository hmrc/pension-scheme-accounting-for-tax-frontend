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

package controllers.financialStatement.penalties

import connectors.FinancialStatementConnector
import connectors.FinancialStatementConnectorSpec.psaFSResponse
import connectors.cache.FinancialInfoCacheConnector
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.PenaltiesFilter.All
import models.financialStatement.PsaFSDetail
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.{PenaltiesCache, PenaltiesService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.viewmodels.{NunjucksSupport, _}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text

import scala.concurrent.Future

class ChargeDetailsControllerSpec
  extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  import ChargeDetailsControllerSpec._

  private def httpPathGETAssociated(chargeReferenceIndex: String): String =
    controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(
      identifier = srn, chargeReferenceIndex = chargeReferenceIndex, All).url

  private def httpPathGETUnassociated: String =
    controllers.financialStatement.penalties.routes.ChargeDetailsController.onPageLoad(
      identifier = "0", chargeReferenceIndex = "0", All).url

  val mockPenaltiesService: PenaltiesService = mock[PenaltiesService]
  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockFSConnector: FinancialStatementConnector = mock[FinancialStatementConnector]
  val mockFIConnector: FinancialInfoCacheConnector = mock[FinancialInfoCacheConnector]

  private val extraModules: Seq[GuiceableModule] =
    Seq[GuiceableModule](
      bind[PenaltiesService].toInstance(mockPenaltiesService),
      bind[SchemeService].toInstance(mockSchemeService),
      bind[FinancialStatementConnector].toInstance(mockFSConnector),
      bind[FinancialInfoCacheConnector].toInstance(mockFIConnector)
    )

  val application: Application = applicationBuilder(extraModules = extraModules).build()
  private val templateToBeRendered = "financialStatement/penalties/chargeDetails.njk"
  private val commonJson: JsObject = Json.obj(
    "heading" -> "Accounting for Tax Late Filing Penalty",
    "isOverdue" -> true,
    "period" -> msg"penalties.period".withArgs("1 April", "30 June 2020"),
    "chargeReference" -> chargeRef,
    "list" -> getRows()
  )

  val isOverdue: PsaFSDetail => Boolean = _ => true

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPenaltiesService)
    reset(mockRenderer)
    when(mockPenaltiesService.chargeDetailsRows(any())(any())).thenReturn(getRows)
    when(mockPenaltiesService.isPaymentOverdue).thenReturn(isOverdue)
    when(mockPenaltiesService.getPenaltiesForJourney(any(), any())(any(), any()))
      .thenReturn(Future.successful(PenaltiesCache(psaId, "psa-name", psaFSResponse)))
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeDetails(schemeDetails.schemeName, pstr, "Open", None)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(play.twirl.api.Html("")))
  }

  "ChargeDetails Controller" when {
    "on a GET" must {

      "render the correct view with penalty tables for associated" in {

        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETAssociated("0"))).value
        val json = Json.obj(
          "schemeAssociated" -> true,
          "schemeName" -> schemeDetails.schemeName
        )

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(commonJson ++ json)
      }

      "render the correct view with penalty tables for unassociated" in {

        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        val result = route(application, httpGETRequest(httpPathGETUnassociated)).value
        val json = Json.obj(
          "schemeAssociated" -> false
        )

        status(result) mustEqual OK

        verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

        templateCaptor.getValue mustEqual templateToBeRendered

        jsonCaptor.getValue must containJson(commonJson ++ json)
      }

      "catch IndexOutOfBoundsException" in {
        when(mockFIConnector.fetch(any(), any())).thenReturn(Future.successful(Some(Json.toJson(psaFSResponse))))

        val result = route(application, httpGETRequest(httpPathGETAssociated("3"))).value

        status(result) mustBe SEE_OTHER
        redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
      }
    }
  }
}

object ChargeDetailsControllerSpec {
  val srn = "S2400000041"
  val pstr = "24000040IN"
  val chargeRef = "XY002610150184"


  private def getRows()(implicit messages: Messages) = Seq(
    SummaryListRow(
      key = Key(Text("Accounting for Tax late filing penalty"), classes = "govuk-!-width-three-quarters"),
      value = Value(Text("£80000.00"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
    ),
    SummaryListRow(
      key = Key(Text(messages("penalties.chargeDetails.payments")), classes = "govuk-!-width-three-quarters"),
      value = Value(Text("£23950.92"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
    ),
    SummaryListRow(
      key = Key(Text(messages("penalties.chargeDetails.amountUnderReview")), classes = "govuk-!-width-three-quarters"),
      value = Value(Text("£25089.08"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
    ),
    SummaryListRow(
      key = Key(Text(messages("penalties.chargeDetails.totalDueBy", "15 July 2020")), classes = "govuk-table__header--numeric govuk-!-padding-right-0"),
      value = Value(Text("£1029.05"), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric")
    )
  )

}
