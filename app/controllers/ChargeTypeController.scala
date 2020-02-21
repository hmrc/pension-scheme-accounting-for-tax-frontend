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

import audit.{AuditService, StartAFTAuditEvent}
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeTypeFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{ChargeType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class ChargeTypeController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: ChargeTypeFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      auditService: AuditService,
                                      aftService: AFTService,
                                      allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate)).async {
    implicit request =>

      if (!request.viewOnly) {

        aftService.retrieveAFTRequiredDetails(srn = srn, startDate = startDate, optionVersion = None).flatMap { case (schemeDetails, userAnswers) =>
          allowService.filterForIllegalPageAccess(srn, userAnswers).flatMap {
            case None =>
              auditService.sendEvent(StartAFTAuditEvent(request.psaId.id, schemeDetails.pstr))
              val preparedForm = userAnswers.get(ChargeTypePage).fold(form)(form.fill)
              val json = Json.obj(
                fields = "srn" -> srn,
                "form" -> preparedForm,
                "radios" -> ChargeType.radios(preparedForm),
                "viewModel" -> viewModel(schemeDetails.schemeName, srn, startDate)
              )
              renderer.render(template = "chargeType.njk", json).map(Ok(_))
            case Some(alternativeLocation) => Future.successful(alternativeLocation)
          }
        }
      } else {
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
      }
  }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form.bindFromRequest().fold(
          formWithErrors => {
            val json = Json.obj(
              fields = "srn" -> srn,
              "form" -> formWithErrors,
              "radios" -> ChargeType.radios(formWithErrors),
              "viewModel" -> viewModel(schemeName, srn, startDate)
            )
            renderer.render(template = "chargeType.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeTypePage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(ChargeTypePage, NormalMode, updatedAnswers, srn, startDate))
        )
      }
  }

  private def viewModel(schemeName: String, srn: String, startDate: LocalDate): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.ChargeTypeController.onSubmit(srn, startDate).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
