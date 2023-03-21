/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.chargeB

import config.FrontendAppConfig
import controllers.DataRetrievals
import controllers.actions._
import forms.DeleteFormProvider
import helpers.ErrorHelper.recoverFrom5XX
import models.LocalDateBinder._
import models.{AccessType, GenericViewModel, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeB.{DeleteChargePage, SpecialDeathBenefitsQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{DeleteAFTChargeService, UUIDService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DeleteChargeController @Inject()(override val messagesApi: MessagesApi,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       deleteAFTChargeService: DeleteAFTChargeService,
                                       userAnswersService: UserAnswersService,
                                       formProvider: DeleteFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer,
                                       uuidService: UUIDService)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteCharge.error.required", messages("chargeB").toLowerCase()))

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>

          val viewModel = GenericViewModel(
            submitUrl = routes.DeleteChargeController.onSubmit(uuidService.v4, srn, startDate, accessType, version).url,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName
          )

          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> Some(localDateToString(startDate)),
            "form" -> form,
            "viewModel" -> viewModel,
            "radios" -> Radios.yesNo(form(implicitly)("value")),
            "chargeName" -> "chargeB"
          )

          renderer.render("deleteCharge.njk", json).map(Ok(_))
        }
    }

  def onSubmit(requestId:String, srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.DeleteChargeController.onSubmit(requestId, srn, startDate, accessType, version).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> Radios.yesNo(formWithErrors("value")),
                "chargeName" -> "chargeB"
              )

              renderer.render("deleteCharge.njk", json).map(BadRequest(_))

            },
            value =>
              if (value) {
                DataRetrievals.retrievePSTR { pstr =>
                  val userAnswers: UserAnswers = userAnswersService.removeSchemeBasedCharge(SpecialDeathBenefitsQuery)
                  (for {
                      _ <- deleteAFTChargeService.deleteAndFileAFTReturn(requestId, pstr, userAnswers)
                    } yield {
                    Redirect(navigator.nextPage(DeleteChargePage, NormalMode, userAnswers, srn, startDate, accessType, version))
                  }) recoverWith recoverFrom5XX(srn, startDate)
                }
              } else {
                Future.successful(Redirect(controllers.chargeB.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)))
              }
          )
      }
    }

}
