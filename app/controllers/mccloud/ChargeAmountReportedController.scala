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
import forms.mccloud.ChargeAmountReportedFormProvider
import models.Index._
import models.LocalDateBinder._
import models.{AFTQuarter, AccessType, ChargeType, CommonQuarters, Index, Mode}
import navigators.CompoundNavigator
import pages.mccloud.{ChargeAmountReportedPage, TaxQuarterReportedAndPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.mccloud.ChargeAmountReported

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ChargeAmountReportedController @Inject()(override val messagesApi: MessagesApi,
                                               userAnswersCacheConnector: UserAnswersCacheConnector,
                                               userAnswersService: UserAnswersService,
                                               navigator: CompoundNavigator,
                                               identify: IdentifierAction,
                                               getData: DataRetrievalAction,
                                               allowAccess: AllowAccessActionProvider,
                                               requireData: DataRequiredAction,
                                               formProvider: ChargeAmountReportedFormProvider,
                                               val controllerComponents: MessagesControllerComponents,
                                               chargeAmountReportedView: ChargeAmountReported)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with CommonQuarters
    with CommonMcCloud {

  private def form(minimumChargeValue: BigDecimal): Form[BigDecimal] = {
    formProvider(
      minimumChargeValueAllowed = minimumChargeValue
    )
  }

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index,
                 schemeIndex: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val mininimumChargeValue: BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed

        val preparedForm: Form[BigDecimal] = request.userAnswers
          .get(ChargeAmountReportedPage(chargeType, index, schemeIndex.map(indexToInt))) match {
          case Some(value) => form(mininimumChargeValue).fill(value)
          case None => form(mininimumChargeValue)
        }

        val taxQuarterSelection = request.userAnswers.get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)))

        (taxQuarterSelection, twirlLifetimeOrAnnual(chargeType)) match {
          case (Some(aftQuarter), Some(chargeTypeDesc)) =>
            val ordinalValue = ordinal(schemeIndex).map(_.value).getOrElse("")
            Future.successful(Ok(chargeAmountReportedView(
              form = preparedForm,
              submitCall = routes.ChargeAmountReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName,
              periodDescription = AFTQuarter.formatForDisplay(aftQuarter),
              ordinal = ordinalValue,
              chargeTypeDesc = chargeTypeDesc
            )))
          case _ =>
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        }
      }
    }


  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index,
               schemeIndex: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val mininimumChargeValue: BigDecimal = request.sessionData.deriveMinimumChargeValueAllowed
        form(mininimumChargeValue)
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val taxQuarterSelection = request.userAnswers
                .get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)))

              (taxQuarterSelection, twirlLifetimeOrAnnual(chargeType)) match {
                case (Some(aftQuarter), Some(chargeTypeDesc)) =>
                  val ordinalValue = ordinal(schemeIndex).map(_.value).getOrElse("")
                  Future.successful(BadRequest(chargeAmountReportedView(
                    form = formWithErrors,
                    submitCall = routes.ChargeAmountReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
                    returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                    schemeName = schemeName,
                    periodDescription = AFTQuarter.formatForDisplay(aftQuarter),
                    ordinal = ordinalValue,
                    chargeTypeDesc = chargeTypeDesc
                  )))
                case _ =>
                  Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
              }
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswersService
                  .set(ChargeAmountReportedPage(chargeType, index, schemeIndex.map(indexToInt)), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(chargeType), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(ChargeAmountReportedPage(chargeType, index,
                schemeIndex.map(indexToInt)), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          )
      }
    }


}
