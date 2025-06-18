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

package controllers.amend

import connectors.AFTConnector
import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import helpers.AmendmentHelper
import matchers.JsonMatchers
import models.AmendedChargeStatus.Updated
import models.ChargeType.ChargeTypeDeRegistration
import models.LocalDateBinder.localDateToString
import models.requests.DataRequest
import models.viewModels.ViewAmendmentDetails
import models.AccessMode
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.Json
import play.api.test.Helpers.{route, status, _}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.AFTConstants.QUARTER_START_DATE
import views.html.amend.ViewAllAmendmentsView

import scala.concurrent.Future

class ViewAllAmendmentsControllerSpec
  extends ControllerSpecBase
    with JsonMatchers
    with BeforeAndAfterEach {

  private def httpPathGET: String = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, QUARTER_START_DATE, accessType, versionNumber).url

  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val mockAmendmentHelper: AmendmentHelper = mock[AmendmentHelper]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AmendmentHelper].toInstance(mockAmendmentHelper),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )

  def application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAmendmentHelper)
    reset(mockAFTConnector)
    when(mockAppConfig.schemeDashboardUrl(any(): DataRequest[?])).thenReturn(dummyCall.url)
    when(mockAFTConnector.getAFTDetails(any(), any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAmendmentHelper.getAllAmendments(any(), any(), any())(any())).thenReturn(allAmendments)  }

  private val allAmendments = Seq(
    ViewAmendmentDetails(
      "test member",
      ChargeTypeDeRegistration.toString,
      "£100.00",
      Updated
    )
  )
  private val versionNumber = 3

  private def table(caption: String, allAmendments: Seq[ViewAmendmentDetails]): Table = {
    val tableHeadingRows =
      Seq(
        HeadCell(Text(Messages("allAmendments.memberDetails.h1")), classes = "govuk-!-width-one-quarter"),
        HeadCell(Text(Messages("allAmendments.chargeType.h1")), classes = "govuk-!-width-one-quarter"),
        HeadCell(Text(Messages("allAmendments.chargeAmount.h1")), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric govuk-!-font-weight-bold")
      )

    val tableRows = allAmendments.map { data =>
      Seq(
        TableRow(Text(data.memberDetails), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell")),
        TableRow(Text(Messages(s"allAmendments.charge.type.${data.chargeType}")), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell")),
        TableRow(Text(data.chargeAmount), classes = "govuk-!-width-one-quarter govuk-table__cell--numeric", attributes = Map("role" -> "cell"))
      )
    }

    Table(
      rows = tableRows,
      head = Some(tableHeadingRows),
      attributes = Map("role" -> "table", "aria-describedby" -> messages(s"allAmendments.table.caption.$caption").toLowerCase)
    )
  }

  mutableFakeDataRetrievalAction.setSessionData(
    SampleData.sessionData(sessionAccessData = sessionAccessData(versionNumber, AccessMode.PageAccessModeCompile)))

  "ViewAllAmendments Controller" must {

    "return OK and the correct view for a GET" in {
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
      val result = route(application, httpGETRequest(httpPathGET)).value
      val request = httpGETRequest(httpPathGET)
      val view = application.injector.instanceOf[ViewAllAmendmentsView].apply(
        pageTitle = Messages("allAmendments.draft.title"),
        isDraft = true,
        versionNumber = versionNumber,
        addedTable = table("added", Nil),
        deletedTable = table("deleted", Nil),
        updatedTable = table("updated", allAmendments),
        submitUrl = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version = 3).url,
        returnUrl = dummyCall.url,
        schemeName = schemeName
      )(request, messages)

      status(result) mustEqual OK
      compareResultAndView(result, view)
    }

    "redirect to session expired page when no mandatory data in user answers" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad.url
    }
  }
}
