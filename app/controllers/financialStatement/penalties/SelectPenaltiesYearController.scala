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
import models.{DisplayYear, Enumerable, FSYears, PaymentOverdue, Year}
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

  private def form(errorParameter: String, config: FrontendAppConfig)(implicit messages: Messages, ev: Enumerable[Year]): Form[Year] =
    formProvider(messages("selectPenaltiesYear.error", messages(errorParameter)))(config, implicitly)

  def onPageLoad(penaltyType: PenaltyType): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>

    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>

      val typeParam = service.getTypeParam(penaltyType)
      val years = getYears(penaltyType, penaltiesCache.penalties)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      val json = Json.obj(
        "psaName" -> penaltiesCache.psaName,
        "typeParam" -> typeParam,
        "form" -> form(typeParam, config),
        "radios" -> FSYears.radios(form(typeParam, config), years)
      )

      renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(Ok(_))
    }

  }

  def onSubmit(penaltyType: PenaltyType): Action[AnyContent] = identify.async { implicit request =>

    val navMethod: (Seq[PsaFS], Int) => Future[Result] =
      if (penaltyType == AccountingForTaxPenalties) aftNavMethod(request.psaIdOrException.id) else nonAftNavMethod(penaltyType)
    val typeParam = service.getTypeParam(penaltyType)

    service.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>
      val years = getYears(penaltyType, penaltiesCache.penalties)
      implicit val ev: Enumerable[Year] = FSYears.enumerable(years.map(_.year))

      form(typeParam, config).bindFromRequest().fold(
        formWithErrors => {
          val json = Json.obj(
            "psaName" -> penaltiesCache.psaName,
            "typeParam" -> typeParam,
            "form" -> formWithErrors,
            "radios" -> FSYears.radios(formWithErrors, getYears(penaltyType, penaltiesCache.penalties))
          )
          renderer.render(template = "financialStatement/penalties/selectYear.njk", json).map(BadRequest(_))
        },
        value => navMethod(penaltiesCache.penalties, value.year)
      )
    }
  }

  private def getYears(penaltyType: PenaltyType, penalties: Seq[PsaFS]): Seq[DisplayYear] = {
    val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == penaltyType)

    filteredPenalties
      .map(_.periodEndDate.getYear).distinct.sorted.reverse
      .map { year =>
        val hint = if (filteredPenalties.filter(_.periodEndDate.getYear == year).exists(service.isPaymentOverdue)) Some(PaymentOverdue) else None
        DisplayYear(year, hint)
      }
  }

  private def aftNavMethod(psaId: String)
                          (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFS], Int) => Future[Result] =
    (penalties, year) => {
      val filteredPenalties = penalties.filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      service.navFromAftYearsPage(filteredPenalties, year, psaId)
    }

  private def nonAftNavMethod(penaltyType: PenaltyType)
                             (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFS], Int) => Future[Result] =
  (penalties, year) => service.navFromNonAftYearsPage(penalties, year.toString, request.psaIdOrException.id, penaltyType)

}
