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

import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.AccessType
import models.GenericViewModel
import models.LocalDateBinder._
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.twirl.api.Html
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.Key
import uk.gov.hmrc.viewmodels.SummaryList.Row
import uk.gov.hmrc.viewmodels.SummaryList.Value
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.SummaryList
import uk.gov.hmrc.viewmodels._
import utils.DateHelper.dateFormatterDMY
import utils.DateHelper.dateFormatterStartDate
import utils.DateHelper.dateFormatterSubmittedDate

import scala.concurrent.ExecutionContext

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

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission).async {
      implicit request =>
        DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment { (schemeName, _, email, quarter, isAmendment, amendedVersion) =>
          val quarterStartDate = quarter.startDate.format(dateFormatterStartDate)
          val quarterEndDate = quarter.endDate.format(dateFormatterDMY)

          val submittedDate = dateFormatterSubmittedDate.format(ZonedDateTime.now(ZoneId.of("Europe/London")))
          val listSchemesUrl = config.yourPensionSchemesUrl

          val rows = getRows(schemeName, quarterStartDate, quarterEndDate, submittedDate, if(isAmendment) Some(amendedVersion) else None)

          val json = Json.obj(
            fields = "srn" -> srn,
            "panelHtml" -> confirmationPanelText.toString(),
            "email" -> email,
            "isAmendment" -> isAmendment,
            "list" -> rows,
            "pensionSchemesUrl" -> listSchemesUrl,
            "viewModel" -> GenericViewModel(
              submitUrl = controllers.routes.SignOutController.signOut(srn, Some(startDate)).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
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

  private[controllers] def getRows(schemeName: String, quarterStartDate: String, quarterEndDate: String,
                                   submittedDate: String, amendedVersion: Option[Int]): Seq[SummaryList.Row] = {
    Seq(Row(
      key = Key(msg"confirmation.table.scheme.label", classes = Seq("govuk-!-font-weight-regular")),
      value = Value(Literal(schemeName), classes = Nil),
      actions = Nil
    ),
      Row(
        key = Key(msg"confirmation.table.accounting.period.label", classes = Seq("govuk-!-font-weight-regular")),
        value = Value(msg"confirmation.table.accounting.period.value".withArgs(quarterStartDate, quarterEndDate), classes = Nil),
        actions = Nil
      ),
      Row(
        key = Key(msg"confirmation.table.data.submitted.label", classes = Seq("govuk-!-font-weight-regular")),
        value = Value(Literal(submittedDate), classes = Nil),
        actions = Nil
      )
    ) ++ amendedVersion.map{ vn =>
      Seq(
        Row(
          key = Key(msg"confirmation.table.submission.number.label", classes = Seq("govuk-!-font-weight-regular")),
          value = Value(Literal(s"$vn"), classes = Nil),
          actions = Nil
        )
      )
    }.getOrElse(Nil)
  }

  private def confirmationPanelText(implicit messages: Messages): Html = {
    Html(s"${Html(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>""").toString()}")
  }
}
