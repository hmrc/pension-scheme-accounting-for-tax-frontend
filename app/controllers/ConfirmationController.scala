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

package controllers

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.GenericViewModel
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.ExecutionContext
import models.LocalDateBinder._

class ConfirmationController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    requireData: DataRequiredAction,
    allowAccess: AllowAccessActionProvider,
    allowSubmission: AllowSubmissionAction,
    val controllerComponents: MessagesControllerComponents,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    renderer: Renderer,
    config: FrontendAppConfig
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen allowSubmission andThen requireData).async {
      implicit request =>
        DataRetrievals.retrieveSchemeNameWithPSTRAndQuarter { (schemeName, pstr, quarter) =>
          val quarterStartDate = quarter.startDate.format(DateTimeFormatter.ofPattern("d MMMM"))
          val quarterEndDate = quarter.endDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
          val submittedDate = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mm a").format(LocalDateTime.now())
          val listSchemesUrl = config.yourPensionSchemesUrl
          val html = confirmationPanelText(submittedDate, schemeName, pstr)

          val json = Json.obj(
            fields = "srn" -> srn,
            "startDate" -> Some(startDate),
            "pstr" -> pstr,
            "dataHtml" -> html.toString(),
            "pensionSchemesUrl" -> listSchemesUrl,
            "quarterStartDate" -> quarterStartDate,
            "quarterEndDate" -> quarterEndDate,
            "submittedDate" -> submittedDate,
            "viewModel" -> GenericViewModel(
              submitUrl = controllers.routes.SignOutController.signOut(srn, Some(startDate)).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName
            )
          )
          renderer.render("confirmation.njk", json).flatMap { viewHtml =>
            userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
              Ok(viewHtml)
            }
          }
        }
    }

  private def confirmationPanelText(submittedDate: String, schemeName: String, pstr: String)(implicit messages: Messages): Html = {
    def pTag(text: String, classes: Option[String] = None): String = {
      s"""<p class="govuk-!-font-size-19 ${classes.getOrElse("")}">$text</p>"""
    }
    def span(text: String): String = {
      s"${Html(s"""<span class="govuk-!-font-weight-bold">$text</span>""").toString()}"
    }
    Html(
      pTag(messages("confirmation.aft.date.submitted", span(submittedDate)), classes = Some("govuk-!-margin-bottom-7")) +
        pTag(schemeName, classes = Some("govuk-!-font-weight-bold")) +
        pTag(messages("confirmation.aft.pstr", span(pstr)))
    )
  }
}
