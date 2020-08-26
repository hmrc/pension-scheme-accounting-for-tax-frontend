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
import connectors.FinancialStatementConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.AccessType
import models.GenericViewModel
import models.LocalDateBinder._
import models.ValueChangeType.ChangeTypeDecrease
import models.ValueChangeType.ChangeTypeIncrease
import models.ValueChangeType.ChangeTypeSame
import models.requests.DataRequest
import pages.ConfirmSubmitAFTAmendmentValueChangeTypePage
import play.api.Logger
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import play.twirl.api.Html
import renderer.Renderer
import services.SchemeService
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

import scala.concurrent.{Future, ExecutionContext}

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
                                        config: FrontendAppConfig,
                                        fsConnector: FinancialStatementConnector,
                                        schemeService: SchemeService
                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def checkIfFinancialInfoLinkDisplayable(srn:String, year:Int)(implicit request: DataRequest[AnyContent]):Future[Boolean] = {
    if (config.isFSEnabled) {
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        fsConnector.getSchemeFS(schemeDetails.pstr).map(_.exists(_.periodStartDate.getYear == year))
      } recover { case e => Logger.error("Exception (not rendered to user) when checking for financial information", e)
        false
      }
    } else {
      Future.successful(false)
    }
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission).async {
      implicit request =>
        val year = startDate.getYear
        DataRetrievals.retrievePSAAndSchemeDetailsWithAmendment { (schemeName, _, email, quarter, isAmendment, amendedVersion) =>
          val quarterStartDate = quarter.startDate.format(dateFormatterStartDate)
          val quarterEndDate = quarter.endDate.format(dateFormatterDMY)

          val submittedDate = dateFormatterSubmittedDate.format(ZonedDateTime.now(ZoneId.of("Europe/London")))
          val listSchemesUrl = config.yourPensionSchemesUrl

          val rows = getRows(schemeName, quarterStartDate, quarterEndDate, submittedDate, if(isAmendment) Some(amendedVersion) else None)

          checkIfFinancialInfoLinkDisplayable(srn, year).flatMap{ isFinancialInfoLinkDisplayable =>
            val optViewPaymentsUrl =
              if (isFinancialInfoLinkDisplayable) {
                Json.obj(
                "viewPaymentsUrl" -> controllers.paymentsAndCharges.routes.PaymentsAndChargesController.onPageLoad(srn, year).url
                )
              } else {
                Json.obj()
              }

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
            ) ++ optViewPaymentsUrl

            renderer.render(getView, json).flatMap { viewHtml =>
              userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
                Ok(viewHtml)
              }
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

  private def getView(implicit request: DataRequest[AnyContent]): String ={
    (request.isAmendment, request.userAnswers.get(ConfirmSubmitAFTAmendmentValueChangeTypePage)) match{
      case (true,Some(ChangeTypeDecrease)) => "confirmationAmendDecrease.njk"
      case (true,Some(ChangeTypeIncrease)) => "confirmationAmendIncrease.njk"
      case (true,Some(ChangeTypeSame)) => "confirmationNoChange.njk"
      case _ => "confirmation.njk"
    }
  }

}
