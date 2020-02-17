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
import models.{GenericViewModel, NormalMode, Quarters, Years}
import navigators.CompoundNavigator
import pages._
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

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
                                   auditService: AuditService,
                                   aftService: AFTService,
                                   allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private def form(year: Years)(implicit messages: Messages): Form[Quarters] =
    formProvider(messages("quarters.error.required", year.getYear.toString), year.getYear)

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen allowAccess(srn) andThen requireData).async {
    implicit request =>

      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(YearPage) match {
          case Some(year) =>

          val preparedForm: Form[Quarters] = request.userAnswers.get(QuarterPage) match {
            case None => form(year)
            case Some(value) =>
              form(year).fill(value.getQuarters)
          }

          val json = Json.obj(
            "srn" -> srn,
            "form" -> preparedForm,
            "radios" -> Quarters.radios(preparedForm, year.getYear),
            "viewModel" -> viewModel(schemeName, srn),
            "year" -> year.getYear.toString
          )

          renderer.render(template = "quarters.njk", json).map(Ok(_))

          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
  }

  def onSubmit(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        request.userAnswers.get(YearPage) match {
          case Some(year) =>

            form(year).bindFromRequest().fold(
              formWithErrors => {
                val json = Json.obj(
                  fields = "srn" -> srn,
                  "form" -> formWithErrors,
                  "radios" -> Quarters.radios(formWithErrors, year.getYear),
                  "viewModel" -> viewModel(schemeName, srn),
                  "year" -> year.getYear.toString
                )
                renderer.render(template = "quarters.njk", json).map(BadRequest(_))
              },
              value =>
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(QuarterPage, Quarters.getQuarter(value, year.getYear)))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(QuarterPage, NormalMode, updatedAnswers, srn))
            )
          case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      }
  }

  private def viewModel(schemeName: String, srn: String): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.QuartersController.onSubmit(srn).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName
    )
  }
}
