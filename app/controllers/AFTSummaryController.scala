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

import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AFTSummaryFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.{AFTSummaryPage, PSTRQuery, SchemeNameQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

import scala.concurrent.{ExecutionContext, Future}

class AFTSummaryController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      requireData: DataRequiredAction,
                                      formProvider: AFTSummaryFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      aftConnector: AFTConnector,
                                      schemeService: SchemeService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      val requestUA = request.userAnswers.getOrElse(UserAnswers())
      schemeService.retrieveSchemeDetails(request.psaId.id, srn, request.internalId).flatMap{ schemeDetails =>
        aftConnector.getAFTDetails(schemeDetails.pstr, "2020-04-01", "1").flatMap { aftDetails =>
          val updateUA = UserAnswers(aftDetails.as[JsObject])
            .set(SchemeNameQuery, schemeDetails.schemeName).toOption.getOrElse(requestUA)
            .set(PSTRQuery, schemeDetails.pstr).toOption.getOrElse(requestUA)

          userAnswersCacheConnector.save(request.internalId, updateUA.data).flatMap { _ =>
            val json = Json.obj(
              "form" -> form,
              "list" -> aftSummaryHelper.summaryListData(updateUA, srn),
              "viewModel" -> viewModel(mode, srn, schemeDetails.schemeName),
              "radios" -> Radios.yesNo(form("value"))
            )

            renderer.render("aftSummary.njk", json).map(Ok(_))
          }
        }
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {

            val json = Json.obj(
              "form" -> formWithErrors,
              "list" -> aftSummaryHelper.summaryListData(request.userAnswers, srn),
              "viewModel" -> viewModel(mode, srn, schemeName),
              "radios" -> Radios.yesNo(formWithErrors("value"))
            )

            renderer.render("aftSummary.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AFTSummaryPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AFTSummaryPage, mode, updatedAnswers, srn))
        )
      }
  }

  def viewModel(mode: Mode, srn: String, schemeName: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(mode, srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName)
  }
}
