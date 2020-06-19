/*
 * Copyright 2020 HM Revenue & Customs
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

import audit.AuditService
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.QuartersFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{Draft, GenericViewModel, Quarter, Quarters, SubmittedHint}
import navigators.CompoundNavigator
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AllowAccessService, QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class QuartersController @Inject()(
                                    override val messagesApi: MessagesApi,
                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                    navigator: CompoundNavigator,
                                    identify: IdentifierAction,
                                    getData: DataRetrievalAction,
                                    allowAccess: AllowAccessActionProvider,
                                    requireData: DataRequiredAction,
                                    formProvider: QuartersFormProvider,
                                    val controllerComponents: MessagesControllerComponents,
                                    renderer: Renderer,
                                    config: FrontendAppConfig,
                                    schemeService: SchemeService,
                                    aftConnector: AFTConnector,
                                    auditService: AuditService,
                                    quartersService: QuartersService,

                                    allowService: AllowAccessService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(year: String, quarters: Seq[Quarter])(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("quarters.error.required", year), quarters)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
        if (displayQuarters.nonEmpty) {
          val quarters = displayQuarters.map(_.quarter)

          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> None,
            "form" -> form(year, quarters),
            "radios" -> Quarters.radios(form(year, quarters), displayQuarters),
            "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
            "year" -> year
          )

          renderer.render(template = "quarters.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
        quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
          if (displayQuarters.nonEmpty) {

            val quarters = displayQuarters.map(_.quarter)
            form(year, quarters)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
                    val json = Json.obj(
                      fields = "srn" -> srn,
                      "startDate" -> None,
                      "form" -> formWithErrors,
                      "radios" -> Quarters.radios(formWithErrors, displayQuarters),
                      "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
                      "year" -> year
                    )
                    renderer.render(template = "quarters.njk", json).map(BadRequest(_))
                  }
                },
                value => {
                  val selectedDisplayQuarter = displayQuarters.find(_.quarter == value).getOrElse(throw InvalidValueSelected)
                  selectedDisplayQuarter.hintText match {
                    case None => Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, value.startDate, Draft, version = 1)))
                    case Some(SubmittedHint) => Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, value.startDate)))
                    case _ =>
                      val version = aftOverview.find(_.periodStartDate == value.startDate).getOrElse(throw InvalidValueSelected).numberOfVersions
                      Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, value.startDate, Draft, version)))
                  }
                }
              )
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        }
      }
    }
  }

  private def viewModel(srn: String, year: String, schemeName: String): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.QuartersController.onSubmit(srn, year).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )

  case object InvalidValueSelected extends Exception("The selected quarter did not match any quarters in the list of options")
}
