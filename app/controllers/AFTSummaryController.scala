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
import controllers.actions.{AllowAccessActionProvider, _}
import forms.AFTSummaryFormProvider
import javax.inject.Inject
import models.{GenericViewModel, Mode, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import pages.{AFTSummaryPage, PSTRQuery, QuarterPage, SchemeNameQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, Call, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.{AFTConstants, AFTSummaryHelper}

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
                                      aftService: AFTService,
                                      allowService: AllowAccessService
                                    )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()
  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  private def getFormattedEndDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatter)

  private def getFormattedStartDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatterStartDate)

  def onPageLoad(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData(srn)).async {
    implicit request =>
      aftService.retrieveAFTRequiredDetails(srn = srn, optionVersion = optionVersion).flatMap { case (schemeDetails, userAnswers) =>
        allowService.filterForIllegalPageAccess(srn, userAnswers).flatMap {
          case None =>
            val json = getJson(form, userAnswers, srn, schemeDetails.schemeName, optionVersion, !request.viewOnly)
            renderer.render("aftSummary.njk", json).map(Ok(_))
          case Some(redirectLocation) => Future.successful(redirectLocation)
        }
      }
  }

  def onSubmit(srn: String, optionVersion: Option[String]): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {
            val ua = request.userAnswers
            val json = getJson(formWithErrors, ua, srn, schemeName, optionVersion, !request.viewOnly)
            renderer.render("aftSummary.njk", json).map(BadRequest(_))
          },
          value =>
            if(value) {
             Future.successful(Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, request.userAnswers, srn)))
            } else {
              userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
                Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
              }
            }
        )
      }
  }

  private def getJson(form: Form[Boolean], ua: UserAnswers, srn: String, schemeName: String,
                      optionVersion: Option[String], canChange: Boolean)(implicit messages: Messages): JsObject = {
    val quarterStartDate = ua.get(QuarterPage).map(_.startDate).getOrElse(AFTConstants.QUARTER_START_DATE)
    val quarterEndDate = ua.get(QuarterPage).map(_.endDate).getOrElse(AFTConstants.QUARTER_END_DATE)
    Json.obj(
      "srn" -> srn,
      "form" -> form,
      "list" -> aftSummaryHelper.summaryListData(ua, srn),
      "viewModel" -> viewModel(NormalMode, srn, schemeName, optionVersion),
      "radios" -> Radios.yesNo(form("value")),
      "startDate" -> getFormattedStartDate(quarterStartDate),
      "endDate" -> getFormattedEndDate(quarterEndDate),
      "canChange" -> canChange
    )
  }

  private def viewModel(mode: Mode, srn: String, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, version).url,
      returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
      schemeName = schemeName)
  }
}
