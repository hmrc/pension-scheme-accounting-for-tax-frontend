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
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AFTSummaryFormProvider
import javax.inject.Inject
import models.requests.OptionalDataRequest
import models.{GenericViewModel, Mode, NormalMode, SchemeDetails, UserAnswers}
import navigators.CompoundNavigator
import pages.{AFTSummaryPage, PSTRQuery, QuarterPage, SchemeNameQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.SchemeService
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
                                      requireData: DataRequiredAction,
                                      formProvider: AFTSummaryFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      aftConnector: AFTConnector,
                                      schemeService: SchemeService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()
  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  private def getFormattedEndDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatter)

  private def getFormattedStartDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatterStartDate)

  def onPageLoad(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData(srn)).async {
    implicit request =>
      val futureJsonToPassToTemplate = for {
        schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
        userAnswersAfterRetrieve <- retrieveUserAnswers(optionVersion, schemeDetails)
        uaWithLock <- setLock(userAnswersAfterRetrieve)
        userAnswersAfterSave <- userAnswersCacheConnector
          .save(request.internalId, addSchemeDetailsToUserAnswers(uaWithLock, schemeDetails).data)
      } yield {
        val ua = UserAnswers(userAnswersAfterSave.as[JsObject])
        ua.get(QuarterPage).map { quarter =>
          Json.obj(
            "srn" -> srn,
            "form" -> form,
            "list" -> aftSummaryHelper.summaryListData(ua, srn),
            "viewModel" -> viewModel(NormalMode, srn, schemeDetails.schemeName, optionVersion),
            "radios" -> Radios.yesNo(form("value")),
            "startDate" -> getFormattedStartDate(quarter.startDate),
            "endDate" -> getFormattedEndDate(quarter.endDate),
            "canChange" -> !request.viewOnly
          )
        }
      }

      futureJsonToPassToTemplate
        .flatMap {
          case None => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          case Some(json) => renderer.render("aftSummary.njk", json).map(Ok(_))
        }

  }

  def onSubmit(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {
            val ua = request.userAnswers
            val optionJson = ua.get(QuarterPage).map { quarter =>
              Json.obj(
                "srn" -> srn,
                "form" -> formWithErrors,
                "list" -> aftSummaryHelper.summaryListData(ua, srn),
                "viewModel" -> viewModel(NormalMode, srn, schemeName, optionVersion),
                "radios" -> Radios.yesNo(form("value")),
                "startDate" -> getFormattedStartDate(quarter.startDate),
                "endDate" -> getFormattedEndDate(quarter.endDate),
                "canChange" -> !request.viewOnly
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
            } yield {

              Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, updatedAnswers, srn))
            }
        )
      }
  }

  private def setLock(ua: UserAnswers)(implicit request: OptionalDataRequest[_]): Future[UserAnswers] = {
    if(request.viewOnly) {
      Future.successful(ua)
    } else {
      userAnswersCacheConnector.setLock(request.internalId, ua.data).map(jsVal => UserAnswers(jsVal.as[JsObject]))
    }
  }

  def viewModel(mode: Mode, srn: String, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, version).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName)
  }

  private def retrieveUserAnswers(optionVersion: Option[String], schemeDetails: SchemeDetails)
                                 (implicit request: OptionalDataRequest[_]): Future[UserAnswers] =
    optionVersion match {
      case None => Future.successful(request.userAnswers.getOrElse(UserAnswers()))
      case Some(version) =>
        aftConnector.getAFTDetails(schemeDetails.pstr, "2020-04-01", version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

  private def addSchemeDetailsToUserAnswers(userAnswers: UserAnswers,
                                            schemeDetails: SchemeDetails): UserAnswers = {
    userAnswers.setOrException(SchemeNameQuery, schemeDetails.schemeName).
      setOrException(PSTRQuery, schemeDetails.pstr)
  }
}
