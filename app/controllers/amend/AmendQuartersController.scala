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

package controllers.amend

import config.FrontendAppConfig
import controllers.actions._
import forms.QuartersFormProvider
import models.LocalDateBinder._
import models.requests.IdentifierRequest
import models.{Quarter, Quarters, GenericViewModel}
import play.api.data.Form
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.{SchemeService, QuartersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AmendQuartersController @Inject()(
                                         override val messagesApi: MessagesApi,
                                         identify: IdentifierAction,
                                         formProvider: QuartersFormProvider,
                                         val controllerComponents: MessagesControllerComponents,
                                         renderer: Renderer,
                                         config: FrontendAppConfig,
                                         quartersService: QuartersService,
                                         schemeService: SchemeService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(quarters: Seq[Quarter])(implicit messages: Messages): Form[Quarter] =
    formProvider(messages("amendQuarters.error.required"), quarters)

  private def futureSessionExpiredPage:Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
  
  private def futureReturnHistoryPage(srn:String, startDate:LocalDate):Future[Result] =
    Future.successful(Redirect (controllers.amend.routes.ReturnHistoryController.onPageLoad (srn, startDate) ) )

  def onPageLoad(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getPastQuarters(schemeDetails.pstr, year.toInt).flatMap {
        case Nil => futureSessionExpiredPage
        case Seq(oneQuarterOnly) => futureReturnHistoryPage(srn, oneQuarterOnly.quarter.startDate)
        case displayQuarters =>
          val quarters = displayQuarters.map(_.quarter)
          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> None,
            "form" -> form(quarters),
            "radios" -> Quarters.radios(form(quarters), displayQuarters),
            "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
            "year" -> year
          )
          renderer.render(template = "amend/amendQuarters.njk", json).map(Ok(_))
      }
    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getPastQuarters(schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
        if (displayQuarters.nonEmpty) {
          val quarters = displayQuarters.map(_.quarter)
          form(quarters)
            .bindFromRequest()
            .fold(
              formWithErrors => {

                  val json = Json.obj(
                    fields = "srn" -> srn,
                    "startDate" -> None,
                    "form" -> formWithErrors,
                    "radios" -> Quarters.radios(formWithErrors, displayQuarters),
                    "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
                    "year" -> year
                  )
                  renderer.render(template = "amend/amendQuarters.njk", json).map(BadRequest(_))
              },
              value => futureReturnHistoryPage(srn, value.startDate)
            )
        } else {
          futureSessionExpiredPage
        }
    }
    }
  }

  private def viewModel(srn: String, year: String, schemeName: String)
                       (implicit request: IdentifierRequest[_]): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.AmendQuartersController.onSubmit(srn, year).url,
      returnUrl = config.schemeDashboardUrl(request).format(srn),
      schemeName = schemeName
    )
}
