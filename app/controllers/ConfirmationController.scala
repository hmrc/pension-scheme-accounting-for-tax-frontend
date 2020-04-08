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
import models.LocalDateBinder._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{SummaryList, _}
import play.api.i18n.Messages
import play.twirl.api.Html

import scala.concurrent.{ExecutionContext, Future}

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
        DataRetrievals.retrieveSchemeNameWithEmailAndQuarter { (schemeName, email, quarter) =>
          val quarterStartDate = quarter.startDate.format(DateTimeFormatter.ofPattern("d MMMM"))
          val quarterEndDate = quarter.endDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
          val submittedDate = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mm a").format(LocalDateTime.now())
          val listSchemesUrl = config.yourPensionSchemesUrl

          val rows = getRows(schemeName, quarterStartDate, quarterEndDate, submittedDate)

          val json = Json.obj(
            fields = "srn" -> srn,
            "startDate" -> Some(startDate),
            "panelHtml" -> confirmationPanelText.toString(),
            "email" -> email,
            "list" -> rows,
            "pensionSchemesUrl" -> listSchemesUrl,
            "viewModel" -> GenericViewModel(
              submitUrl = controllers.routes.SignOutController.signOut(srn, Some(startDate)).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName
            )
          )
          renderer.render("confirmation.njk", json).flatMap { viewHtml =>
            /*userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
              Ok(viewHtml)
            }*/
            Future.successful(Ok(viewHtml))
          }
        }
    }

  private def getRows(schemeName: String, quarterStartDate: String, quarterEndDate: String, submittedDate: String): Seq[SummaryList.Row] = {
    Seq(Row(
      key = Key(msg"confirmation.table.r1.c1", classes = Seq("govuk-!-font-weight-regular")),
      value = Value(Literal(schemeName), classes = Nil),
      actions = Nil
    ),
      Row(
        key = Key(msg"confirmation.table.r2.c1", classes = Seq("govuk-!-font-weight-regular")),
        value = Value(msg"confirmation.table.r2.c2".withArgs(quarterStartDate, quarterEndDate), classes = Nil),
        actions = Nil
      ),
      Row(
        key = Key(msg"confirmation.table.r3.c1", classes = Seq("govuk-!-font-weight-regular")),
        value = Value(Literal(submittedDate), classes = Nil),
        actions = Nil
      )
    )
  }

  private def confirmationPanelText(implicit messages: Messages): Html = {
    Html(s"${Html(s"""<span class="heading-large">${messages("confirmation.aft")}</span>""").toString()}")
  }
}
