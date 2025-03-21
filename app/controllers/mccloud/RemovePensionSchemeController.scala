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
import models.{AccessType, ChargeType, Index, Mode, UserAnswers}
import navigators.CompoundNavigator
import pages.mccloud.{RemovePensionSchemePage, SchemePathHelper, WasAnotherPensionSchemePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import viewmodels.TwirlRadios
import views.html.mccloud.RemovePensionScheme

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class RemovePensionSchemeController @Inject()(override val messagesApi: MessagesApi,
                                              userAnswersCacheConnector: UserAnswersCacheConnector,
                                              userAnswersService: UserAnswersService,
                                              navigator: CompoundNavigator,
                                              identify: IdentifierAction,
                                              getData: DataRetrievalAction,
                                              allowAccess: AllowAccessActionProvider,
                                              requireData: DataRequiredAction,
                                              formProvider: YesNoFormProvider,
                                              val controllerComponents: MessagesControllerComponents,
                                              removePensionSchemeView: RemovePensionScheme)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index,
                 schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")

          val preparedForm = request.userAnswers.get(RemovePensionSchemePage(chargeType, index, schemeIndex)) match {
            case None => form(chargeTypeDescription)
            case Some(value) => form(chargeTypeDescription).fill(value)
          }

          Future.successful(Ok(removePensionSchemeView(
            form = preparedForm,
            radios = TwirlRadios.yesNo(preparedForm("value")),
            submitCall = routes.RemovePensionSchemeController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName
          )))
        }
    }

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("removePensionScheme.error.required", memberName))

  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index,
               schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")
        form(chargeTypeDescription)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(BadRequest(removePensionSchemeView(
                form = formWithErrors,
                radios = TwirlRadios.yesNo(formWithErrors("value")),
                submitCall = routes.RemovePensionSchemeController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )))
            },
            value =>
              if (value) {
                for {
                  updatedAnswers <- if (pensionsSchemeCount(request.userAnswers, chargeType, index) > 1) {
                    Future.fromTry(Success(request.userAnswers.removeWithPath(SchemePathHelper.schemePath(chargeType, index, schemeIndex))))
                  } else {
                    Future.fromTry(request.userAnswers.removeWithPath(SchemePathHelper.path(chargeType, index))
                      .removeWithCleanup(WasAnotherPensionSchemePage(chargeType, index)))
                  }
                  _ <- userAnswersCacheConnector
                    .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = Some(index.id))
                  updatedAnswers <- Future.fromTry(userAnswersService.set(RemovePensionSchemePage(chargeType, index, schemeIndex), value, mode))
                } yield
                  Redirect(
                    navigator
                      .nextPage(RemovePensionSchemePage(chargeType, index, schemeIndex), mode, updatedAnswers, srn, startDate, accessType, version))
              } else {
                for {
                  updatedAnswers <- Future.fromTry(userAnswersService.set(RemovePensionSchemePage(chargeType, index, schemeIndex), value, mode))
                } yield
                  Redirect(
                    navigator
                      .nextPage(RemovePensionSchemePage(chargeType, index, schemeIndex), mode, updatedAnswers, srn, startDate, accessType, version))
              }
          )
      }
    }

  private def pensionsSchemeCount(userAnswers: UserAnswers, chargeType: ChargeType, index: Int): Int = {
    SchemePathHelper.path(chargeType, index).readNullable[JsArray].reads(userAnswers.data).asOpt.flatten.map(_.value.size).getOrElse(0)
  }
}
