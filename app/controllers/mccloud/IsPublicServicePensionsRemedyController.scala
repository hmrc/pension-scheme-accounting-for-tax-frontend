/*
 * Copyright 2022 HM Revenue & Customs
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
import models.{AccessType, ChargeType, GenericViewModel, Index, Mode}
import navigators.CompoundNavigator
import pages.mccloud.IsPublicServicePensionsRemedyPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class IsPublicServicePensionsRemedyController @Inject()(override val messagesApi: MessagesApi,
                                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                                        userAnswersService: UserAnswersService,
                                                        navigator: CompoundNavigator,
                                                        identify: IdentifierAction,
                                                        getData: DataRetrievalAction,
                                                        allowAccess: AllowAccessActionProvider,
                                                        requireData: DataRequiredAction,
                                                        formProvider: YesNoFormProvider,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(memberName: String)(implicit messages: Messages): Form[Boolean] =
    formProvider(messages("isPublicServicePensionsRemedy.error.required", memberName))

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

          val viewModel = GenericViewModel(
            submitUrl = routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName
          )

          val preparedForm = request.userAnswers.get(IsPublicServicePensionsRemedyPage(chargeType, index)) match {
            case None        => form(chargeTypeDescription)
            case Some(value) => form(chargeTypeDescription).fill(value)
          }

          val json = Json.obj(
            "srn" -> srn,
            "startDate" -> Some(localDateToString(startDate)),
            "form" -> preparedForm,
            "viewModel" -> viewModel,
            "radios" -> Radios.yesNo(preparedForm("value")),
            "chargeTypeDescription" -> chargeTypeDescription
          )

          renderer.render("mccloud/isPublicServicePensionsRemedy.njk", json).map(Ok(_))

        }
    }

  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")
        form(chargeTypeDescription)
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "radios" -> Radios.yesNo(formWithErrors("value")),
                "chargeTypeDescription" -> chargeTypeDescription
              )
              renderer.render("mccloud/isPublicServicePensionsRemedy.njk", json).map(BadRequest(_))

            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(IsPublicServicePensionsRemedyPage(chargeType, index), value, mode))
                _ <- userAnswersCacheConnector
                  .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = Some(index.id))
              } yield
                Redirect(
                  navigator.nextPage(IsPublicServicePensionsRemedyPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
          )

      }
    }
}
