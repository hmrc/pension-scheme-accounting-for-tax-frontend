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

import audit.{AuditService, StartAFTAuditEvent}
import config.FrontendAppConfig
import connectors.MinimalPsaConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.ChargeTypeFormProvider
import javax.inject.Inject
import models.requests.OptionalDataRequest
import models.{ChargeType, GenericViewModel, Mode, Quarter, UserAnswers}
import navigators.CompoundNavigator
import pages._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AllowAccessService, SchemeService}
import uk.gov.hmrc.http.HeaderCarrier
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
                                      schemeService: SchemeService,
                                      auditService: AuditService,
                                      minimalPsaConnector: MinimalPsaConnector,
                                      allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  private def ensureUserAnswersHasPSASuspendedFlag(request: OptionalDataRequest[_])(implicit hc:HeaderCarrier):Future[UserAnswers] = {
    val ua = request.userAnswers.getOrElse(UserAnswers())
    ua.get(IsPsaSuspendedQuery) match {
      case None =>
        minimalPsaConnector.isPsaSuspended(request.psaId.id).map { retrievedIsSuspendedValue =>
          ua.setOrException(IsPsaSuspendedQuery, retrievedIsSuspendedValue)
        }
      case Some(retrievedIsSuspendedValue) => Future.successful(ua)
    }
  }

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      ensureUserAnswersHasPSASuspendedFlag(request).flatMap{ ua =>
        allowService.filterForIllegalPageAccess(srn, ua).flatMap {
          case None =>
            schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
              userAnswersCacheConnector.save(request.internalId,
                ua
                  .setOrException(QuarterPage, Quarter("2020-04-01", "2020-06-30"))
                  .setOrException(AFTStatusQuery, value = "Compiled")
                  .setOrException(SchemeNameQuery, schemeDetails.schemeName)
                  .setOrException(PSTRQuery, schemeDetails.pstr)
                  .data
              ).flatMap { _ =>
                auditService.sendEvent(StartAFTAuditEvent(request.psaId.id, schemeDetails.pstr))
                val preparedForm = ua.get(ChargeTypePage).fold(form)(form.fill)
                val json = Json.obj(
                  fields = "form" -> preparedForm,
                  "radios" -> ChargeType.radios(preparedForm),
                  "viewModel" -> viewModel(schemeDetails.schemeName, mode, srn)
                )

                renderer.render(template = "chargeType.njk", json).map(Ok(_))
              }
            }
          case Some(redirectLocation) => Future.successful(redirectLocation)
        }
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form.bindFromRequest().fold(
          formWithErrors => {
            val json = Json.obj(
              fields = "form" -> formWithErrors,
              "radios" -> ChargeType.radios(formWithErrors),
              "viewModel" -> viewModel(schemeName, mode, srn)
            )
            renderer.render(template = "chargeType.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeTypePage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(ChargeTypePage, mode, updatedAnswers, srn))
        )
      }
  }

  private def viewModel(schemeName: String, mode: Mode, srn: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.ChargeTypeController.onSubmit(mode, srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
