/*
 * Copyright 2022 HM Revenue & Customs
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

import controllers.actions._
import controllers.financialOverview.psa.routes._
import forms.SelectSchemeFormProvider
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.requests.IdentifierRequest
import models.{ChargeDetailsFilter, DisplayHint, PaymentOverdue, PenaltySchemes}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.financialOverview.psa.{PenaltiesNavigationService, PsaPenaltiesAndChargesService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SelectSchemeController @Inject()(
                                        identify: IdentifierAction,
                                        allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                        override val messagesApi: MessagesApi,
                                        val controllerComponents: MessagesControllerComponents,
                                        formProvider: SelectSchemeFormProvider,
                                        psaPenaltiesAndChargesService: PsaPenaltiesAndChargesService,
                                        navService: PenaltiesNavigationService,
                                        renderer: Renderer
                                      )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(schemes: Seq[PenaltySchemes], typeParam: String)
                  (implicit messages: Messages): Form[PenaltySchemes] = formProvider(schemes, messages("selectScheme.error", typeParam))

  def onPageLoad(penaltyType: PenaltyType, period: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
      val (penaltySchemesFunction, _) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id)
      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        penaltySchemesFunction(penaltiesCache.penalties.toSeq).flatMap { penaltySchemes =>
          if (penaltySchemes.nonEmpty) {

            val diplayPenaltySchemes = getPenaltySchemes(penaltiesCache.penalties.toSeq, penaltySchemes, penaltyType, period)
            val typeParam = psaPenaltiesAndChargesService.getTypeParam(penaltyType)
            val json = Json.obj(
              "psaName" -> penaltiesCache.psaName,
              "typeParam" -> typeParam,
              "form" -> form(penaltySchemes, typeParam),
              "radios" -> PenaltySchemes.radios(form(penaltySchemes, typeParam), diplayPenaltySchemes,
                Seq("govuk-tag govuk-tag--red govuk-!-display-inline"), areLabelsBold = false))

            renderer.render(template = "financialOverview/psa/selectScheme.njk", json).map(Ok(_))
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
      }
  }

  def onSubmit(penaltyType: PenaltyType, period: String, journeyType: ChargeDetailsFilter): Action[AnyContent] = identify.async {
    implicit request =>

      val (penaltySchemesFunction, redirectUrl) = getSchemesAndUrl(penaltyType, period, request.psaIdOrException.id)

      psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
        penaltySchemesFunction(penaltiesCache.penalties.toSeq).flatMap { penaltySchemes =>

          val typeParam = psaPenaltiesAndChargesService.getTypeParam(penaltyType)

          form(penaltySchemes, typeParam).bindFromRequest().fold(
            formWithErrors => {

              val json = Json.obj(
                "psaName" -> penaltiesCache.psaName,
                "typeParam" -> typeParam,
                "form" -> formWithErrors,
                "radios" -> PenaltySchemes.radios(formWithErrors, penaltySchemes))

              renderer.render(template = "financialOverview/psa/selectScheme.njk", json).map(BadRequest(_))
            },
            value => psaPenaltiesAndChargesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).map { _ =>
              Redirect(redirectUrl(value.pstr))
            }
          )
        }
      }
  }

  def getSchemesAndUrl(penaltyType: PenaltyType, period: String, psaId: String)
                      (implicit request: IdentifierRequest[AnyContent]): (Seq[PsaFSDetail] => Future[Seq[PenaltySchemes]], String => Call) =
    penaltyType match {
      case AccountingForTaxPenalties =>
        (penalties => navService.penaltySchemes(LocalDate.parse(period), psaId, penalties).map(_.toSeq),
          identifier => AllPenaltiesAndChargesController.onPageLoadAFT(period, identifier))
      case _ =>
        (penalties => navService.penaltySchemes(period.toInt, psaId, penaltyType, penalties).map(_.toSeq),
          identifier => AllPenaltiesAndChargesController.onPageLoad(period, identifier, penaltyType))
    }

  def getPenaltySchemes(psaFSDetails: Seq[PsaFSDetail], penaltySchemes: Seq[PenaltySchemes], penaltyType: PenaltyType, period: String): Seq[PenaltySchemes] =
    penaltySchemes.map(value => {

      val filteredPsaFSDetails = psaFSDetails.filter(_.pstr == value.pstr)
        .filter(x => getPenaltyType(x.chargeType).equalsIgnoreCase(penaltyType))
        .filter(_.periodStartDate.toString == period)

      val isOverdue: Boolean = filteredPsaFSDetails.exists(psaPenaltiesAndChargesService.isPaymentOverdue)
      val hint: Option[DisplayHint] = if (isOverdue) Some(PaymentOverdue) else None
      PenaltySchemes(value.name, value.pstr, value.srn, hint)
    })
}






