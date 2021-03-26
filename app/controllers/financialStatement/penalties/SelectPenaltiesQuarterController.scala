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

import controllers.actions._
import forms.QuartersFormProvider
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, getPenaltyType}
import models.financialStatement.PsaFS
import models.{DisplayHint, DisplayQuarter, PaymentOverdue, Quarter, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.PenaltiesService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectPenaltiesQuarterController @Inject()(
                                                  override val messagesApi: MessagesApi,
                                                  identify: IdentifierAction,
                                                  allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                                  formProvider: QuartersFormProvider,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  renderer: Renderer,
                                                  penaltiesService: PenaltiesService)
                                                (implicit ec: ExecutionContext)
                                                  extends FrontendBaseController
                                                  with I18nSupport
                                                  with NunjucksSupport {

  private def form(quarters: Seq[Quarter])(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("selectPenaltiesQuarter.error"), quarters)

  def onPageLoad(year: String): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>


    penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>

      val quarters: Seq[Quarter] = getQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt))

        if (quarters.nonEmpty) {

          val json = Json.obj(
            "psaName" -> penaltiesCache.psaName,
            "form" -> form(quarters),
            "radios" -> Quarters.radios(form(quarters),
                                        getDisplayQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt)),
                                        Seq("govuk-tag govuk-tag--red govuk-!-display-inline"),
                                        areLabelsBold = false),
            "submitUrl" -> routes.SelectPenaltiesQuarterController.onSubmit(year).url,
            "year" -> year
          )

          renderer.render(template = "financialStatement/penalties/selectQuarter.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }

    }
  }

  def onSubmit(year: String): Action[AnyContent] = identify.async { implicit request =>
    penaltiesService.getPenaltiesFromCache(request.psaIdOrException.id).flatMap { penaltiesCache =>

      val quarters: Seq[Quarter] = getQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt))
        if (quarters.nonEmpty) {

          form(quarters).bindFromRequest().fold(
              formWithErrors => {

                  val json = Json.obj(
                    "psaName" -> penaltiesCache.psaName,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors,
                                                getDisplayQuarters(year, filteredPenalties(penaltiesCache.penalties, year.toInt)),
                                                Seq("govuk-tag govuk-!-display-inline govuk-tag--red"),
                                                areLabelsBold = false),
                    "submitUrl" -> routes.SelectPenaltiesQuarterController.onSubmit(year).url,
                    "year" -> year
                  )
                  renderer.render(template = "financialStatement/penalties/selectQuarter.njk", json).map(BadRequest(_))

              },
              value => penaltiesService.navFromAftQuartersPage(penaltiesCache.penalties, value.startDate, request.psaIdOrException.id)
            )
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
    }
  }

  val filteredPenalties: (Seq[PsaFS], Int) => Seq[PsaFS] = (penalties, year) =>
    penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodStartDate.getYear == year)

  private def getDisplayQuarters(year: String, penalties: Seq[PsaFS]): Seq[DisplayQuarter] = {

    val quartersFound: Seq[LocalDate] = penalties.map(_.periodStartDate).distinct.sortBy(_.getMonth)

    quartersFound.map { startDate =>
      val hint: Option[DisplayHint] =
        if (penalties.filter(_.periodStartDate == startDate).exists(penaltiesService.isPaymentOverdue)) Some(PaymentOverdue) else None

      DisplayQuarter(Quarters.getQuarter(startDate), displayYear = false, None, hint)

    }
  }

  private def getQuarters(year: String, penalties: Seq[PsaFS]): Seq[Quarter] =
    penalties.distinct.map(penalty => Quarters.getQuarter(penalty.periodStartDate))


}
