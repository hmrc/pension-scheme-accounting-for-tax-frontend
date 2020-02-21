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

package controllers.chargeE

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.YearRangeFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{GenericViewModel, Index, Mode, YearRange}
import navigators.CompoundNavigator
import pages.chargeE.AnnualAllowanceYearPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class AnnualAllowanceYearController @Inject()(override val messagesApi: MessagesApi,
                                              userAnswersCacheConnector: UserAnswersCacheConnector,
                                              navigator: CompoundNavigator,
                                              identify: IdentifierAction,
                                              getData: DataRetrievalAction,
                                              allowAccess: AllowAccessActionProvider,
                                              requireData: DataRequiredAction,
                                              formProvider: YearRangeFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              config: FrontendAppConfig,
                                              renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def form()(implicit messages: Messages): Form[YearRange] =
    formProvider("annualAllowanceYear.error.required")

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val preparedForm: Form[YearRange] = request.userAnswers.get(AnnualAllowanceYearPage(index)) match {
          case Some(value) => form.fill(value)
          case None => form
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.AnnualAllowanceYearController.onSubmit(mode, srn, startDate, index).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val json = Json.obj(
          "srn" -> srn,
          "form" -> preparedForm,
          "radios" -> YearRange.radios(preparedForm),
          "viewModel" -> viewModel
        )

        renderer.render(template = "chargeE/annualAllowanceYear.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form.bindFromRequest().fold(
          formWithErrors => {
            val viewModel = GenericViewModel(
              submitUrl = routes.AnnualAllowanceYearController.onSubmit(mode, srn, startDate, index).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
          "srn" -> srn,
          "form" -> formWithErrors,
              "radios" -> YearRange.radios(formWithErrors),
              "viewModel" -> viewModel
            )
            renderer.render(template = "chargeE/annualAllowanceYear.njk", json).map(BadRequest(_))
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AnnualAllowanceYearPage(index), value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AnnualAllowanceYearPage(index), mode, updatedAnswers, srn, startDate))
          }
        )
      }
  }
}
