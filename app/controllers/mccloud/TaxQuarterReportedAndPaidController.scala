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
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.QuartersFormProvider
import models.Index.indexToInt
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, ChargeType, GenericViewModel, Index, Mode, Quarters, YearRange}
import navigators.CompoundNavigator
import pages.mccloud.{TaxQuarterReportedAndPaidPage, TaxYearReportedAndPaidPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.{QuartersService, SchemeService, UserAnswersService}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

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
                                    aftConnector: AFTConnector,
                                    quartersService: QuartersService,
                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                    userAnswersService: UserAnswersService
                                  )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(year: String, quarters: Seq[AFTQuarter])(implicit messages: Messages): Form[AFTQuarter] =
    formProvider(messages("quarters.error.required", year), quarters)

  def onPageLoadWithIndex(chargeType: ChargeType,
                 mode: Mode, srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index,
                 schemeIndex: Index
                ): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData andThen
    allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
    get(chargeType, mode, srn, startDate, accessType, version, index, Some(schemeIndex))
  }

  //scalastyle:off parameter.number
  private def get(chargeType: ChargeType,
                  mode: Mode,
                  srn: String,
                  startDate: LocalDate,
                  accessType: AccessType,
                  version: Int,
                  index: Index,
                  schemeIndex: Option[Index])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))).map(_.startYear) match {
      case Some(year) =>
        schemeService.retrieveSchemeDetails(
          psaId = request.idOrException,
          srn = srn,
          schemeIdType = "srn"
        ) flatMap { schemeDetails =>
          quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
            if (displayQuarters.nonEmpty) {
              val quarters = displayQuarters.map(_.quarter)

              val vm = GenericViewModel(
                submitUrl = routes.TaxQuarterReportedAndPaidController
                  .onSubmitWithIndex(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex.get).url,
                returnUrl = config.schemeDashboardUrl(request).format(srn),
                schemeName = schemeDetails.schemeName
              )

              val preparedForm: Form[AFTQuarter] =
                request.userAnswers.get(TaxQuarterReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))) match {
                case Some(value) => form(year, quarters).fill(value)
                case None => form(year, quarters)
              }
              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(localDateToString(startDate)),
                "form" -> preparedForm,
                "radios" -> Quarters.radios(form(year, quarters), displayQuarters),
                "viewModel" -> vm,
                "year" -> year
              )

              renderer.render(template = "mccloud/taxQuarterReportedAndPaid.njk", json).map(Ok(_))
            } else {
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            }
          }
        }
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }

  }

  // scalastyle:off method.length
  def onSubmitWithIndex(chargeType: ChargeType,
               mode: Mode, srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index,
               schemeIndex: Index
              ): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData andThen
    allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
    post(chargeType, mode, srn, startDate, accessType, version, index, Some(schemeIndex))
  }

  //scalastyle:off parameter.number
  private def post(chargeType: ChargeType,
                   mode: Mode,
                   srn: String,
                   startDate: LocalDate,
                   accessType: AccessType,
                   version: Int,
                   index: Index,
                   schemeIndex: Option[Index])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(TaxYearReportedAndPaidPage(chargeType, index, schemeIndex.map(indexToInt))).map(_.startYear) match {
      case Some(year) =>
        schemeService.retrieveSchemeDetails(request.idOrException, srn, "srn") flatMap { schemeDetails =>
          quartersService.getStartQuarters(srn, schemeDetails.pstr, year.toInt).flatMap { displayQuarters =>
            if (displayQuarters.nonEmpty) {
              val quarters = displayQuarters.map(_.quarter)
              form(year, quarters)
                .bindFromRequest()
                .fold(
                  formWithErrors => {
                    val vm = GenericViewModel(
                      submitUrl = routes.TaxQuarterReportedAndPaidController
                        .onSubmitWithIndex(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex.get).url,
                      returnUrl = config.schemeDashboardUrl(request).format(srn),
                      schemeName = schemeDetails.schemeName
                    )

                    val json = Json.obj(
                      fields = "srn" -> srn,
                      "startDate" -> None,
                      "form" -> formWithErrors,
                      "radios" -> Quarters.radios(formWithErrors, displayQuarters),
                      "viewModel" -> vm,
                      "year" -> year
                    )
                    renderer.render(template = "mccloud/taxQuarterReportedAndPaid.njk", json).map(BadRequest(_))
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
            } else {
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            }
          }
        }
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  case class InvalidValueSelected(details: String) extends Exception(s"The selected quarter did not match any quarters in the list of options: $details")
}
