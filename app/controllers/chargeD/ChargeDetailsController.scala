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

package controllers.chargeD

import java.time.LocalDate
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeD.ChargeDetailsFormProvider

import javax.inject.Inject
import models.LocalDateBinder._
import models.{Quarters, GenericViewModel, AccessType, Mode, ChargeType, Index}
import models.chargeD.ChargeDDetails
import navigators.CompoundNavigator
import pages.chargeD.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswerService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(minimumChargeValue:BigDecimal, startDate: LocalDate)(implicit messages: Messages): Form[ChargeDDetails] = {
    val endDate = Quarters.getQuarter(startDate).endDate
    formProvider(
      startDate,
      endDate,
      minimumChargeValue
    )
  }

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate,
                        accessType: AccessType, version: Int, index: Index, schemeName: String): GenericViewModel =
    GenericViewModel(
      submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version, index).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      schemeName = schemeName
    )

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[ChargeDDetails] = request.userAnswers.get(ChargeDetailsPage(index)) match {
          case Some(value) => form(mininimumChargeValue, startDate).fill(value)
          case None        => form(mininimumChargeValue, startDate)
        }

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(localDateToString(startDate)),
          "form" -> preparedForm,
          "viewModel" -> viewModel(mode, srn, startDate, accessType, version, index, schemeName),
          "date" -> DateInput.localDate(preparedForm("dateOfEvent")),
          "memberName" -> memberName
        )

        renderer.render(template = "chargeD/chargeDetails.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(mininimumChargeValue, startDate)
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> formWithErrors,
                "viewModel" -> viewModel(mode, srn, startDate, accessType, version, index, schemeName),
                "date" -> DateInput.localDate(formWithErrors("dateOfEvent")),
                "memberName" -> memberName
              )
              renderer.render(template = "chargeD/chargeDetails.njk", json).map(BadRequest(_))
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswerService.set(ChargeDetailsPage(index), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeLifetimeAllowance), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(ChargeDetailsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          )
      }
    }
}
