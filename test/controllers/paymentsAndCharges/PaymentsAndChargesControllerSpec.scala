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

package controllers.paymentsAndCharges

import java.time.LocalDate

import connectors.FinancialStatementConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import matchers.JsonMatchers
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_OTC_AFT_RETURN}
import models.{Enumerable, SchemeDetails}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.when
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

class PaymentsAndChargesControllerSpec
    extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGET: String = controllers.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, year = 2020).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction

  private val mockPaymentAndChargesConnector = mock[FinancialStatementConnector]
  private val schemeService = mock[SchemeService]

  val application: Application =
    applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, Seq(
      bind[FinancialStatementConnector].toInstance(mockPaymentAndChargesConnector),
      bind[SchemeService].toInstance(schemeService)
    )).build()

  private val schemeFSResponse: Seq[SchemeFS] = Seq(
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = PSS_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 23000.55,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30")
    ),
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = PSS_OTC_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 24000.41,
      periodStartDate =  LocalDate.parse("2020-04-01"),
      periodEndDate =  LocalDate.parse("2020-06-30")
    ),
    SchemeFS(
      chargeReference = "XY002610150184",
      chargeType = PSS_OTC_AFT_RETURN,
      dueDate = Some(LocalDate.parse("2020-02-15")),
      totalAmount = 56432.00,
      outstandingAmount = 56049.08,
      stoodOverAmount = 25089.08,
      amountDue = 1029.05,
      accruedInterestTotal = 24000.41,
      periodStartDate =  LocalDate.parse("2020-07-01"),
      periodEndDate =  LocalDate.parse("2020-09-30")
    )
  )

  private def expectedJson: JsObject = Json.obj(
    fields = "srn" -> srn)

  "PaymentsAndCharges Controller" when {

    "on a GET and overviewApi is disabled i.e before 21st July 2020" must {

      "return to ChargeType page in every case" in {
        val templateCaptor = ArgumentCaptor.forClass(classOf[String])
        val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
        when(schemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SchemeDetails("", "", "")))
        when(mockPaymentAndChargesConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(schemeFSResponse))
        val result = route(application, httpGETRequest(httpPathGET)).value

        templateCaptor.getValue mustEqual "amend/viewAllAmendments.njk"
        jsonCaptor.getValue must containJson(expectedJson)
      }
    }

  }
}
