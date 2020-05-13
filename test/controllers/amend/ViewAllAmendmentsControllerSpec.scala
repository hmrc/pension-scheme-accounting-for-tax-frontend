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
import models.viewModels.ViewAmendmentDetails
import models.{AccessMode, Enumerable, GenericViewModel}
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.{when, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Results
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Literal
import utils.AFTConstants.QUARTER_START_DATE
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.Future

class ViewAllAmendmentsControllerSpec
    extends ControllerSpecBase
    with NunjucksSupport
    with JsonMatchers
    with BeforeAndAfterEach
    with Enumerable.Implicits
    with Results
    with ScalaFutures {

  private def httpPathGET: String = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, QUARTER_START_DATE).url
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val mockAmendmentHelper: AmendmentHelper = mock[AmendmentHelper]
  private val mockAFTConnector: AFTConnector = mock[AFTConnector]
  private val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[AmendmentHelper].toInstance(mockAmendmentHelper),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )
  def application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction, extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    reset(mockAmendmentHelper, mockAFTConnector, mockRenderer)
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockAFTConnector.getAFTDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockAmendmentHelper.getAllAmendments(any(), any())(any(), any())).thenReturn(allAmendments)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

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
        Cell(msg"allAmendments.memberDetails.h1", classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"allAmendments.chargeType.h1", classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"allAmendments.chargeAmount.h1", classes = Seq("govuk-!-width-one-quarter"))
      )

    val tableRows = allAmendments.map { data =>
      Seq(
        Cell(Literal(data.memberDetails), classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"allAmendments.charge.type.${data.chargeType}", classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.chargeAmount), classes = Seq("govuk-!-width-one-quarter"))
      )
    }

    Table(caption = Some(messages(s"allAmendments.table.caption.$caption")), head = tableHeadingRows, rows = tableRows)
  }

  private def expectedJson: JsObject = Json.obj(
    fields = "srn" -> srn,
    "startDate" -> Some(startDate),
    "viewModel" -> GenericViewModel(
      submitUrl = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, Some(s"$versionNumber")).url,
      returnUrl = dummyCall.url,
      schemeName = schemeName
    ),
    "addedTable" -> table("added", Nil),
    "deletedTable" -> table("deleted", Nil),
    "updatedTable" -> table("updated", allAmendments)
  )

  mutableFakeDataRetrievalAction.setSessionData(
    SampleData.sessionData(sessionAccessData = sessionAccessData(versionNumber, AccessMode.PageAccessModeCompile)))

  "ViewAllAmendments Controller" must {

    "return OK and the correct view for a GET" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])
      mutableFakeDataRetrievalAction.setDataToReturn(Some(userAnswersWithSchemeName))
      val result = route(application, httpGETRequest(httpPathGET)).value
      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual "amend/viewAllAmendments.njk"
      jsonCaptor.getValue must containJson(expectedJson)
    }

    "redirect to session expired page when no mandatory data in user answers" in {
      mutableFakeDataRetrievalAction.setDataToReturn(None)
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual SEE_OTHER
      redirectLocation(result).value mustBe controllers.routes.SessionExpiredController.onPageLoad().url
    }
  }
}
