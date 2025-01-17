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

package controllers.financialStatement.penalties

import config.FrontendAppConfig
import controllers.actions._
import forms.QuartersFormProvider
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSDetail
import models.{AFTQuarter, DisplayHint, DisplayQuarter, PaymentOverdue, PenaltiesFilter, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.SelectQuarterView

class SelectPenaltiesQuarterController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  selectQuarterView: SelectQuarterView,
                                                  penaltiesService: PenaltiesService,
                                                  config: FrontendAppConfig
                                                )
                                                (implicit ec: ExecutionContext)
                                                  extends FrontendBaseController
                                                  with I18nSupport {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("selectPenaltiesQuarter.error"), quarters)

  def onPageLoad(year: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>


    penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt))

        if (quarters.nonEmpty) {
          Future.successful(Ok(selectQuarterView(
            form(quarters),
            year,
            TwirlMigration.toTwirlRadiosWithHintText(Quarters.radios(form(quarters),
              getDisplayQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt)),
              Seq("govuk-tag govuk-tag--red govuk-!-display-inline-block"),
              areLabelsBold = false)),
            routes.SelectPenaltiesQuarterController.onSubmit(year, journeyType),
            config.managePensionsSchemeOverviewUrl,
            penaltiesCache.psaName
          )))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }

    }
  }

  def onSubmit(year: String, journeyType: PenaltiesFilter): Action[AnyContent] = identify.async { implicit request =>
    penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt))
        if (quarters.nonEmpty) {

          form(quarters).bindFromRequest().fold(
              formWithErrors => {
                Future.successful(BadRequest(selectQuarterView(
                  formWithErrors,
                  year,
                  TwirlMigration.toTwirlRadiosWithHintText(Quarters.radios(formWithErrors,
                    getDisplayQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt)),
                    Seq("govuk-tag govuk-!-display-inline govuk-tag--red"),
                    areLabelsBold = false)),
                  routes.SelectPenaltiesQuarterController.onSubmit(year, journeyType),
                  config.managePensionsSchemeOverviewUrl,
                  penaltiesCache.psaName
                )))
              },
              value => penaltiesService.navFromAftQuartersPage(penaltiesCache.penalties, value.startDate, request.psaIdOrException.id, journeyType)
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
    }
  }

  val filteredPenalties: (Seq[PsaFSDetail], Int) => Seq[PsaFSDetail] = (penalties, year) =>
    penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodStartDate.getYear == year)

  private def getDisplayQuarters(year: String, penalties: Seq[PsaFSDetail]): Seq[DisplayQuarter] = {

    val quartersFound: Seq[LocalDate] = penalties.map(_.periodStartDate).distinct.sortBy(_.getMonth)

    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (penalties.filter(_.periodStartDate == startDate).exists(penaltiesService.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = false, None, hint)

    }
  }

  private def getQuarters(year: String, penalties: Seq[PsaFSDetail]): Seq[AFTQuarter] =
    penalties.distinct.map(penalty => Quarters.getQuarter(penalty.periodStartDate))


}
