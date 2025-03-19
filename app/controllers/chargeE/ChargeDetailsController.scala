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

package controllers.chargeE

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeE.ChargeDetailsFormProvider
import models.LocalDateBinder._
import models.chargeE.ChargeEDetails
import models.{AccessType, ChargeType, CommonQuarters, Index, Mode}
import navigators.CompoundNavigator
import pages.chargeE.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeE.ChargeDetailsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                        userAnswersService: UserAnswersService,
                                        navigator: CompoundNavigator,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        allowAccess: AllowAccessActionProvider,
                                        requireData: DataRequiredAction,
                                        formProvider: ChargeDetailsFormProvider,
                                        val controllerComponents: MessagesControllerComponents,
                                        config: FrontendAppConfig,
                                        view: ChargeDetailsView)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with CommonQuarters {

  private def form(minimumChargeValue: BigDecimal, startDate: LocalDate)(implicit messages: Messages): Form[ChargeEDetails] = {
    val endDate = getQuarter(startDate).endDate
    formProvider(
      minimumChargeValueAllowed = minimumChargeValue,
      minimumDate = config.earliestDateOfNotice,
      maximumDate = endDate
    )
  }

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue: BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[ChargeEDetails] = request.userAnswers.get(ChargeDetailsPage(index)) match {
          case Some(value) => form(mininimumChargeValue, startDate).fill(value)
          case None => form(mininimumChargeValue, startDate)
        }

        val submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version, index)
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

        Future.successful(Ok(view(preparedForm,
          schemeName,
          submitUrl,
          returnUrl,
          memberName,
          utils.Radios.yesNo(preparedForm("isPaymentMandatory"))))
        )
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndMember(MemberDetailsPage(index)) { (schemeName, memberName) =>

        val mininimumChargeValue: BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        form(mininimumChargeValue, startDate)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version, index)
              val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

              Future.successful(BadRequest(view(formWithErrors,
                schemeName,
                submitUrl,
                returnUrl,
                memberName,
                utils.Radios.yesNo(formWithErrors("isPaymentMandatory"))))
              )
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeDetailsPage(index), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeAnnualAllowance), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(ChargeDetailsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          )
      }
    }
}
