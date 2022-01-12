/*
 * Copyright 2022 HM Revenue & Customs
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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, FinancialStatementConnector, SchemeDetailsConnector}
import controllers.actions.IdentifierAction
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.SubmitterType.PSA
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.requests.IdentifierRequest
import models.{AFTOverview, AFTVersion, AccessType, Draft, LockDetail, Quarters, Submission, SubmitterDetails, VersionsWithSubmitter}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Content, Html, NunjucksSupport, Text}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

class ReturnHistoryController @Inject()(
                                         schemeService: SchemeService,
                                         aftConnector: AFTConnector,
                                         userAnswersCacheConnector: UserAnswersCacheConnector,
                                         financialStatementConnector: FinancialStatementConnector,
                                         schemeDetailsConnector: SchemeDetailsConnector,
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
      seqAFTOverview <- aftConnector.getAftOverview(schemeDetails.pstr, Some(localDateToString(startDate)), Some(endDate))
      versions <- aftConnector.getListOfVersions(schemeDetails.pstr, startDate)
      _ <- userAnswersCacheConnector.removeAll(internalId)
      table <- tableOfVersions(srn, versions.sortBy(_.versionDetails.reportVersion).reverse, startDate, seqAFTOverview)
    } yield {

      val paymentJson = if (schemeFs.isEmpty) {
        Json.obj()
      }
      else {
        Json.obj("paymentsAndChargesUrl" ->
          controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, startDate, AccountingForTaxCharges, All).url)
      }

      Json.obj(
        fields = "srn" -> srn,
        "startDate" -> Some(localDateToString(startDate)),
        "quarterStart" -> startDate.format(dateFormatterStartDate),
        "quarterEnd" -> Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
        "returnUrl" -> config.schemeDashboardUrl(request).format(srn),
        "schemeName" -> schemeDetails.schemeName,
        "startYear"-> startDate.getYear.toString
      ) ++ table ++ paymentJson
    }
    json.flatMap(renderer.render("amend/returnHistory.njk", _).map(Ok(_)))
  }

  private def tableOfVersions(srn: String, versions: Seq[VersionsWithSubmitter], startDate: String, seqAftOverview: Seq[AFTOverview])
                             (implicit request: IdentifierRequest[AnyContent],
                              ec: ExecutionContext,
                              hc: HeaderCarrier): Future[JsObject] = {
    if (versions.nonEmpty) {
      val isCompileAvailable: Option[Boolean] = seqAftOverview
        .filter(_.versionDetails.isDefined)
        .map(_.toPodsReport)
        .find(_.periodStartDate == stringToLocalDate(startDate))
        .map(_.compiledVersionAvailable)

      def url: (AccessType, Int) => Call = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, _, _)

      val tableRows: Seq[Future[Seq[Cell]]] = versions.zipWithIndex.map { data =>
        val (version, index) = data
        val accessType = if (index == 0) Draft else Submission

        for {
          optionLockDetail <- userAnswersCacheConnector.lockDetail(srn, version.versionDetails.date)
          displayDetails <- getDisplayDetails(index, version.versionDetails, optionLockDetail, srn)
          submitter <- submittedBy(index, version.versionDetails, version.submitterDetails, srn)
        } yield
          Seq(
            Cell(displayDetails.version, classes = Seq("govuk-!-width-one-quarter")),
            Cell(displayDetails.status, classes = Seq("govuk-!-width-one-half")),
            Cell(submitter, classes = Seq("govuk-!-width-one-quarter")),
            Cell(link(version.versionDetails.reportVersion, displayDetails.linkText, accessType, index, isCompileAvailable, url),
              classes = Seq("govuk-!-width-one-quarter"), attributes = Map("role" -> "cell"))
          )
      }

      Future.sequence(tableRows).map { rows =>

        val displaySubmittedByColumn: Boolean =
          rows.exists(x => !(x(2).content == visuallyHidden("draft") || x(2).content == visuallyHidden("notAuthorised")))

        val head: Seq[Cell] = if(displaySubmittedByColumn) headCells else dropThirdCell(headCells)
        val tableRows: Seq[Seq[Cell]] = if(displaySubmittedByColumn) rows else rows.map(row => dropThirdCell(row))

        Json.obj("versions" -> Table(head = head,
          rows = tableRows,
          attributes = Map("role" -> "table")))
      }
    } else {
      Future.successful(Json.obj())
    }
  }

  val dropThirdCell: Seq[Cell] => Seq[Cell] = seq => seq.zipWithIndex collect { case (x, i) if i!=2 => x}

  private def link(version: Int, linkText: String, accessType: AccessType, index: Int,
                   isCompileAvailable: Option[Boolean], url: (AccessType, Int) => Call)(implicit messages: Messages): Html = {

    val updatedVersion = if (index == 0 && isCompileAvailable.contains(false)) version + 1 else version

    Html(
      s"<a id= report-version-$version class=govuk-link href=${url(accessType, updatedVersion)}>" +
        s"<span aria-hidden=true>${messages(linkText)}</span>" +
        s"<span class=govuk-visually-hidden> ${messages(linkText)} " +
        s"${messages(s"returnHistory.visuallyHidden", version.toString)}</span></a>"
    )
  }

  private def headCells(implicit messages: Messages): Seq[Cell] = Seq(
    Cell(msg"returnHistory.version", classes = Seq("govuk-!-width-one-quarter")),
    Cell(msg"returnHistory.status", classes = Seq("govuk-!-width-one-half")),
    Cell(msg"returnHistory.submittedBy", classes = Seq("govuk-!-width-one-quarter")),
    Cell(Html(s"""<span class=govuk-visually-hidden>${messages("site.action")}</span>"""))
  )

  private def getDisplayDetails(index: Int, aftVersion: AFTVersion, optionLockDetail: Option[LockDetail], srn: String)
                               (implicit request: IdentifierRequest[AnyContent]): Future[DisplayDetails] = {
    if (index == 0) {
      (optionLockDetail, aftVersion.reportStatus) match {

        case (Some(lockedBy), _) =>
          getLockedBy(lockedBy, request.idOrException, srn).map { nameOpt =>
            DisplayDetails(
              msg"returnHistory.versionDraft",
              nameOpt.fold(msg"returnHistory.locked")(name => msg"returnHistory.lockedBy".withArgs(name)),
              "site.view")
          }
        case (_, "Compiled") => Future(DisplayDetails(
          msg"returnHistory.versionDraft",
          msg"returnHistory.compiledStatus",
          "site.change"))

        case _ => Future(DisplayDetails(
          Literal(aftVersion.reportVersion.toString),
          msg"returnHistory.submittedOn".withArgs(aftVersion.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))),
          "site.viewOrChange"))
      }
    } else {
      Future(DisplayDetails(
        Literal(aftVersion.reportVersion.toString),
        msg"returnHistory.submittedOn".withArgs(aftVersion.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))),
        "site.view"))
    }
  }

  private val psaIdRegex: Regex = "^A[0-9]{7}$".r
  private val pspIdRegex: Regex = "^[0-9]{8}$".r

  private def submittedBy(index: Int, aftVersion: AFTVersion, submitterDetails: Option[SubmitterDetails], srn: String)
                         (implicit request: IdentifierRequest[AnyContent], hc: HeaderCarrier): Future[Content] =
  if((index == 0 && aftVersion.reportStatus.equalsIgnoreCase("Compiled")) || submitterDetails.isEmpty){
    Future(visuallyHidden("draft"))
  } else {
    val submitter = submitterDetails.head
    request.idOrException match {
      case psaIdRegex(_*) => Future(Literal(submitter.submitterName))

      case pspIdRegex(_*) if submitter.submitterType == PSA =>

        schemeDetailsConnector.getPspSchemeDetails(request.idOrException, srn).map { schemeDetails =>
          if (schemeDetails.authorisingPSAID.contains(submitter.submitterID)) {
            Literal(submitter.submitterName)
          } else {
            visuallyHidden("notAuthorised")
          }
        }

      case pspIdRegex(_*) if submitter.submitterID == request.idOrException =>
        Future(Literal(submitter.submitterName))

      case _ => Future(visuallyHidden("notAuthorised"))

    }
  }

  def visuallyHidden(messageType: String)(implicit messages: Messages): Html =
    Html(s"<span class=govuk-visually-hidden>${messages(s"returnHistory.$messageType.visuallyHiddenText")}</span>")

  private def getLockedBy(lockedBy: LockDetail, loggedInId: String, srn: String)
                         (implicit hc: HeaderCarrier): Future[Option[String]] = {
    loggedInId match {
      case psaIdRegex(_*) => Future(Some(lockedBy.name))
      case pspIdRegex(_*) if lockedBy.psaOrPspId.matches(psaIdRegex.toString()) =>
        schemeDetailsConnector.getPspSchemeDetails(loggedInId, srn).map { schemeDetails =>
          if (schemeDetails.authorisingPSAID.contains(lockedBy.psaOrPspId)) {
            Some(lockedBy.name)
          } else {
            None
          }
        }
      case _ => Future(None)
    }
  }

}

case class DisplayDetails(version: Text, status: Text, linkText: String)