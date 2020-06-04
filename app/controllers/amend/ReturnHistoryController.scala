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
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
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
import models.LocalDateBinder._

import scala.concurrent.{ExecutionContext, Future}

class ReturnHistoryController @Inject()(
    schemeService: SchemeService,
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
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

    val json = for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      seqAFTOverview <- aftConnector.getAftOverview(schemeDetails.pstr, Some(startDate), Some(endDate))
      versions <- aftConnector.getListOfVersions(schemeDetails.pstr, startDate)
      table <- tableOfVersions(srn, versions, startDate, seqAFTOverview)
    } yield {
      Json.obj(
        fields = "srn" -> srn,
        "startDate" -> Some(startDate),
        "quarterStart" -> startDate.format(dateFormatterStartDate),
        "quarterEnd" -> Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
        "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
        "schemeName" -> schemeDetails.schemeName
      ) ++ table
    }
    json.flatMap(renderer.render("amend/returnHistory.njk", _).map(Ok(_)))
  }

  private def tableOfVersions(srn: String, aftVersions: Seq[AFTVersion], startDate: String, seqAftOverview: Seq[AFTOverview])(
      implicit messages: Messages,
      ec: ExecutionContext,
      hc: HeaderCarrier): Future[JsObject] = {

    val versions = aftVersions.sortBy(_.reportVersion).reverse
    val isCompileAvailable = seqAftOverview.find(_.periodStartDate == stringToLocalDate(startDate)).map(_.compiledVersionAvailable)

    if (versions.nonEmpty) {
      val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")
      def url: (AccessType, Int) => Call = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, _, _)

      def link(data: AFTVersion, linkText: String, accessType: AccessType)(implicit messages: Messages): Html = {
        Html(
          s"<a id= report-version-${data.reportVersion} href=${url(accessType, data.reportVersion)}> ${messages(linkText)}" +
            s"<span class=govuk-visually-hidden>${messages(s"returnHistory.visuallyHidden", data.reportVersion.toString)}</span> </a>")
      }

      val head = Seq(
        Cell(msg"returnHistory.version", classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"returnHistory.dateSubmitted", classes = Seq("govuk-!-width-one-quarter")),
        Cell(msg"")
      )

      val tableRows = versions.zipWithIndex.map { data =>
        val (version, index) = data
        val accessType = if (index == 0 && isCompileAvailable.contains(true)) Draft else Submission

        getLinkText(index, srn, version.date).map { linkText =>
          Seq(
            Cell(msg"returnHistory.submission".withArgs(version.reportVersion), classes = Seq("govuk-!-width-one-quarter")),
            Cell(Literal(version.date.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
            Cell(link(version, linkText, accessType), classes = Seq("govuk-!-width-one-quarter"))
          )
        }
      }

      Future.sequence(tableRows).map { rows =>
        Json.obj("versions" -> Table(head = head, rows = rows))
      }
    } else {
      Future(Json.obj())
    }
  }

  private def getLinkText(index: Int, srn: String, date: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[String] = {
    if (index == 0) {
      userAnswersCacheConnector.lockedBy(srn, date).map {
        case Some(_) => "site.view"
        case _       => "site.viewOrChange"
      }
    } else {
      Future.successful("site.view")
    }
  }

}
