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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.mccloud.ChargeAmountReportedFormProvider
import models.Index._
import models.LocalDateBinder._
import models.{AFTQuarter, AccessType, ChargeType, CommonQuarters, GenericViewModel, Index, Mode}
import navigators.CompoundNavigator
import pages.mccloud.{ChargeAmountReportedPage, TaxQuarterReportedAndPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

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
                                               config: FrontendAppConfig,
                                               renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport
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

        val viewModel = GenericViewModel(
          submitUrl = routes.ChargeAmountReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val taxQuarterSelection = request.userAnswers.get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)))

        (taxQuarterSelection, lifetimeOrAnnual(chargeType)) match {
          case (Some(aftQuarter), Some(chargeTypeDesc)) =>
            val ordinalValue = ordinal(schemeIndex).map(_.resolve).getOrElse("")
            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> Some(localDateToString(startDate)),
              "form" -> preparedForm,
              "viewModel" -> viewModel,
              "periodDescription" -> AFTQuarter.formatForDisplay(aftQuarter),
              "ordinal" -> ordinalValue,
              "chargeTypeDesc" -> chargeTypeDesc
            )
            renderer.render(template = "mccloud/chargeAmountReported.njk", json).map(Ok(_))
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
              val viewModel = GenericViewModel(
                submitUrl = routes.ChargeAmountReportedController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )
              val taxQuarterSelection = request.userAnswers
                .get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)))

              (taxQuarterSelection, lifetimeOrAnnual(chargeType)) match {
                case (Some(aftQuarter), Some(chargeTypeDesc)) =>
                  val ordinalValue = ordinal(schemeIndex).map(_.resolve).getOrElse("")
                  val json = Json.obj(
                    "srn" -> srn,
                    "startDate" -> Some(localDateToString(startDate)),
                    "form" -> formWithErrors,
                    "viewModel" -> viewModel,
                    "periodDescription" -> AFTQuarter.formatForDisplay(aftQuarter),
                    "ordinal" -> ordinalValue,
                    "chargeTypeDesc" -> chargeTypeDesc
                  )
                  renderer.render(template = "mccloud/chargeAmountReported.njk", json).map(BadRequest(_))
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
