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
import forms.YearsFormProvider
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.financialOverview.psa.{PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration
import views.html.financialOverview.psa.SelectYearView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesYearController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: YearsFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              appConfig: FrontendAppConfig,
                                              selectYearView: SelectYearView,
                                              psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                              navService: PenaltiesNavigationService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
  with I18nSupport {

  private def form(errorParameter: String)(implicit messages: Messages, ev: Enumerable[Year]): Form[Year] =
    formProvider(messages("selectPenaltiesYear.error", messages(errorParameter)))(implicitly)

  def onPageLoad(penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>

    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

      val typeParam = psaPenaltiesAndChargesService.getTypeParam(penaltyType)
      val years = getYears(penaltyType, penaltiesCache.penalties.toSeq)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      Future.successful(Ok(selectYearView(
        form(typeParam),
        routes.SelectPenaltiesYearController.onSubmit(penaltyType),
        penaltiesCache.psaName,
        appConfig.managePensionsSchemeOverviewUrl,
        TwirlMigration.toTwirlRadiosWithHintText(FSYears.radios(form(typeParam), years)
        )
      )))
    }
  }

  def onSubmit(penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async { implicit request =>

    val navMethod: (Seq[PsaFSDetail], Int) => Future[Result] = {
      penaltyType match {
        case AccountingForTaxPenalties => aftNavMethod(request.psaIdOrException.id)
        case EventReportingCharges => erNavMethod(request.psaIdOrException.id)
        case _ => nonAftNavMethod(penaltyType)
      }
    }
    val typeParam = psaPenaltiesAndChargesService.getTypeParam(penaltyType)

    psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val years = getYears(penaltyType, penaltiesCache.penalties.toSeq)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      form(typeParam).bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(selectYearView(
            formWithErrors,
            routes.SelectPenaltiesYearController.onSubmit(penaltyType),
            penaltiesCache.psaName,
            appConfig.managePensionsSchemeOverviewUrl,
            TwirlMigration.toTwirlRadiosWithHintText(FSYears.radios(formWithErrors, years)
            )
          )))
        },
        value => navMethod(penaltiesCache.penalties.toSeq, value.year)
      )
    }
  }

  private def getYears(penaltyType: PenaltyType, penalties: Seq[PsaFSDetail]): Seq[DisplayYear] = {
    val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == penaltyType)

    filteredPenalties
      .map(_.periodEndDate.getYear).distinct.sorted.reverse
      .map { year =>
        val hint = if (filteredPenalties.filter(_.periodEndDate.getYear == year).exists(psaPenaltiesAndChargesService.isPaymentOverdue)) {
          Some(PaymentOverdue)
        } else {
          None
        }
        DisplayYear(year, hint)
      }
  }

  private def erNavMethod(psaId: String): (Seq[PsaFSDetail], Int) => Future[Result] =
    (penalties, year) => {
      val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == EventReportingCharges)
      navService.navFromERYearsPage(filteredPenalties, year, psaId, EventReportingCharges)
    }
  private def aftNavMethod(psaId: String)
                          (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail], Int) => Future[Result] =
    (penalties, year) => {
      val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      navService.navFromAFTYearsPage(filteredPenalties, year, psaId, AccountingForTaxPenalties)
    }

  private def nonAftNavMethod(penaltyType: PenaltyType)
                             (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail], Int) => Future[Result] = {
    (penalties, year) => navService.navFromNonAftYearsPage(penalties, year, request.idOrException, penaltyType)
  }


}
