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

import java.time.LocalDate

import audit.AuditService
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.YearsFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Years}
import navigators.CompoundNavigator
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class YearsController @Inject()(
                                 override val messagesApi: MessagesApi,
                                 userAnswersCacheConnector: UserAnswersCacheConnector,
                                 navigator: CompoundNavigator,
                                 identify: IdentifierAction,
                                 getData: DataRetrievalAction,
                                 allowAccess: AllowAccessActionProvider,
                                 requireData: DataRequiredAction,
                                 formProvider: YearsFormProvider,
                                 val controllerComponents: MessagesControllerComponents,
                                 renderer: Renderer,
                                 config: FrontendAppConfig,
                                 schemeService: SchemeService,
                                 auditService: AuditService,
                                 aftService: AFTService,
                                 allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form = formProvider()

  def onPageLoad(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> None,
          "form" -> form,
          "radios" -> Years.radios(form),
          "viewModel" -> viewModel(schemeDetails.schemeName, srn)
        )

        renderer.render(template = "years.njk", json).map(Ok(_))
      }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async {
    implicit request =>

        form.bindFromRequest().fold(
          formWithErrors =>
            schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
            val json = Json.obj(
              fields = "srn" -> srn,
              "startDate" -> None,
              "form" -> formWithErrors,
              "radios" -> Years.radios(formWithErrors),
              "viewModel" -> viewModel(schemeDetails.schemeName, srn)
            )
            renderer.render(template = "years.njk", json).map(BadRequest(_))
          },
          value =>
            Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, value.getYear.toString)))
        )
  }

  private def viewModel(schemeName: String, srn: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.YearsController.onSubmit(srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
