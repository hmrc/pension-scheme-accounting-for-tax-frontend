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

package controllers.chargeG

import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeG.ChargeAmountsFormProvider
import models.LocalDateBinder._
import models.chargeG.ChargeAmounts
import models.{AccessType, ChargeType, Index, Mode}
import navigators.CompoundNavigator
import pages.chargeG.{ChargeAmountsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeG.ChargeAmountsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChargeAmountsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswersService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeAmountsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        chargeAmountsView: ChargeAmountsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def form(memberName: String, minimumChargeValue:BigDecimal)(implicit messages: Messages): Form[ChargeAmounts] =
    formProvider(memberName, minimumChargeValueAllowed = minimumChargeValue)

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[ChargeAmounts] = request.userAnswers.get(ChargeAmountsPage(index)) match {
          case Some(value) => form(memberName, mininimumChargeValue).fill(value)
          case None        => form(memberName, mininimumChargeValue)
        }

        Future.successful(
          Ok(chargeAmountsView(
            preparedForm,
            memberName,
            routes.ChargeAmountsController.onSubmit(mode, srn, startDate, accessType, version, index),
            controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName
          ))
        )
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue:BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(memberName, mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(
                BadRequest(chargeAmountsView(
                  formWithErrors,
                  memberName,
                  routes.ChargeAmountsController.onSubmit(mode, srn, startDate, accessType, version, index),
                  controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                  schemeName
                ))
              )
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeAmountsPage(index), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeOverseasTransfer), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(ChargeAmountsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          )
      }
    }
}
