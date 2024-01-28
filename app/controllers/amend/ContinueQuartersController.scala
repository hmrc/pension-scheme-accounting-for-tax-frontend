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
import models.requests.IdentifierRequest
import models.{AFTQuarter, Draft, GenericViewModel, Quarters}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{QuartersService, SchemeService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ContinueQuartersController @Inject()(
                                            override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            formProvider: QuartersFormProvider,
                                            val controllerComponents: MessagesControllerComponents,
                                            renderer: Renderer,
                                            config: FrontendAppConfig,
                                            quartersService: QuartersService,
                                            schemeService: SchemeService,
                                            aftConnector: AFTConnector
                                          )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("continueQuarters.error.required"), quarters)

  def onPageLoad(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      quartersService.getInProgressQuarters(srn, schemeDetails.pstr).flatMap { displayQuarters =>
        if (displayQuarters.nonEmpty) {

          val quarters = displayQuarters.map(_.quarter)

          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> None,
            "form" -> form(quarters),
            "radios" -> Quarters.radios(form(quarters), displayQuarters),
            "viewModel" -> viewModel(srn, schemeDetails.schemeName)
          )

          renderer.render(template = "amend/continueQuarters.njk", json).map(Ok(_))
        } else {
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      aftConnector.getAftOverview(schemeDetails.pstr).flatMap { aftOverview =>
        quartersService.getInProgressQuarters(srn, schemeDetails.pstr).flatMap { displayQuarters =>
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
                    "viewModel" -> viewModel(srn, schemeDetails.schemeName)
                  )
                  renderer.render(template = "amend/continueQuarters.njk", json).map(BadRequest(_))
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

  private def viewModel(srn: String, schemeName: String)(implicit request: IdentifierRequest[_]): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ContinueQuartersController.onSubmit(srn).url,
      returnUrl = config.schemeDashboardUrl(request).format(srn),
      schemeName = schemeName
    )

  case object InvalidValueSelected extends Exception("The selected quarter did not match any quarters in the list of options")

}
