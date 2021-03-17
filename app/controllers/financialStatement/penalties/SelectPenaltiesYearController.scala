/*
 * Copyright 2021 HM Revenue & Customs
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
import models.financialStatement.{PenaltyType, PsaFS}
import models.requests.IdentifierRequest
import models.{DisplayYear, FSYears, PaymentOverdue, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesYearController @Inject()(override val messagesApi: MessagesApi,
                                              identify: IdentifierAction,
                                              allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                              formProvider: YearsFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              renderer: Renderer,
                                              config: FrontendAppConfig,
                                              service: PenaltiesService)
                                             (implicit ec: ExecutionContext) extends FrontendBaseController
                                                                      with I18nSupport
                                                                      with NunjucksSupport {

  private def form(errorParameter: String, config: FrontendAppConfig)(implicit messages: Messages): Form[Year] =
    formProvider(messages("selectPenaltiesYear.error", messages(errorParameter)))(config)

  def onPageLoadAft: Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    onPageLoad(AccountingForTaxPenalties, "penaltyType.accountingForTax")
  }

  def onSubmitAft: Action[AnyContent] = identify.async { implicit request =>

    val penaltyType: PenaltyType = AccountingForTaxPenalties
    val navMethod: (Seq[PsaFS], Int) => Future[Result] =
      (penalties, year) => {
        val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == penaltyType)
        service.skipYearsPage(filteredPenalties, year, request.psaIdOrException.id)
      }

    onSubmit(penaltyType, "penaltyType.accountingForTax", navMethod)
  }

  def onPageLoadContract: Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    onPageLoad(ContractSettlementCharges, "penaltyType.contractSettlement")
  }

  def onSubmitContract: Action[AnyContent] = identify.async { implicit request =>

    val penaltyType: PenaltyType = ContractSettlementCharges
    onSubmit(penaltyType, "penaltyType.contractSettlement", nonAftNavMethod(penaltyType))
  }

  def onPageLoadInfoNotice: Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    onPageLoad(InformationNoticePenalties, "penaltyType.informationNotice")
  }

  def onSubmitInfoNotice: Action[AnyContent] = identify.async { implicit request =>

    val penaltyType: PenaltyType = InformationNoticePenalties
    onSubmit(penaltyType, "penaltyType.informationNotice", nonAftNavMethod(penaltyType))
  }

  def onPageLoadPension: Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    onPageLoad(PensionsPenalties, "penaltyType.pensionsPenalties")
  }

  def onSubmitPension: Action[AnyContent] = identify.async { implicit request =>

    val penaltyType: PenaltyType = PensionsPenalties
    onSubmit(penaltyType, "penaltyType.pensionsPenalties", nonAftNavMethod(penaltyType))
  }

  private def onPageLoad(penaltyType: PenaltyType, typeParam: String)
                        (implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>

      val years = getYears(penaltyType, penalties)
      val json = Json.obj(
        "typeParam" -> typeParam,
        "form" -> form(typeParam, config),
        "radios" -> FSYears.radios(form(typeParam, config), years)
      )

      renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(Ok(_))
    }

  private def onSubmit(penaltyType: PenaltyType, typeParam: String, navMethod: (Seq[PsaFS], Int) => Future[Result])
              (implicit request: IdentifierRequest[AnyContent]): Future[Result] =
    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penalties =>
      form(typeParam, config).bindFromRequest().fold(
        formWithErrors => {
          val json = Json.obj(
            "typeParam" -> typeParam,
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(penaltyType, penalties))
          )
          renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(BadRequest(_))
        },
        value => navMethod(penalties, value.year)
      )
    }

  private def getYears(penaltyType: PenaltyType, penalties: Seq[PsaFS]): Seq[DisplayYear] =
    penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .map(_.periodStartDate.getYear).distinct.sorted.reverse
      .map { year =>
        val hint = if (penalties.filter(_.periodStartDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
        DisplayYear(year, hint)
      }

  private def nonAftNavMethod(penaltyType: PenaltyType)
                             (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFS], Int) => Future[Result] =
  (penalties, year) => service.skipYearsAndQuartersPage(penalties, year.toString, request.psaIdOrException.id, penaltyType)

}
