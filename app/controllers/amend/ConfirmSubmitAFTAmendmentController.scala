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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.ConfirmSubmitAFTReturnFormProvider
import helpers.AmendmentHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, Draft, NormalMode, Quarters, UserAnswers, ValueChangeType}
import navigators.CompoundNavigator
import pages.{ConfirmSubmitAFTAmendmentPage, ConfirmSubmitAFTAmendmentValueChangeTypePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsObject
import play.api.mvc._
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.ConfirmSubmitAFTAmendmentView


class ConfirmSubmitAFTAmendmentController @Inject()(override val messagesApi: MessagesApi,
                                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                                    navigator: CompoundNavigator,
                                                    identify: IdentifierAction,
                                                    getData: DataRetrievalAction,
                                                    allowAccess: AllowAccessActionProvider,
                                                    requireData: DataRequiredAction,
                                                    formProvider: ConfirmSubmitAFTReturnFormProvider,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    config: FrontendAppConfig,
                                                    aftConnector: AFTConnector,
                                                    amendmentHelper: AmendmentHelper,
                                                    confirmSubmitAFTAmendmentView: ConfirmSubmitAFTAmendmentView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val preparedForm = request.userAnswers.get(ConfirmSubmitAFTAmendmentPage) match {
        case None        => form
        case Some(value) => form.fill(value)
      }

      populateView(srn, startDate, request.userAnswers, preparedForm, Results.Status(OK), accessType, version)
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen
      requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            populateView(srn, startDate, request.userAnswers, formWithErrors, Results.Status(BAD_REQUEST), accessType, version)
          },
          value =>
            if (!value) {
              userAnswersCacheConnector.removeAll(request.internalId).map { _ => Redirect(config.schemeDashboardUrl(request).format(srn))
              }
            } else {
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ConfirmSubmitAFTAmendmentPage, value))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ConfirmSubmitAFTAmendmentPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
    }

  private def populateView(srn: String,
                           startDate: LocalDate,
                           ua: UserAnswers,
                           form: Form[Boolean],
                           result: Results.Status,
                           accessType: AccessType,
                           version: Int)(implicit request: DataRequest[AnyContent]): Future[Result] = {
    DataRetrievals.retrieveSchemeWithPSTR { (schemeName, pstr) =>
      val amendedVersion = request.aftVersion
      val previousVersion = amendedVersion - 1

      aftConnector.getAftOverview(pstr, srn, request.isLoggedInAsPsa, Some(localDateToString(startDate)),
        Some(Quarters.getQuarter(startDate).endDate)).flatMap { seqOverview =>
        val isCompilable = seqOverview.filter(_.versionDetails.isDefined).map(_.toPodsReport).exists(_.compiledVersionAvailable)

        if (accessType == Draft && !isCompilable) {
          Future.successful(Redirect(Call("GET", config.schemeDashboardUrl(request).format(srn))))
        } else {
          aftConnector.getAFTDetails(pstr, startDate, aftVersion = s"$previousVersion", srn, request.isLoggedInAsPsa).flatMap { previousVersionJsValue =>
            val (currentTotalAmountUK, currentTotalAmountNonUK) = amendmentHelper.getTotalAmount(ua)
            val (previousTotalAmountUK, previousTotalAmountNonUK) = amendmentHelper.getTotalAmount(UserAnswers(previousVersionJsValue.as[JsObject]))

           val updatedUA = ua.setOrException(ConfirmSubmitAFTAmendmentValueChangeTypePage, ValueChangeType.valueChangeType(
              currentTotalAmountNonUK + currentTotalAmountUK,
              previousTotalAmountNonUK + previousTotalAmountUK
            ))
            userAnswersCacheConnector.savePartial(request.internalId, updatedUA.data).
            flatMap{ _ =>

              val tableRowsUK: Seq[SummaryListRow] = amendmentHelper.amendmentSummaryRows(
                currentTotalAmountUK, previousTotalAmountUK, previousVersion)

              val tableRowsNonUK: Seq[SummaryListRow] = amendmentHelper.amendmentSummaryRows(
                currentTotalAmountNonUK, previousTotalAmountNonUK, previousVersion)

              Future.successful(result(confirmSubmitAFTAmendmentView(
                tableRowsUK,
                tableRowsNonUK,
                form,
                TwirlRadios.yesNo(form("value")),
                routes.ConfirmSubmitAFTAmendmentController.onSubmit(srn, startDate, accessType, version),
                config.schemeDashboardUrl(request).format(srn),
                schemeName
              )))
            }
          }
        }
      }
    }
  }
}
