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

package controllers

import config.FrontendAppConfig
import controllers.actions._
import forms.YearsFormProvider
import models.requests.IdentifierRequest
import models.{GenericViewModel, StartYears, Year}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class YearsController @Inject()(
                                override val messagesApi: MessagesApi,
                                identify: IdentifierAction,
                                allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                formProvider: YearsFormProvider,
                                val controllerComponents: MessagesControllerComponents,
                                renderer: Renderer,
                                config: FrontendAppConfig,
                                schemeService: SchemeService
                            )(implicit ec: ExecutionContext)
                                extends FrontendBaseController
                                with I18nSupport
                                with NunjucksSupport {

  private def form(implicit config: FrontendAppConfig): Form[Year] = formProvider()(StartYears.enumerable)

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen allowAccess()).async { implicit request =>
    schemeService.retrieveSchemeDetails(
      psaId = request.idOrException,
      srn = srn,
      schemeIdType = "srn"
    ) flatMap { schemeDetails =>
      val json = Json.obj(
        "srn" -> srn,
        "startDate" -> None,
        "form" -> form(config),
        "radios" -> StartYears.radios(form(config))(config),
        "viewModel" -> viewModel(schemeDetails.schemeName, srn)
      )

      renderer.render(template = "years.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String): Action[AnyContent] = identify.async { implicit request =>
    form(config)
      .bindFromRequest()
      .fold(
        formWithErrors =>
          schemeService.retrieveSchemeDetails(
            psaId = request.idOrException,
            srn = srn,
            schemeIdType = "srn"
          ) flatMap { schemeDetails =>
            val json = Json.obj(
              fields = "srn" -> srn,
              "startDate" -> None,
              "form" -> formWithErrors,
              "radios" -> StartYears.radios(formWithErrors)(config),
              "viewModel" -> viewModel(schemeDetails.schemeName, srn)
            )
            renderer.render(template = "years.njk", json).map(BadRequest(_))
        },
        value => Future.successful(Redirect(controllers.routes.QuartersController.onPageLoad(srn, value.getYear.toString)))
      )
  }

  private def viewModel(schemeName: String, srn: String)(implicit request: IdentifierRequest[_]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.YearsController.onSubmit(srn).url,
      returnUrl = config.schemeDashboardUrl(request).format(srn),
      schemeName = schemeName
    )
  }

}
