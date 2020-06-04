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

import java.time.LocalDate

import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.base.ControllerSpecBase
import play.api.mvc.Result
import play.api.mvc.Results._
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.AFTVersion
import models.LocalDateBinder._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.JsArray
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.test.Helpers.route
import play.api.test.Helpers.status
import play.api.test.Helpers._
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.AFTConstants._

import scala.concurrent.Future

class ReturnHistoryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {

  private val templateToBeRendered = "amend/returnHistory.njk"
  private def httpPathGET: String = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url

  private val cssQuarterWidth = "govuk-!-width-one-quarter"
  private val cssHalfWidth = "govuk-!-width-one-half"

  private val version1 = AFTVersion(1, LocalDate.of(2020, 4, 17), "Submitted")
  private val version2 = AFTVersion(2, LocalDate.of(2020, 5, 17), "Submitted")
  private val version3 = AFTVersion(3, LocalDate.of(2020, 6, 17), "Compiled")
  private val versions = Seq(version1, version2, version3)

  private def versionsTable = Json.obj(
    "firstCellIsHeader" -> false,
    "head" -> Json.arr(
      Json.obj("text" -> "Version", "classes" -> cssHalfWidth),
      Json.obj("text" -> "Date submitted", "classes" -> cssQuarterWidth),
      Json.obj("text" -> "")
    ),

    "rows" -> Json.arr(
      Json.arr(
        Json.obj("text" -> "Submission 3","classes" -> cssHalfWidth),
        Json.obj("text" -> "17/6/2020","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a id=report-version-3 href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/3/summary> View<span class= govuk-visually-hidden>submission 3 of the AFT return</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Submission 2","classes" -> cssHalfWidth),
        Json.obj("text" -> "17/5/2020","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a id=report-version-2 href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/2/summary> View<span class= govuk-visually-hidden>submission 2 of the AFT return</span> </a>","classes" -> cssQuarterWidth)
      ),
      Json.arr(
        Json.obj("text" -> "Submission 1","classes" -> cssHalfWidth),
        Json.obj("text" -> "17/4/2020","classes" -> cssQuarterWidth),
        Json.obj("html" -> s"<a id=report-version-1 href=/manage-pension-scheme-accounting-for-tax/aa/new-return/$QUARTER_START_DATE/1/summary> View<span class= govuk-visually-hidden>submission 1 of the AFT return</span> </a>","classes" -> cssQuarterWidth)
      )
    )
  )

  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockAFTConnector: AFTConnector = mock[AFTConnector]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[AFTConnector].toInstance(mockAFTConnector)
  )

  private val application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockAFTConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(versions))
    when(mockUserAnswersCacheConnector.lockedBy(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok("")))
    when(mockAppConfig.managePensionsSchemeSummaryUrl).thenReturn(dummyCall.url)
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ReturnHistory Controller" must {
    "return OK and the correct view for a GET" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(),any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val actual = jsonCaptor.getValue
      val actualColumnTitles = (actual \ "versions" \ "head").validate[JsArray].asOpt
          .map(_.value.flatMap(jsValue => (jsValue \ "text").validate[String].asOpt.toSeq))

      val actualColumnValues = (actual \ "versions" \ "rows").validate[JsArray].asOpt
        .map(_.value.flatMap( _.validate[JsArray].asOpt.toSeq
          .flatMap( _.value.flatMap(jsValue => (jsValue \ "text").validate[String].asOpt.toSeq))))

      actualColumnTitles mustBe Some(Seq(messages("returnHistory.version"), messages("returnHistory.status"), ""))

      actualColumnValues mustBe Some(
        Seq(
          messages("returnHistory.versionDraft"),
          messages("returnHistory.compiledStatus"),
          "2",
          messages("returnHistory.submittedOn", "17/5/2020"),
          "1",
          messages("returnHistory.submittedOn", "17/4/2020") )
      )
    }
  }
}
