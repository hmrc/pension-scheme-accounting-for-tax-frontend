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

package controllers.mccloud

import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.YesNoFormProvider
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode}
import navigators.CompoundNavigator
import pages.IsPublicServicePensionsRemedyPage
import pages.mccloud.{IsChargeInAdditionReportedPage, SchemePathHelper}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios
import views.html.mccloud.IsChargeInAdditionReported

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IsChargeInAdditionReportedController @Inject()(override val messagesApi: MessagesApi,
                                                     userAnswersCacheConnector: UserAnswersCacheConnector,
                                                     userAnswersService: UserAnswersService,
                                                     navigator: CompoundNavigator,
                                                     identify: IdentifierAction,
                                                     getData: DataRetrievalAction,
                                                     allowAccess: AllowAccessActionProvider,
                                                     requireData: DataRequiredAction,
                                                     formProvider: YesNoFormProvider,
                                                     val controllerComponents: MessagesControllerComponents,
                                                     isChargeInAdditionReportedView: IsChargeInAdditionReported)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("isChargeInAdditionReported.error.required", memberName))

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")

          val preparedForm = request.userAnswers.get(IsChargeInAdditionReportedPage(chargeType, index)) match {
            case None => form(chargeTypeDescription)
            case Some(value) => form(chargeTypeDescription).fill(value)
          }

          Future.successful(Ok(isChargeInAdditionReportedView(
            form = preparedForm,
            radios = TwirlRadios.yesNo(preparedForm("value")),
            chargeTypeDesc = s"chargeType.description.${chargeType.toString}",
            submitCall = routes.IsChargeInAdditionReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index),
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName
          )))
        }
    }

  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")
        form(chargeTypeDescription)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(BadRequest(isChargeInAdditionReportedView(
                form = formWithErrors,
                radios = TwirlRadios.yesNo(formWithErrors("value")),
                chargeTypeDesc = s"chargeType.description.${chargeType.toString}",
                submitCall = routes.IsChargeInAdditionReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index),
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )))
            },
            value =>
              if (value) {
                for {
                  updatedAnswers <- Future.fromTry(userAnswersService.set(IsChargeInAdditionReportedPage(chargeType, index), value, mode))
                  _ <- userAnswersCacheConnector
                    .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = Some(index.id))
                } yield
                  Redirect(
                    navigator.nextPage(IsChargeInAdditionReportedPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
              } else {
                for {
                  updatedAnswers <- Future.fromTry(
                    request.userAnswers
                      .removeWithPath(SchemePathHelper.basePath(chargeType, index))
                      .setOrException(IsPublicServicePensionsRemedyPage(chargeType, Some(index)), true)
                      .set(IsChargeInAdditionReportedPage(chargeType, index), value))
                  _ <- userAnswersCacheConnector
                    .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = Some(index.id))
                } yield
                  Redirect(
                    navigator.nextPage(IsChargeInAdditionReportedPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
              }
          )
      }
    }
}
