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
import forms.YearsFormProvider
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.requests.IdentifierRequest
import models.{DisplayYear, Enumerable, FSYears, PaymentOverdue, PenaltiesFilter, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.SelectYearView

class SelectPenaltiesYearController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: YearsFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              service: PenaltiesService,
                                              selectYearView: SelectYearView)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
                                                                      with I18nSupport {

  private def form(errorParameter: String)(implicit messages: Messages, ev: Enumerable[Year]): Form[Year] =
    formProvider(messages("selectPenaltiesYear.error", messages(errorParameter)))(implicitly)

  def onPageLoad(penaltyType: PenaltyType, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>

    service.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

      val typeParam = service.getTypeParam(penaltyType)
      val years = getYears(penaltyType, penaltiesCache.penalties)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      Future.successful(Ok(selectYearView(
        form(typeParam),
        typeParam,
        TwirlMigration.toTwirlRadiosWithHintText(FSYears.radios(form(typeParam), years)),
        routes.SelectPenaltiesYearController.onSubmit(penaltyType,  journeyType),
        config.managePensionsSchemeOverviewUrl,
        penaltiesCache.psaName
      )))
    }

  }

  def onSubmit(penaltyType: PenaltyType, journeyType: PenaltiesFilter): Action[AnyContent] = identify.async { implicit request =>

    val navMethod: (Seq[PsaFSDetail], Int) => Future[Result] = {
      penaltyType match {
        case AccountingForTaxPenalties => aftNavMethod(request.psaIdOrException.id, journeyType)
        case EventReportingCharges => erNavMethod(request.psaIdOrException.id, journeyType)
        case _ => nonAftNavMethod(penaltyType, journeyType)
      }
    }
    val typeParam = service.getTypeParam(penaltyType)

    service.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val years = getYears(penaltyType, penaltiesCache.penalties)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      form(typeParam).bindFromRequest().fold(
        formWithErrors => {
          Future.successful(BadRequest(selectYearView(
            formWithErrors,
            typeParam,
            TwirlMigration.toTwirlRadiosWithHintText(FSYears.radios(formWithErrors, getYears(penaltyType, penaltiesCache.penalties))),
            routes.SelectPenaltiesYearController.onSubmit(penaltyType,  journeyType),
            config.managePensionsSchemeOverviewUrl,
            penaltiesCache.psaName
          )))
        },
        value => navMethod(penaltiesCache.penalties, value.year)
      )
    }
  }

  private def getYears(penaltyType: PenaltyType, penalties: Seq[PsaFSDetail]): Seq[DisplayYear] = {
    val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == penaltyType)

    filteredPenalties
      .map(_.periodEndDate.getYear).distinct.sorted.reverse
      .map { year =>
        val hint = if (filteredPenalties.filter(_.periodEndDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
        DisplayYear(year, hint)
      }
  }
  private def erNavMethod(psaId: String, journeyType: PenaltiesFilter)
                          (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail], Int) => Future[Result] =
    (penalties, year) => {
      val erPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == EventReportingCharges)
      service.navFromERYearsPage(erPenalties, year, psaId, journeyType)
    }
  private def aftNavMethod(psaId: String, journeyType: PenaltiesFilter)
                          (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail], Int) => Future[Result] =
    (penalties, year) => {
      val aftPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      service.navFromAftYearsPage(aftPenalties, year, psaId, journeyType)
    }

  private def nonAftNavMethod(penaltyType: PenaltyType, journeyType: PenaltiesFilter)
                             (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail], Int) => Future[Result] =
  (penalties, year) => service.navFromNonAftYearsPage(penalties, year.toString, request.psaIdOrException.id, penaltyType, journeyType)

}
