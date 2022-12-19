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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import controllers.mccloud.TaxQuarterReportedAndPaidController.{filterQuarters, fullYearRange}
import forms.QuartersFormProvider
import models.Index.indexToInt
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, ChargeType, CommonQuarters, DisplayQuarter, GenericViewModel, Index, Mode, Quarters, YearRange}
import navigators.CompoundNavigator
import pages.mccloud.{TaxQuarterReportedAndPaidPage, TaxYearReportedAndPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc._
import renderer.Renderer
import services.{SchemeService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class TaxQuarterReportedAndPaidController @Inject()(
                                                     override val messagesApi: MessagesApi,
                                                     identify: IdentifierAction,
                                                     getData: DataRetrievalAction,
                                                     allowAccess: AllowAccessActionProvider,
                                                     requireData: DataRequiredAction,
                                                     navigator: CompoundNavigator,
                                                     formProvider: QuartersFormProvider,
                                                     val controllerComponents: MessagesControllerComponents,
                                                     renderer: Renderer,
                                                     config: FrontendAppConfig,
                                                     schemeService: SchemeService,
                                                     userAnswersCacheConnector: UserAnswersCacheConnector,
                                                     userAnswersService: UserAnswersService
                                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport
    with CommonMcCloud
    with CommonQuarters {

  private def form(quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("taxQuarterReportedAndPaid.error.required"), quarters)

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
      get(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex)
    }

  //scalastyle:off parameter.number
  private def get(chargeType: ChargeType, mode: Mode, srn: String, startDate: LocalDate,
                  accessType: AccessType, version: Int,
                  index: Index, schemeIndex: Option[Index])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))).map(fullYearRange) match {
      case Some(yearRange) =>
        schemeService.retrieveSchemeDetails(
          psaId = request.idOrException,
          srn = srn,
          schemeIdType = "srn"
        ) flatMap { schemeDetails =>
          val displayQuarters = getAllQuartersForYear(yearRange.startYear).filter(filterQuarters)
          val vm = GenericViewModel(
            submitUrl = routes.TaxQuarterReportedAndPaidController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
            returnUrl = config.schemeDashboardUrl(request).format(srn),
            schemeName = schemeDetails.schemeName
          )
          val preparedForm: Form[AFTQuarter] = {
            val quarters = displayQuarters.map(_.quarter)
            request.userAnswers.get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))) match {
              case Some(value) => form(quarters).fill(value)
              case None => form(quarters)
            }
          }
          lifetimeOrAnnual(chargeType) match {
            case Some(chargeTypeDesc) =>
              val ordinalValue = ordinal(schemeIndex).map(_.resolve).getOrElse("")
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> preparedForm,
                "radios" -> Quarters.radios(preparedForm, displayQuarters),
                "viewModel" -> vm,
                "year" -> yearRange.toString,
                "ordinal" -> ordinalValue,
                "chargeTypeDesc" -> chargeTypeDesc
              )
              renderer.render(template = "mccloud/taxQuarterReportedAndPaid.njk", json).map(Ok(_))
            case _ => sessionExpired
          }
        }
      case _ => sessionExpired
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
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      post(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex)
    }

  // scalastyle:off method.length
  //scalastyle:off parameter.number
  private def post(chargeType: ChargeType,
                   mode: Mode,
                   srn: String,
                   startDate: LocalDate,
                   accessType: AccessType,
                   version: Int,
                   index: Index,
                   schemeIndex: Option[Index])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))).map(fullYearRange) match {
      case Some(yearRange) =>
        schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn") flatMap { schemeDetails =>
          val displayQuarters = getAllQuartersForYear(yearRange.startYear).filter(filterQuarters)
          form(displayQuarters.map(_.quarter))
            .bindFromRequest()
            .fold(
              formWithErrors => {
                val vm = GenericViewModel(
                  submitUrl = routes.TaxQuarterReportedAndPaidController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
                  returnUrl = config.schemeDashboardUrl(request).format(srn),
                  schemeName = schemeDetails.schemeName
                )
                lifetimeOrAnnual(chargeType) match {
                  case Some(chargeTypeDesc) =>
                    val ordinalValue = ordinal(schemeIndex).map(_.resolve).getOrElse("")
                    val json = Json.obj(
                      fields = "srn" -> srn,
                      "startDate" -> None,
                      "form" -> formWithErrors,
                      "radios" -> Quarters.radios(formWithErrors, displayQuarters),
                      "viewModel" -> vm,
                      "year" -> yearRange.toString,
                      "ordinal" -> ordinalValue,
                      "chargeTypeDesc" -> chargeTypeDesc
                    )
                    renderer.render(template = "mccloud/taxQuarterReportedAndPaid.njk", json).map(BadRequest(_))
                  case _ => sessionExpired
                }
              },
              value => {
                for {
                  updatedAnswers <- Future.fromTry(userAnswersService
                    .set(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)), value, mode))
                  _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                    chargeType = Some(chargeType), memberNo = Some(index.id))
                } yield {
                  Redirect(navigator
                    .nextPage(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt)), mode, updatedAnswers,
                      srn, startDate, accessType, version))
                }
              }
            )
        }
      case _ => sessionExpired
    }
  }
}

object TaxQuarterReportedAndPaidController extends CommonQuarters {
  private val filterQuarters: DisplayQuarter => Boolean = {
    val earliestYear = 2015
    val quartersAfter = getQuarter(Q1, earliestYear).endDate
    dq => dq.quarter.startDate.isAfter(quartersAfter) && dq.quarter.endDate.isBefore(DateHelper.today)
  }

  private case class FullYearRange(startYear: Int, endYear: Int) {
    override def toString: String = startYear.toString + " to " + endYear.toString
  }

  private val fullYearRange: YearRange => FullYearRange = yr => {
    val yrStart = yr.toString.toInt
    val yrEnd = yrStart + 1
    FullYearRange(yrStart, yrEnd)
  }
}
