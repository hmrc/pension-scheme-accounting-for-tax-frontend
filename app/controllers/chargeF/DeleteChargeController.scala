/*
 * Copyright 2024 HM Revenue & Customs
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

import controllers.DataRetrievals
import controllers.actions._
import forms.YesNoFormProvider
import helpers.ErrorHelper.recoverFrom5XX
import models.LocalDateBinder._
import models.{AccessType, NormalMode}
import navigators.CompoundNavigator
import pages.chargeF.{DeleteChargePage, DeregistrationQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.{DeleteAFTChargeService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.DeleteChargeView

class DeleteChargeController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersService: UserAnswersService,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       allowAccess: AllowAccessActionProvider,
                                       requireData: DataRequiredAction,
                                       deleteAFTChargeService: DeleteAFTChargeService,
                                       formProvider: YesNoFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       deleteChargeView: DeleteChargeView)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("deleteCharge.error.required", messages("chargeF").toLowerCase()))

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          Future.successful(Ok(deleteChargeView(
            "chargeF",
            form,
            TwirlRadios.yesNo(form(implicitly)("value")),
            routes.DeleteChargeController.onSubmit(srn, startDate, accessType, version),
            controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName
          )))
        }
    }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(BadRequest(deleteChargeView(
                "chargeF",
                formWithErrors,
                TwirlRadios.yesNo(formWithErrors("value")),
                routes.DeleteChargeController.onSubmit(srn, startDate, accessType, version),
                controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName
              )))
            },
            value =>
              if (value) {
                DataRetrievals.retrievePSTR { pstr =>
                  val userAnswers = userAnswersService.removeSchemeBasedCharge(DeregistrationQuery)
                  (for {
                    _ <- deleteAFTChargeService.deleteAndFileAFTReturn(pstr, userAnswers)
                  } yield {
                    Redirect(navigator.nextPage(DeleteChargePage, NormalMode, userAnswers, srn, startDate, accessType, version))
                  }) recoverWith recoverFrom5XX(srn, startDate)
                }
              } else {
                Future.successful(Redirect(controllers.chargeF.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version)))
              }
          )
      }
    }
}
