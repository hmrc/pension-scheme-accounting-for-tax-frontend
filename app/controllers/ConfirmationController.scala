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

package controllers

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import controllers.financialStatement.paymentsAndCharges.routes._
import controllers.routes.SignOutController
import models.ChargeDetailsFilter.All
import models.LocalDateBinder._
import models.ValueChangeType.{ChangeTypeDecrease, ChangeTypeIncrease, ChangeTypeSame}
import models.financialStatement.PaymentOrChargeType.AccountingForTaxCharges
import models.requests.DataRequest
import models.viewModels.ConfirmationViewModel
import models.AccessType
import pages.ConfirmSubmitAFTAmendmentValueChangeTypePage
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.SchemeService
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.{HtmlContent, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.{Key, SummaryListRow, Value}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.formatSubmittedDate

import java.time.{LocalDate, ZoneId, ZonedDateTime}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.ConfirmationView
import views.html.ConfirmationAmendDecreaseView
import views.html.ConfirmationAmendIncreaseView
import views.html.ConfirmationNoChargeView

class ConfirmationController @Inject()(
                                        override val messagesApi: MessagesApi,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        requireData: DataRequiredAction,
                                        allowAccess: AllowAccessActionProvider,
                                        allowSubmission: AllowSubmissionAction,
                                        val controllerComponents: MessagesControllerComponents,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        confirmationView: ConfirmationView,
                                        confirmationAmendDecreaseView: ConfirmationAmendDecreaseView,
                                        confirmationAmendIncreaseView: ConfirmationAmendIncreaseView,
                                        confirmationNoChargeView: ConfirmationNoChargeView,
                                        config: FrontendAppConfig,
                                        fsConnector: FinancialStatementConnector,
                                        schemeService: SchemeService
                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private val logger = Logger(classOf[ConfirmationController])

  private def checkIfFinancialInfoLinkDisplayable(srn: String, startDate: LocalDate)
                                                 (implicit request: DataRequest[AnyContent]): Future[Boolean] = {
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn
    ) flatMap { schemeDetails =>
      fsConnector.getSchemeFS(schemeDetails.pstr, srn, request.isLoggedInAsPsa).map(_.seqSchemeFSDetail.exists(_.periodStartDate.contains(startDate)))
    } recover { case e =>
      logger.error("Exception (not rendered to user) when checking for financial information", e)
      false
    }

  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType) andThen allowSubmission).async {
      implicit request =>
        DataRetrievals.retrievePSAAndSchemeDetailsWithAmendmentNoPSTR {
          (schemeName, email, quarterStartDate, quarterEndDate, isAmendment, amendedVersion) =>
            val submittedDate = formatSubmittedDate(ZonedDateTime.now(ZoneId.of("Europe/London")))
            val rows = getRows(
              schemeName = schemeName,
              quarterStartDate = quarterStartDate,
              quarterEndDate = quarterEndDate,
              submittedDate = submittedDate,
              amendedVersion = if (isAmendment) Some(amendedVersion) else None
            )

            checkIfFinancialInfoLinkDisplayable(srn, startDate).flatMap { isFinancialInfoLinkDisplayable =>
              val optViewPaymentsUrl =
                if (isFinancialInfoLinkDisplayable) {
                  Some(PaymentsAndChargesController.onPageLoad(srn, startDate, AccountingForTaxCharges, All).url)
                } else {
                  None
                }

              val panelH1 = if (isAmendment) Messages("confirmation.aft.amendment.panel.h1") else Messages("confirmation.aft.return.panel.h1")

              val viewModel = ConfirmationViewModel(
                  panelH1,
                  confirmationPanelText,
                  email,
                  rows,
                  optViewPaymentsUrl,
                  controllers.routes.AFTOverviewController.onPageLoad(srn).url,
                  schemeName,
                  SignOutController.signOut(Some(srn), Some(localDateToString(startDate))).url
              )

              userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
                (request.isAmendment, request.userAnswers.get(ConfirmSubmitAFTAmendmentValueChangeTypePage)) match {
                  case (true, Some(ChangeTypeDecrease)) => Ok(confirmationAmendDecreaseView(viewModel))
                  case (true, Some(ChangeTypeIncrease)) => Ok(confirmationAmendIncreaseView(viewModel))
                  case (true, Some(ChangeTypeSame)) => Ok(confirmationNoChargeView(viewModel))
                  case _ => Ok(confirmationView(viewModel))
                }
              }
            }
        }
    }

  private[controllers] def getRows(schemeName: String, quarterStartDate: String, quarterEndDate: String,
                                   submittedDate: String, amendedVersion: Option[Int])(implicit messages: Messages): Seq[SummaryListRow] = {
    Seq(SummaryListRow(
      key = Key(Text(messages("confirmation.table.scheme.label")), classes = "govuk-!-font-weight-regular"),
      value = Value(Text(schemeName))
    ),
      SummaryListRow(
        key = Key(Text(messages("confirmation.table.accounting.period.label")), classes = "govuk-!-font-weight-regular"),
        value = Value(Text(messages("confirmation.table.accounting.period.value", quarterStartDate, quarterEndDate)))
      ),
      SummaryListRow(
        key = Key(Text(messages("confirmation.table.data.submitted.label")), classes = "govuk-!-font-weight-regular"),
        value = Value(Text(submittedDate))
      )
    ) ++ amendedVersion.map { vn =>
      Seq(
        SummaryListRow(
          key = Key(Text(messages("confirmation.table.submission.number.label")), classes = "govuk-!-font-weight-regular"),
          value = Value(Text(s"$vn"))
        )
      )
    }.getOrElse(Nil)
  }

  private def confirmationPanelText(implicit messages: Messages): HtmlContent = {
    HtmlContent(s"""<span class="heading-large govuk-!-font-weight-bold">${messages("confirmation.aft.return.panel.text")}</span>""")
  }

}
