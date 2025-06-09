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
import models.{AFTOverview, AFTOverviewVersion, AFTVersion, AccessType, Draft, Quarters, Submission, SubmitterDetails, VersionsWithSubmitter}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.mvc.Results._
import play.api.test.Helpers.{route, status, _}
import services.SchemeService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, Table, TableRow}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import views.html.amend.ReturnHistoryView

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

class ReturnHistoryControllerSpec extends ControllerSpecBase with JsonMatchers {
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

  private val paymentsAndChargesUrl = controllers
    .financialStatement.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, startDate, AccountingForTaxCharges, All).url

  private val tableHead = Seq(
    HeadCell(Text(Messages("returnHistory.version")), classes = "govuk-!-width-one-quarter"),
    HeadCell(Text(Messages("returnHistory.status")), classes = "govuk-!-width-one-half"),
    HeadCell(Text(Messages("returnHistory.submittedBy")), classes = "govuk-!-width-one-quarter"),
    HeadCell(
      HtmlContent(s"""<span class=govuk-visually-hidden>${messages("site.action")}</span>""")
    )
  )

  private def link(version: AFTVersion, linkText: String, accessType: AccessType) =
    s"<a id= report-version-${version.reportVersion} class=govuk-link href=${controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version.reportVersion)}>" +
      s"<span aria-hidden=true>$linkText</span>" +
      s"<span class=govuk-visually-hidden> $linkText " +
      s"${messages(s"returnHistory.visuallyHidden", version.reportVersion.toString)}</span></a>"

  private val tableRows = Seq(
    Seq(
      TableRow(Text("Draft"), classes = "govuk-!-width-one-quarter"),
      TableRow(Text("In progress"), classes = "govuk-!-width-one-half"),
      TableRow(HtmlContent("<span class=govuk-visually-hidden>not yet submitted</span>"), classes = "govuk-!-width-one-quarter"),
      TableRow(HtmlContent(link(version3, "Change", Draft)), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell"))
    ),
    Seq(
      TableRow(Text(version2.reportVersion.toString), classes = "govuk-!-width-one-quarter"),
      TableRow(Text(Messages("returnHistory.submittedOn", version2.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))),
        classes = "govuk-!-width-one-half"),
      TableRow(Text(submitter2.submitterName), classes = "govuk-!-width-one-quarter"),
      TableRow(HtmlContent(link(version2, "View", Submission)), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell"))
    ),
    Seq(
      TableRow(Text(version1.reportVersion.toString), classes = "govuk-!-width-one-quarter"),
      TableRow(Text(Messages("returnHistory.submittedOn", version1.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")))),
        classes = "govuk-!-width-one-half"),
      TableRow(Text(submitter1.submitterName), classes = "govuk-!-width-one-quarter"),
      TableRow(HtmlContent(link(version1, "View", Submission)), classes = "govuk-!-width-one-quarter", attributes = Map("role" -> "cell"))
    )
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockSchemeService.retrieveSchemeDetails(any(), any())(any(), any())).thenReturn(Future.successful(SampleData.schemeDetails))
    when(mockAFTConnector.getListOfVersions(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(versions))
    when(mockAFTConnector.getAftOverview(any(), any(), any(),  any(), any())(any(), any())).thenReturn(Future.successful(multipleVersions))
    when(mockUserAnswersCacheConnector.lockDetail(any(), any())(any(), any())).thenReturn(Future.successful(None))
    when(mockUserAnswersCacheConnector.removeAll(any())(any(), any())).thenReturn(Future.successful(Ok("")))
    when(mockAppConfig.schemeDashboardUrl(any(): IdentifierRequest[_])).thenReturn(dummyCall.url)
    when(mockFinancialStatementConnector.getSchemeFS(any(), any(), any())(any(), any()))
      .thenReturn(Future.successful(SchemeFS(seqSchemeFSDetail = Seq.empty)))  }

  "ReturnHistory Controller" must {
    "return OK and the correct view for a GET" in {
      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK


      val view = application.injector.instanceOf[ReturnHistoryView].apply(
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
        Table(head = Some(tableHead), rows = tableRows, attributes = Map("role" -> "table")),
        paymentsAndCharges = false,
        paymentsAndChargesUrl,
        startDate.getYear.toString,
        dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }

    "return OK and the correct view with payment and charges URL for a GET where there is scheme fin info" in {
      when(mockFinancialStatementConnector.getSchemeFS(any(), any(), any())(any(), any()))
        .thenReturn(Future.successful(SampleData.schemeFSResponseAftAndOTC))

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      val view = application.injector.instanceOf[ReturnHistoryView].apply(
        startDate.format(dateFormatterStartDate),
        Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
        Table(head = Some(tableHead), rows = tableRows, attributes = Map("role" -> "table")),
        paymentsAndCharges = true,
        paymentsAndChargesUrl,
        startDate.getYear.toString,
        dummyCall.url,
        schemeName
      )(httpGETRequest(httpPathGET), messages)

      compareResultAndView(result, view)
    }
  }
}
