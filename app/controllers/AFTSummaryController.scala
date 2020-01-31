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

package controllers

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import controllers.actions.{AllowAccessActionProvider, _}
import forms.AFTSummaryFormProvider
import javax.inject.Inject
import models.requests.OptionalDataRequest
import models.{GenericViewModel, Mode, NormalMode, SchemeDetails, UserAnswers}
import navigators.CompoundNavigator
import pages._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AllowAccessService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper

import scala.concurrent.{ExecutionContext, Future}

class AFTSummaryController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: AFTSummaryFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      aftConnector: AFTConnector,
                                      schemeService: SchemeService,
                                      minimalPsaConnector: MinimalPsaConnector,
                                      allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()
  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  def onPageLoad(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData).async {
    implicit request =>
      (for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
        ua <- retrieveAndUpdateUserAnswers(optionVersion, schemeDetails)
        _ <- userAnswersCacheConnector.save(request.internalId, ua.data)
        filterAccess <- allowService.filterForIllegalPageAccess(srn, ua)
      } yield {
        filterAccess match {
          case None =>
            ua.get(QuarterPage) match {
              case Some(quarter) =>
                val json = Json.obj(
                  "form" -> form,
                  "list" -> aftSummaryHelper.summaryListData(ua, srn),
                  "viewModel" -> viewModel(NormalMode, srn, schemeDetails.schemeName, optionVersion),
                  "radios" -> Radios.yesNo(form("value")),
                  "startDate" -> getFormattedStartDate(quarter.startDate),
                  "endDate" -> getFormattedEndDate(quarter.endDate)
                )
                renderer.render("aftSummary.njk", json).map(Ok(_))
              case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          case Some(redirectLocation) => Future.successful(redirectLocation)
        }

      }).flatMap(identity)
  }

  def onSubmit(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {
            val ua = request.userAnswers
            val optionJson = ua.get(QuarterPage).map { quarter =>
              Json.obj(
                "form" -> formWithErrors,
                "list" -> aftSummaryHelper.summaryListData(ua, srn),
                "viewModel" -> viewModel(NormalMode, srn, schemeName, optionVersion),
                "radios" -> Radios.yesNo(form("value")),
                "startDate" -> getFormattedStartDate(quarter.startDate),
                "endDate" -> getFormattedEndDate(quarter.endDate)
              )
            }

            optionJson match {
              case None => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
              case Some(json) => renderer.render("aftSummary.njk", json).map(BadRequest(_))
            }
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AFTSummaryPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, updatedAnswers, srn))
        )
      }
  }

  private def viewModel(mode: Mode, srn: String, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, version).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName)
  }

  // TODO: Refactor this to make it clearer what's going on and to save having to retrieve suspended flag more than once
  private def retrieveAndUpdateUserAnswers(optionVersion: Option[String], schemeDetails: SchemeDetails)
                                                (implicit request: OptionalDataRequest[_]): Future[UserAnswers] = {

    val futureUserAnswers = optionVersion match {
      case None => Future.successful(request.userAnswers.getOrElse(UserAnswers()))
      case Some(version) =>
        aftConnector.getAFTDetails(schemeDetails.pstr, "2020-04-01", version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

    futureUserAnswers.flatMap { ua =>
      val futureUserAnswersWithSuspendedFlag = ua.get(IsPsaSuspendedQuery) match {
        case None =>
          minimalPsaConnector.isPsaSuspended(request.psaId.id).map { retrievedIsSuspendedValue =>
            ua.setOrException(IsPsaSuspendedQuery, retrievedIsSuspendedValue)
          }
        case Some(_) =>
          Future.successful(ua)
      }

      futureUserAnswersWithSuspendedFlag.map(_
        .setOrException(SchemeNameQuery, schemeDetails.schemeName)
        .setOrException(PSTRQuery, schemeDetails.pstr)
      )
    }
  }

  private def getFormattedEndDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatter)

  private def getFormattedStartDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatterStartDate)
}
