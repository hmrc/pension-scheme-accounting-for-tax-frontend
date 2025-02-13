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

package controllers

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.{AFTQuarter, Draft, Quarters, SubmittedHint}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.QuartersView

class QuartersController @Inject()(
                                    override val messagesApi: MessagesApi,
                                    identify: IdentifierAction,
                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                    formProvider: QuartersFormProvider,
                                    val controllerComponents: MessagesControllerComponents,
                                    quartersView: QuartersView,
                                    config: FrontendAppConfig,
                                    schemeService: SchemeService,
                                    aftConnector: AFTConnector,
                                    quartersService: QuartersService
                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(year: String, quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("quarters.error.required", year), quarters)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
        if (displayQuarters.nonEmpty) {
          val quarters = displayQuarters.map(_.quarter)

          Future.successful(Ok(quartersView(
            year,
            form(year, quarters),
            Quarters.radios(form(year, quarters), displayQuarters),
            routes.QuartersController.onSubmit(srn, year),
            config.schemeDashboardUrl(request).format(srn),
            schemeDetails.schemeName
          )))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn") flatMap { schemeDetails =>
      aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
        quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
          if (displayQuarters.nonEmpty) {
            val quarters = displayQuarters.map(_.quarter)
            form(year, quarters)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Future.successful(BadRequest(quartersView(
                    year,
                    formWithErrors,
                    Quarters.radios(formWithErrors, displayQuarters),
                    routes.QuartersController.onSubmit(srn, year),
                    config.schemeDashboardUrl(request).format(srn),
                    schemeDetails.schemeName
                  )))
                },
                value => {
                  val tpssReports = aftOverview.filter(_.periodStartDate == value.startDate).filter(_.tpssReportPresent)
                  if (tpssReports.nonEmpty) {
                    Future.successful(Redirect(controllers.routes.CannotSubmitAFTController.onPageLoad(srn, value.startDate)))
                  } else {
                    displayQuarters.find(_.quarter == value) match {
                      case None => throw InvalidValueSelected(s"display quarters = $displayQuarters and value = $value ")
                      case Some(selectedDisplayQuarter) =>
                        selectedDisplayQuarter.hintText match {
                          case None =>
                            Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, value.startDate, Draft, version = 1)))
                          case Some(SubmittedHint) =>
                            Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, value.startDate)))
                          case Some(_) =>
                            aftOverview.find(_.periodStartDate == value.startDate)
                              .filter(_.versionDetails.nonEmpty).map(_.toPodsReport) match {
                              case None =>
                                Future.successful(Redirect(controllers.routes.AFTReturnLockedController.onPageLoad(srn, value.startDate)))
                              case Some(o) =>
                                Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, value.startDate, Draft, o.numberOfVersions)))
                            }
                        }
                    }
                  }
                }
              )
          } else {
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
          }
        }
      }
    }
  }

  case class InvalidValueSelected(details: String) extends Exception(s"The selected quarter did not match any quarters in the list of options: $details")
}
