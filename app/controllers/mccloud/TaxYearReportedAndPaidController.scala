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
import forms.YearRangeFormProvider
import models.Index.indexToInt
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, Mode, YearRange, YearRangeMcCloud}
import navigators.CompoundNavigator
import pages.mccloud.TaxYearReportedAndPaidPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.mccloud.TaxYearReportedAndPaid

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TaxYearReportedAndPaidController @Inject()(override val messagesApi: MessagesApi,
                                                 userAnswersCacheConnector: UserAnswersCacheConnector,
                                                 userAnswersService: UserAnswersService,
                                                 navigator: CompoundNavigator,
                                                 identify: IdentifierAction,
                                                 getData: DataRetrievalAction,
                                                 allowAccess: AllowAccessActionProvider,
                                                 requireData: DataRequiredAction,
                                                 formProvider: YearRangeFormProvider,
                                                 val controllerComponents: MessagesControllerComponents,
                                                 taxYearReportedAndPaidView: TaxYearReportedAndPaid)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with CommonMcCloud {

  private def form: Form[YearRange] =
    formProvider("taxYearReportedAndPaid.error.required")

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
        val preparedForm: Form[YearRange] = request.userAnswers.get(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))) match {
          case Some(value) => form.fill(value)
          case None => form
        }

        twirlLifetimeOrAnnual(chargeType) match {
          case Some(chargeTypeDesc) =>
            val ordinalValue = ordinal(schemeIndex).map(_.value).getOrElse("")

            Future.successful(Ok(taxYearReportedAndPaidView(
              form = preparedForm,
              radios = YearRangeMcCloud.radios(preparedForm),
              ordinal = ordinalValue,
              chargeTypeDesc = chargeTypeDesc,
              submitCall = routes.TaxYearReportedAndPaidController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName
            )))
          case _ => sessionExpired
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
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              twirlLifetimeOrAnnual(chargeType) match {
                case Some(chargeTypeDesc) =>
                  val ordinalValue = ordinal(schemeIndex).map(_.value).getOrElse("")

                  Future.successful(BadRequest(taxYearReportedAndPaidView(
                    form = formWithErrors,
                    radios = YearRangeMcCloud.radios(formWithErrors),
                    ordinal = ordinalValue,
                    chargeTypeDesc = chargeTypeDesc,
                    submitCall = routes.TaxYearReportedAndPaidController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex),
                    returnUrl = controllers.routes.ReturnToSchemeDetailsController
                      .returnToSchemeDetails(srn, startDate, accessType, version).url,
                    schemeName = schemeName
                  )))
                case _ => sessionExpired
              }
            },
            value => {
              for {
                updatedAnswers <- Future.fromTry(userAnswersService
                  .set(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(chargeType), memberNo = Some(index.id))
              } yield {
                Redirect(navigator
                  .nextPage(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)),
                    mode, updatedAnswers, srn, startDate, accessType, version))
              }
            }
          )
      }
    }


}
