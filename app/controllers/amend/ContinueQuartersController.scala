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

package controllers.amend

import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.{AFTQuarter, Draft, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.amend.ContinueQuartersView

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ContinueQuartersController @Inject()(
                                            override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            formProvider: QuartersFormProvider,
                                            val controllerComponents: MessagesControllerComponents,
                                            config: FrontendAppConfig,
                                            quartersService: QuartersService,
                                            schemeService: SchemeService,
                                            aftConnector: AFTConnector,
                                            allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                            continueQuartersView: ContinueQuartersView
                                          )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("continueQuarters.error.required"), quarters)

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn
    ) flatMap { schemeDetails =>
      quartersService.getInProgressQuarters(srn, schemeDetails.pstr, request.isLoggedInAsPsa).flatMap { displayQuarters =>
        if (displayQuarters.nonEmpty) {

          val quarters = displayQuarters.map(_.quarter)

          Future.successful(Ok(continueQuartersView(
            form(quarters),
            Quarters.radios(form(quarters), displayQuarters),
            routes.ContinueQuartersController.onSubmit(srn),
            config.schemeDashboardUrl(request).format(srn),
            schemeDetails.schemeName
          )))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }
  }

  def onSubmit(srn: String): Action[AnyContent] = (identify andThen allowAccess(Some(srn))).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn
    ) flatMap { schemeDetails =>
      aftConnector.getAftOverview(schemeDetails.pstr, srn, request.isLoggedInAsPsa).flatMap { aftOverview =>
        quartersService.getInProgressQuarters(srn, schemeDetails.pstr, request.isLoggedInAsPsa).flatMap { displayQuarters =>
          if (displayQuarters.nonEmpty) {

            val quarters = displayQuarters.map(_.quarter)

            form(quarters)
              .bindFromRequest()
              .fold(
                formWithErrors => {
                  Future.successful(BadRequest(continueQuartersView(
                    formWithErrors,
                    Quarters.radios(formWithErrors, displayQuarters),
                    routes.ContinueQuartersController.onSubmit(srn),
                    config.schemeDashboardUrl(request).format(srn),
                    schemeDetails.schemeName
                  )))
                },
                value => {
                  val aftOverviewElement = aftOverview.filter(_.versionDetails.isDefined)
                    .map(_.toPodsReport).find(_.periodStartDate == value.startDate).getOrElse(throw InvalidValueSelected)
                  if (!aftOverviewElement.submittedVersionAvailable) {
                    Future.successful(
                      Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, value.startDate, Draft, version = 1)))
                  } else {
                    Future.successful(
                      Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, value.startDate)))
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

  case object InvalidValueSelected extends Exception("The selected quarter did not match any quarters in the list of options")

}
