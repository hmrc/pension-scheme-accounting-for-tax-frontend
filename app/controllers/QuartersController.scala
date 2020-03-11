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
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.QuartersFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Quarters}
import navigators.CompoundNavigator
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}
import models.LocalDateBinder._

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
    auditService: AuditService,
    aftService: AFTService,
    allowService: AllowAccessService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(year: String)(implicit messages: Messages, config: FrontendAppConfig): Form[Quarters] =
    formProvider(messages("quarters.error.required", year), year.toInt)

  def onPageLoad(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
      val json = Json.obj(
        "srn" -> srn,
        "startDate" -> None,
        "form" -> form(year)(implicitly, config),
        "radios" -> Quarters.radios(form(year)(implicitly, config), year.toInt)(implicitly, config),
        "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
        "year" -> year
      )

      renderer.render(template = "quarters.njk", json).map(Ok(_))
    }
  }

  def onSubmit(srn: String, year: String): Action[AnyContent] = identify.async { implicit request =>
    form(year)(implicitly, config)
      .bindFromRequest()
      .fold(
        formWithErrors => {
          schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
            val json = Json.obj(
              fields = "srn" -> srn,
              "startDate" -> None,
              "form" -> formWithErrors,
              "radios" -> Quarters.radios(formWithErrors, year.toInt)(implicitly, config),
              "viewModel" -> viewModel(srn, year, schemeDetails.schemeName),
              "year" -> year
            )
            renderer.render(template = "quarters.njk", json).map(BadRequest(_))
          }
        },
        value =>
          Future.successful(Redirect(controllers.routes.ChargeTypeController.onPageLoad(srn, Quarters.getStartDate(value, year.toInt))))
      )

  }

  private def viewModel(srn: String, year: String, schemeName: String): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.QuartersController.onSubmit(srn, year).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
}
