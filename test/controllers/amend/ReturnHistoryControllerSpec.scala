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

import connectors.{AFTConnector, FinancialStatementConnector}
import controllers.base.ControllerSpecBase
import data.SampleData
import data.SampleData._
import matchers.JsonMatchers
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.SubmitterType.{PSA, PSP}
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.financialStatement.SchemeFS
import models.requests.IdentifierRequest
import models.{AFTOverview, AFTOverviewVersion, AFTVersion, AccessType, Draft, Submission, SubmitterDetails, VersionsWithSubmitter}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Results._
import play.api.test.Helpers.{route, status, _}
import play.twirl.api.Html
import services.SchemeService
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.concurrent.Future

class ReturnHistoryControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {

  private val templateToBeRendered = "amend/returnHistory.njk"

  private def httpPathGET: String = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url

  private val mockFinancialStatementConnector = mock[FinancialStatementConnector]

  private val version1 = AFTVersion(1, LocalDate.of(2020, 4, 17), "Submitted")
  private val version2 = AFTVersion(2, LocalDate.of(2020, 5, 17), "Submitted")
  private val version3 = AFTVersion(3, LocalDate.of(2020, 6, 17), "Compiled")

  private val submitter1 = SubmitterDetails(PSA, "Submitter 1", "A2100005", None, LocalDate.of(2020, 4, 17))
  private val submitter2 = SubmitterDetails(PSP, "Submitter 2", "21000005", Some("A2100005"), LocalDate.of(2020, 5, 17))

  private val versions: Seq[VersionsWithSubmitter] = Seq(
    VersionsWithSubmitter(version1, Some(submitter1)),
    VersionsWithSubmitter(version2, Some(submitter2)),
    VersionsWithSubmitter(version3, None))

  private val multipleVersions = Seq[AFTOverview](
    AFTOverview(
      periodStartDate = LocalDate.of(2020, 4, 1),
      periodEndDate = LocalDate.of(2020, 6, 28),
      tpssReportPresent = false,
      Some(AFTOverviewVersion(
        numberOfVersions = 3,
        submittedVersionAvailable = true,
        compiledVersionAvailable = true
      )))
  )

  val mockSchemeService: SchemeService = mock[SchemeService]
  val mockAFTConnector: AFTConnector = mock[AFTConnector]

  val extraModules: Seq[GuiceableModule] = Seq[GuiceableModule](
    bind[SchemeService].toInstance(mockSchemeService),
    bind[AFTConnector].toInstance(mockAFTConnector),
    bind[FinancialStatementConnector].toInstance(mockFinancialStatementConnector)
  )

  private val application: Application = applicationBuilder(extraModules = extraModules).build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockSchemeService.retrieveSchemeDetails(any(), any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockAFTConnector.getListOfVersions(any(), any())(any(), any())).thenReturn(Future.successful(versions))
    when(mockAFTConnector.getAftOverview(any(), any(), any())(any(), any())).thenReturn(Future.successful(multipleVersions))
    when(mockUserAnswersCacheConnector.lockDetail(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any())).thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq.empty)))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  "ReturnHistory Controller" must {
    "return OK and the correct view for a GET" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val actual = jsonCaptor.getValue

      val actualColumnTextTitles: Option[IndexedSeq[String]] =
        (actual \ "versions" \ "head").validate[JsArray].asOpt
          .map(_.value.toIndexedSeq.flatMap(
            jsValue => (jsValue \ "text").validate[String].asOpt.toSeq))

      val actualColumnHtmlTitles: Option[IndexedSeq[String]] =
        (actual \ "versions" \ "head").validate[JsArray].asOpt
          .map(_.value.toIndexedSeq.flatMap(
            jsValue => (jsValue \ "html").validate[String].asOpt.toSeq))

      val actualColumnValues: Option[IndexedSeq[String]] =
        (actual \ "versions" \ "rows").validate[JsArray].asOpt
          .map(_.value.toIndexedSeq.flatMap(_.validate[JsArray].asOpt.toSeq
            .flatMap(_.value.flatMap { jsValue =>
              ((jsValue \ "text").validate[String].asOpt match {
                case None => (jsValue \ "html").validate[String].asOpt
                case t => t
              }).toSeq
            })))

      actualColumnTextTitles mustBe Some(Seq(messages("returnHistory.version"), messages("returnHistory.status"), messages("returnHistory.submittedBy")))

      actualColumnHtmlTitles mustBe Some(Seq(s"""<span class=govuk-visually-hidden>${messages("site.action")}</span>"""))

      def anchor(startDate: String, version: Int, linkContent: String, accessType: AccessType): String =
        s"<a id= report-version-$version class=govuk-link href=${controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url}>" +
          s"<span aria-hidden=true>$linkContent</span>" +
          s"<span class=govuk-visually-hidden> $linkContent ${messages("returnHistory.visuallyHidden", version)}</span></a>"

      val expectedStartDate = "2020-04-01"

      actualColumnValues mustBe Some(
        Seq(
          messages("returnHistory.versionDraft"),
          messages("returnHistory.compiledStatus"),
          "<span class=govuk-visually-hidden>not yet submitted</span>",
          anchor(expectedStartDate, 3, messages("site.change"), Draft),
          "2",
          messages("returnHistory.submittedOn", "17 May 2020"),
          "Submitter 2",
          anchor(expectedStartDate, 2, messages("site.view"), Submission),
          "1",
          messages("returnHistory.submittedOn", "17 April 2020"),
          "Submitter 1",
          anchor(expectedStartDate, 1, messages("site.view"), Submission)
        )
      )

    }

    "return OK and the correct view with payment and charges URL for a GET where there is scheme fin info" in {
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      when(mockFinancialStatementConnector.getSchemeFS(any())(any(), any()))
        .thenReturn(Future.successful(SampleData.schemeFSResponseAftAndOTC))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())
      verify(mockUserAnswersCacheConnector, times(1)).removeAll(any())(any(), any())

      templateCaptor.getValue mustEqual templateToBeRendered

      val actual = jsonCaptor.getValue

      val expectedJson =
        Json.obj("paymentsAndChargesUrl" -> controllers.financialStatement.paymentsAndCharges.routes
          .PaymentsAndChargesController.onPageLoad(srn, startDate, AccountingForTaxCharges, All).url)

      actual must containJson(expectedJson)
    }
  }
}
