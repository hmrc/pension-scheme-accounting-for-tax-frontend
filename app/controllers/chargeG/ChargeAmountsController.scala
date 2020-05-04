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

package controllers.chargeG

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeG.ChargeAmountsFormProvider
import javax.inject.Inject
import models.chargeG.ChargeAmounts
import models.{Mode, GenericViewModel, UserAnswers, Index}
import navigators.CompoundNavigator
import pages.chargeG.{MemberDetailsPage, ChargeAmountsPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, MessagesControllerComponents, Action}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{Future, ExecutionContext}
import java.time.LocalDate

import models.LocalDateBinder._
import models.SessionData

class ChargeAmountsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeAmountsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def form(memberName: String, minimumChargeValue:BigDecimal)(implicit messages: Messages): Form[ChargeAmounts] =
    formProvider(memberName, minimumChargeValueAllowed = minimumChargeValue)

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[ChargeAmounts] = request.userAnswers.get(ChargeAmountsPage(index)) match {
          case Some(value) => form(memberName, mininimumChargeValue).fill(value)
          case None        => form(memberName, mininimumChargeValue)
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.ChargeAmountsController.onSubmit(mode, srn, startDate, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "memberName" -> memberName
        )

        renderer.render(template = "chargeG/chargeAmounts.njk", json).map(Ok(_))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(memberName, mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val viewModel = GenericViewModel(
                submitUrl = routes.ChargeAmountsController.onSubmit(mode, srn, startDate, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> formWithErrors,
                "viewModel" -> viewModel,
                "memberName" -> memberName
              )
              renderer.render(template = "chargeG/chargeAmounts.njk", json).map(BadRequest(_))
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeAmountsPage(index), value))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(ChargeAmountsPage(index), mode, updatedAnswers, srn, startDate))
            }
          )
      }
    }
}
