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

package controllers.chargeF

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{GenericViewModel, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeF.{DeleteChargePage, DeregistrationQuery}
import pages.chargeF.DeregistrationQuery
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.DeleteAFTChargeService
import services.{AFTService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class DeleteChargeController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       userAnswersService: UserAnswersService,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       deleteAFTChargeService: DeleteAFTChargeService,
                                       formProvider: DeleteFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteCharge.error.required", messages("chargeF").toLowerCase()))

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async {
      implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

            val viewModel = GenericViewModel(
              submitUrl = routes.DeleteChargeController.onSubmit(srn, startDate).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
              schemeName = schemeName
            )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> form,
          "viewModel" -> viewModel,
          "radios" -> Radios.yesNo(form(implicitly)("value")),
          "chargeName" -> "chargeF"
        )

        renderer.render("deleteCharge.njk", json).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {

                  val viewModel = GenericViewModel(
                    submitUrl = routes.DeleteChargeController.onSubmit(srn, startDate).url,
                    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                    schemeName = schemeName
                  )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> Radios.yesNo(formWithErrors("value")),
                "chargeName" -> "chargeF"
              )

              renderer.render("deleteCharge.njk", json).map(BadRequest(_))

            },
            value =>
              if (value) {
                DataRetrievals.retrievePSTR { pstr =>
                  for {
                    updatedAnswers <- Future.fromTry(userAnswersService.remove(DeregistrationQuery))
                    answersJs <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                    _ <- deleteAFTChargeService.deleteAndFileAFTReturn(pstr, UserAnswers(answersJs.as[JsObject]), Some(DeregistrationQuery.path))
                  } yield Redirect(navigator.nextPage(DeleteChargePage, NormalMode, UserAnswers(answersJs.as[JsObject]), srn, startDate))
                }
              } else {
                Future.successful(Redirect(controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate)))
            }
          )
      }
    }
}
