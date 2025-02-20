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

import java.time.LocalDate
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeG.ChargeDetailsFormProvider

import javax.inject.Inject
import models.LocalDateBinder._
import models.chargeG.ChargeDetails
import models.{Quarters, AccessType, Mode, ChargeType, Index}
import navigators.CompoundNavigator
import pages.chargeG.{ChargeDetailsPage, MemberDetailsPage}
import play.api.data.Form
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import scala.concurrent.{ExecutionContext, Future}
import views.html.chargeG.ChargeDetailsView

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
                                        chargeDetailsView: ChargeDetailsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def form(startDate: LocalDate)(implicit messages: Messages): Form[ChargeDetails] = {
    val endDate = Quarters.getQuarter(startDate).endDate
    formProvider(startDate, endDate)
  }

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>
        val preparedForm = request.userAnswers.get(ChargeDetailsPage(index)) match {
          case Some(value) => form(startDate).fill(value)
          case None        => form(startDate)
        }

        Future.successful(Ok(chargeDetailsView(
          preparedForm,
          memberName,
          routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version, index),
          controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName
        )))
      }
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeMemberChargeG(MemberDetailsPage(index)) { (schemeName, memberName) =>
        form(startDate)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              Future.successful(BadRequest(chargeDetailsView(
                formWithErrors,
                memberName,
                routes.ChargeDetailsController.onSubmit(mode, srn, startDate, accessType, version, index),
                controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName
              )))
            },
            value => {
              val cleanedValue = {
                val qropsRefNo = value.qropsReferenceNumber

                if (qropsRefNo.startsWith("Q") || qropsRefNo.startsWith("q")) {
                  value.copy(qropsReferenceNumber = qropsRefNo.drop(1))
                } else {
                  value
                }
              }
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(ChargeDetailsPage(index), cleanedValue, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(ChargeType.ChargeTypeOverseasTransfer), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(ChargeDetailsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            })
      }
    }
}
