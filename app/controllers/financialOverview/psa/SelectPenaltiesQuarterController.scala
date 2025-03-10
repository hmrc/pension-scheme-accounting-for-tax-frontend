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

package controllers.financialOverview.psa

import config.FrontendAppConfig
import controllers.actions._
import forms.QuartersFormProvider
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFSDetail
import models.{AFTQuarter, ChargeDetailsFilter, DisplayHint, DisplayQuarter, PaymentOverdue, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.financialOverview.psa.{PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.financialOverview.psa.SelectQuarterView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesQuarterController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  appConfig: FrontendAppConfig,
                                                  selectQuarterView: SelectQuarterView,
                                                  psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                                  navService: PenaltiesNavigationService)
                                                (implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("selectPenaltiesQuarter.error"), quarters)

  def onPageLoad(year: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>

    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

      val quarters: Seq[AFTQuarter] = getQuarters(filteredPenalties(penaltiesCache.penalties.toSeq, year.toInt))
      if (quarters.nonEmpty) {
        Future.successful(Ok(selectQuarterView(
          form(quarters),
          routes.SelectPenaltiesQuarterController.onSubmit(year),
          penaltiesCache.psaName,
          appConfig.managePensionsSchemeOverviewUrl,
          Quarters.radios(form(quarters),
            getDisplayQuarters(filteredPenalties(penaltiesCache.penalties.toSeq, year.toInt)),
            Seq("govuk-tag govuk-tag--red govuk-!-display-inline-block")),
            year
          )))
      } else {
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    }
  }

  def onSubmit(year: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>
    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val quarters: Seq[AFTQuarter] = getQuarters(filteredPenalties(penaltiesCache.penalties.toSeq, year.toInt))
      if (quarters.nonEmpty) {
        form(quarters).bindFromRequest().fold(
          formWithErrors => {
            Future.successful(BadRequest(selectQuarterView(
              formWithErrors,
              routes.SelectPenaltiesQuarterController.onSubmit(year),
              penaltiesCache.psaName,
              appConfig.managePensionsSchemeOverviewUrl,
              Quarters.radios(formWithErrors,
                getDisplayQuarters(filteredPenalties(penaltiesCache.penalties.toSeq, year.toInt)),
                Seq("govuk-tag govuk-!-display-inline govuk-tag--red")),
              year
            )))
          },
          value => navService.navFromQuartersPage(penaltiesCache.penalties, value.startDate, request.psaIdOrException.id)
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

  private def getDisplayQuarters(penalties: Seq[PsaFSDetail]): Seq[DisplayQuarter] = {

    val quartersFound: Seq[LocalDate] = penalties.map(_.periodStartDate).distinct.sortBy(_.getMonth)

    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (penalties.filter(_.periodStartDate == startDate).exists(psaPenaltiesAndChargesService.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = false, None, hint)

    }
  }

  private def getQuarters(penalties: Seq[PsaFSDetail]): Seq[AFTQuarter] =
    penalties.distinct.map(penalty => Quarters.getQuarter(penalty.periodStartDate))

}
