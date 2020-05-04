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

package controllers.chargeA

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteFormProvider
import javax.inject.Inject
import models.GenericViewModel
import models.LocalDateBinder._
import navigators.CompoundNavigator
import pages.chargeA.ChargeDetailsPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class DeleteChargeController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       aftService: AFTService,
                                       formProvider: DeleteFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteCharge.error.required", messages("chargeA").toLowerCase()))

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
              "chargeName" -> "chargeA"
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
                    "chargeName" -> "chargeA"
                  )

                  renderer.render("deleteCharge.njk", json).map(BadRequest(_))

                },
                value =>
                  if (value) {
                    DataRetrievals.retrievePSTR {
                      pstr =>
                        val updatedAnswers = request.userAnswers.removeWithPath(ChargeDetailsPage.path)
                        for {
                          _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                          _ <- aftService.fileAFTReturn(pstr, updatedAnswers)
                        } yield Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None))
                    }
                  } else {
                    Future.successful(Redirect(controllers.chargeA.routes.CheckYourAnswersController.onPageLoad(srn, startDate)))
                  }
              )
      }
    }
}
