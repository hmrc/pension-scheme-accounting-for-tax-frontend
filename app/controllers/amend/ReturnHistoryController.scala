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
import models.{AFTVersion, Quarters}
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
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      aftConnector.getListOfVersions(schemeDetails.pstr, startDate).flatMap { versions =>
        val internalId = s"$srn$startDate"
        userAnswersCacheConnector.removeAll(internalId).flatMap { _ =>

          def url: Option[String] => Call = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, _)

          tableOfVersions(srn, versions.sortBy(_.reportVersion).reverse, url).flatMap { table =>

            val json = Json.obj(
              fields = "srn" -> srn,
              "startDate" -> Some(startDate),
              "quarterStart" -> startDate.format(dateFormatterStartDate),
              "quarterEnd" -> Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
              "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn),
              "schemeName" -> schemeDetails.schemeName
            ) ++ table

            renderer.render("amend/returnHistory.njk", json).map(Ok(_))
          }
        }
      }
    }
  }

  private def tableOfVersions(srn: String, versions: Seq[AFTVersion], url: Option[String] => Call
                                )(implicit messages: Messages, ec: ExecutionContext, hc: HeaderCarrier): Future[JsObject] = {
    if (versions.nonEmpty) {
    val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy")

    def link(data: AFTVersion, linkText: String)(implicit messages: Messages): Html = {
      Html(
        s"<a id= report-version-${data.reportVersion} href=${url(Some(data.reportVersion.toString))}> ${messages(linkText)}" +
          s"<span class=govuk-visually-hidden>${messages(s"returnHistory.visuallyHidden", data.reportVersion.toString)}</span> </a>")
    }

    val head = Seq(
      Cell(msg"returnHistory.version", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"returnHistory.status", classes = Seq("govuk-!-width-one-quarter")),
      Cell(msg"")
    )

      def versionCell(reportVersion:Int, reportStatus:String):Cell = {
        val version = reportStatus.toLowerCase match {
          case "compiled" => msg"returnHistory.versionDraft"
          case _ => Literal(reportVersion.toString)
        }
        Cell(version, classes = Seq("govuk-!-width-one-quarter"))
      }

    def statusCell(date:String, reportStatus:String):Cell = {
      val status = reportStatus match {
        case "Compiled" => msg"returnHistory.compiledStatus"
        case _ => msg"returnHistory.submittedOn".withArgs(date)
      }

      Cell(status, classes = Seq("govuk-!-width-one-quarter"))
    }

    val tableRows = versions.zipWithIndex.map { data =>
      val (version, index) = data

      getLinkText(index, srn, version.date).map { linkText =>
        Seq(
          versionCell(version.reportVersion, version.reportStatus),
          statusCell(version.date.format(dateFormatter), version.reportStatus),
          Cell(link(version, linkText), classes = Seq("govuk-!-width-one-quarter"))
        )
      }
    }

    Future.sequence(tableRows).map { rows =>
      Json.obj("versions" -> Table(head = head, rows = rows))
    }
  } else {
      Future.successful(Json.obj())
    }
  }

  private def getLinkText(index: Int, srn: String, date: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[String] = {
    if (index == 0) {
      userAnswersCacheConnector.lockedBy(srn, date).map {
        case Some(_) => "site.view"
        case _ => "site.viewOrChange"
      }
    } else {
      Future.successful("site.view")
    }
  }

}
