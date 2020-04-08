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
import controllers.actions.{AllowAccessActionProvider, DataRetrievalAction, IdentifierAction}
import javax.inject.Inject
import models.LocalDateBinder._
import models.{AFTVersion, StartQuarters}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import viewmodels.Table
import viewmodels.Table.Cell

import scala.concurrent.ExecutionContext

class ReturnHistoryController @Inject()(
    schemeService: SchemeService,
    aftConnector: AFTConnector,
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    config: FrontendAppConfig
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate)).async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      aftConnector.getListOfVersions(schemeDetails.pstr, startDate).flatMap { versions =>
        def url: Option[String] => Call = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, _)

        val tableOfVersions =
          if (versions.nonEmpty) {
            Json.obj("versions" -> mapVersionsToTable(versions.sortBy(_.reportVersion).reverse, url))
          } else {
            Json.obj()
          }

        val json = Json.obj(
          fields = "srn" -> srn,
          "startDate" -> Some(startDate),
          "quarterStart" -> startDate.format(dateFormatterStartDate),
          "quarterEnd" -> StartQuarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
          "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
          "schemeName" -> schemeDetails.schemeName
        ) ++ tableOfVersions

        renderer.render("amend/returnHistory.njk", json).map(Ok(_))
      }
    }
  }

  private def mapVersionsToTable(versions: Seq[AFTVersion], url: Option[String] => Call)(implicit messages: Messages): Table = {

    val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

    def link(data: AFTVersion)(implicit messages: Messages): Html = {
      Html(
        s"<a id= report-version-${data.reportVersion} href=${url(Some(data.reportVersion.toString))}> ${messages("site.view")}" +
          s"<span class=govuk-visually-hidden>${messages(s"returnHistory.visuallyHidden", data.reportVersion.toString)}</span> </a>")
    }

    val head = Seq(
      Cell(msg"returnHistory.version", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"returnHistory.dateSubmitted", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    )

    val rows = versions.map { data =>
      Seq(
        Cell(msg"returnHistory.submission".withArgs(data.reportVersion), classes = Seq("govuk-!-width-one-quarter")),
        Cell(Literal(data.date.format(dateFormatter)), classes = Seq("govuk-!-width-one-quarter")),
        Cell(link(data), classes = Seq("govuk-!-width-one-quarter"))
      )
    }

    Table(head = head, rows = rows)
  }

}
