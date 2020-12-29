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
import java.time.format.DateTimeFormatter

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, FinancialStatementConnector}
import controllers.actions.IdentifierAction
import javax.inject.Inject
import models.LocalDateBinder._
import models.{AFTOverview, AFTVersion, AccessType, Draft, Quarters, Submission}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.{ExecutionContext, Future}

class ReturnHistoryController @Inject()(
                                         schemeService: SchemeService,
                                         aftConnector: AFTConnector,
                                         userAnswersCacheConnector: UserAnswersCacheConnector,
                                         financialStatementConnector: FinancialStatementConnector,
                                         override val messagesApi: MessagesApi,
                                         identify: IdentifierAction,
                                         val controllerComponents: MessagesControllerComponents,
                                         renderer: Renderer,
                                         config: FrontendAppConfig
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = identify.async { implicit request =>
    val endDate = Quarters.getQuarter(startDate).endDate
    val internalId = s"$srn$startDate"

    val json = for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn")
      schemeFs <- financialStatementConnector.getSchemeFS(schemeDetails.pstr)
      seqAFTOverview <- aftConnector.getAftOverview(schemeDetails.pstr, Some(startDate), Some(endDate))
      versions <- aftConnector.getListOfVersions(schemeDetails.pstr, startDate)
      _ <- userAnswersCacheConnector.removeAll(internalId)
      table <- tableOfVersions(srn, versions.sortBy(_.reportVersion).reverse, startDate, seqAFTOverview)
    } yield {
      val paymentJson = if (schemeFs.isEmpty) Json.obj()
      else
        Json.obj("paymentsAndChargesUrl" -> controllers.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, startDate.getYear).url)
      Json.obj(
        fields = "srn" -> srn,
        "startDate" -> Some(startDate),
        "quarterStart" -> startDate.format(dateFormatterStartDate),
        "quarterEnd" -> Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn),
        "schemeName" -> schemeDetails.schemeName
      ) ++ table ++ paymentJson
    }
    json.flatMap(renderer.render("amend/returnHistory.njk", _).map(Ok(_)))
  }

  private def tableOfVersions(srn: String, versions: Seq[AFTVersion], startDate: String, seqAftOverview: Seq[AFTOverview])
                             (implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[JsObject] = {
    if (versions.nonEmpty) {
      val isCompileAvailable = seqAftOverview.find(_.periodStartDate == stringToLocalDate(startDate)).map(_.compiledVersionAvailable)
      val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

      def url: (AccessType, Int) => Call = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, _, _)

      def link(version: Int, linkText: String, accessType: AccessType, index: Int)(implicit messages: Messages): Html = {
        val updatedVersion = if (index == 0 && isCompileAvailable.contains(false)) version + 1 else version
        Html(
          s"<a id= report-version-$version class=govuk-link href=${url(accessType, updatedVersion)}>" +
            s"<span aria-hidden=true>${messages(linkText)}</span>" +
            s"<span class=govuk-visually-hidden> ${messages(linkText)} " +
            s"${messages(s"returnHistory.visuallyHidden", version.toString)}</span></a>"
        )
      }

      val head = Seq(
        Cell(msg"returnHistory.version", classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"returnHistory.status", classes = Seq("govuk-!-width-one-half")),
        Cell(Html(s"""<span class=govuk-visually-hidden>${messages("site.action")}</span>"""))
      )

      def versionCell(reportVersion: Int, reportStatus: String): Cell = {
        val version = reportStatus.toLowerCase match {
          case "compiled" => msg"returnHistory.versionDraft"
          case _ => Literal(reportVersion.toString)
        }
        Cell(version, classes = Seq("govuk-!-width-one-quarter"))
      }

      def statusCell(date: String, reportStatus: String): Cell = {
        val status = reportStatus match {
          case "Compiled" => msg"returnHistory.compiledStatus"
          case _ => msg"returnHistory.submittedOn".withArgs(date)
        }
        Cell(status, classes = Seq("govuk-!-width-one-half"))
      }

      val tableRows = versions.zipWithIndex.map { data =>
        val (version, index) = data
        val accessType = if (index == 0) Draft else Submission

        getLinkText(index, srn, version.date, version.reportStatus).map { linkText =>
          Seq(
            versionCell(version.reportVersion, version.reportStatus),
            statusCell(version.date.format(dateFormatter), version.reportStatus),
            Cell(link(version.reportVersion, linkText, accessType, index), classes = Seq("govuk-!-width-one-quarter"), attributes = Map("role" -> "cell"))
          )
        }
      }

      Future.sequence(tableRows).map { rows =>
        Json.obj("versions" -> Table(head = head,
          rows = rows,
          attributes = Map("role" -> "table")))
      }
    } else {
      Future.successful(Json.obj())
    }
  }

  private def getLinkText(index: Int, srn: String, date: String, reportStatus: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[String] = {
    if (index == 0) {
      userAnswersCacheConnector.lockDetail(srn, date).map { optionLockDetail =>
        (optionLockDetail, reportStatus) match {
          case (Some(_), _) => "site.view"
          case (_, "Compiled") => "site.change"
          case _ => "site.viewOrChange"
        }
      }
    } else {
      Future.successful("site.view")
    }
  }

}
