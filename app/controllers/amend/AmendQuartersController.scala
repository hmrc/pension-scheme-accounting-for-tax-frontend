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
import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.{AFTQuarter, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.amend.AmendQuartersView

class AmendQuartersController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         identify: IdentifierAction,
                                         formProvider: QuartersFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         config: FrontendAppConfig,
                                         quartersService: QuartersService,
                                         schemeService: SchemeService,
                                         allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                         amendQuartersView: AmendQuartersView
                                       )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("amendQuarters.error.required"), quarters)

  private def futureSessionExpiredPage: Future[Result] =
    Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  private def futureReturnHistoryPage(srn: String, startDate: LocalDate): Future[Result] =
    Future.successful(Redirect(controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate)))

  def onPageLoad(srn: String, year: String): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        schemeService.retrieveSchemeDetails(
          psaId = request.idOrException,
          srn = srn
        ) flatMap {
          schemeDetails =>
            quartersService.getPastQuarters(
              pstr = schemeDetails.pstr,
              year = year.toInt,
              srn  = srn,
              request.isLoggedInAsPsa
            ) flatMap {
              case Nil =>
                futureSessionExpiredPage
              case Seq(oneQuarterOnly) =>
                futureReturnHistoryPage(srn, oneQuarterOnly.quarter.startDate)
              case displayQuarters =>
                val quarters = displayQuarters.map(_.quarter)
                Future.successful(Ok(amendQuartersView(
                  form(quarters),
                  Quarters.radios(form(quarters), displayQuarters),
                  routes.AmendQuartersController.onSubmit(srn, year),
                  config.schemeDashboardUrl(request).format(srn),
                  schemeDetails.schemeName
                )))
            }
        }
    }

  def onSubmit(srn: String, year: String): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        schemeService.retrieveSchemeDetails(
          psaId = request.idOrException,
          srn = srn
        ) flatMap {
          schemeDetails =>
            quartersService.getPastQuarters(
              pstr = schemeDetails.pstr,
              year = year.toInt,
              srn = srn,
              request.isLoggedInAsPsa
            ) flatMap {
              displayQuarters =>
                if (displayQuarters.nonEmpty) {
                  form(displayQuarters.map(_.quarter))
                    .bindFromRequest()
                    .fold(
                      formWithErrors =>
                          Future.successful(BadRequest(amendQuartersView(
                            formWithErrors,
                            Quarters.radios(formWithErrors, displayQuarters),
                            routes.AmendQuartersController.onSubmit(srn, year),
                            config.schemeDashboardUrl(request).format(srn),
                            schemeDetails.schemeName
                          ))),
                      value =>
                        futureReturnHistoryPage(srn, value.startDate)
                    )
                } else {
                  futureSessionExpiredPage
                }
            }
        }
    }
}
