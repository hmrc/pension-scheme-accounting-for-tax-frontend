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
import controllers.actions.AllowAccessActionProvider
import controllers.actions._
import forms.AFTSummaryFormProvider
import forms.MemberSearchFormProvider
import javax.inject.Inject
import models.AccessMode.PageAccessModeCompile
import models.LocalDateBinder._
import models.Quarters
import models.GenericViewModel
import models.Mode
import models.NormalMode
import models.UserAnswers
import models.requests.DataRequest
import navigators.CompoundNavigator
import pages.AFTSummaryPage
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import services.RequestCreationService
import services.AFTService
import services.AllowAccessService
import services.MemberSearchService
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Radios
import utils.AFTSummaryHelper
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AFTSummaryController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      updateData: DataUpdateAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: AFTSummaryFormProvider,
                                      memberSearchFormProvider: MemberSearchFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      config: FrontendAppConfig,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      aftService: AFTService,
                                      allowService: AllowAccessService,
                                      requestCreationService: RequestCreationService,
                                      schemeService: SchemeService,
                                      memberSearchService: MemberSearchService
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def nunjucksTemplate = "aftSummary.njk"

  private val form = formProvider()
  private val memberSearchForm = memberSearchFormProvider()
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  private def getFormattedEndDate(date: LocalDate): String = date.format(dateFormatterDMY)

  private def getFormattedStartDate(date: LocalDate): String = date.format(dateFormatterStartDate)

  def onPageLoad(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen updateData(srn, startDate, optionVersion, optionCurrentPage = Some(AFTSummaryPage)) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage))).async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        val json =
          getJson(
            form,
            memberSearchForm,
            request.userAnswers,
            srn,
            startDate,
            schemeDetails.schemeName,
            optionVersion,
            isEditable, isAmendmentInProgress
          )

        renderer.render(nunjucksTemplate, json).map(Ok(_))
      }
    }

  def onSearchMember(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage))).async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        val ua = request.userAnswers
        memberSearchForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val json = getJson( form, formWithErrors, ua, srn, startDate, schemeDetails.schemeName,
                optionVersion, isEditable, isAmendmentInProgress
              )
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              val preparedForm: Form[String] = memberSearchForm.fill(value)
              val searchResults = memberSearchService.search(ua, srn, startDate, value)
              val json =
                getJsonCommon(form, preparedForm, ua, srn, startDate, schemeDetails.schemeName, optionVersion,
                  isEditable, isAmendmentInProgress
                ) ++
                  Json.obj( "list" -> searchResults) ++
                  Json.obj("aftSummaryURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, optionVersion).url)

              renderer.render(template = nunjucksTemplate, json).map(Ok(_))
            }
          )
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndQuarterWithAmendment { (schemeName, quarter, isAmendment) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val ua = request.userAnswers
              val json = getJson( formWithErrors, memberSearchForm, ua, srn, startDate, schemeName, optionVersion, isEditable, isAmendmentInProgress)
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              if (!value && aftService.isSubmissionDisabled(quarter.endDate)) {
                userAnswersCacheConnector.removeAll(request.internalId).map { _ =>
                  Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
                }
              } else if (!value && isAmendment) {
                Future.successful(Redirect(controllers.amend.routes.ConfirmSubmitAFTAmendmentController.onPageLoad(srn, startDate)))
              } else {
                Future.fromTry(request.userAnswers.set(AFTSummaryPage, value)).flatMap { answers =>
                  userAnswersCacheConnector.save(request.internalId, answers.data).map { updatedAnswers =>
                    Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, UserAnswers(updatedAnswers.as[JsObject]), srn, startDate))
                  }
                }
              }
            }
          )
      }
    }

  private def isAmendmentInProgress(implicit request:DataRequest[_]):Boolean =
    (request.sessionData.sessionAccessData.accessMode == PageAccessModeCompile && request.isAmendment)

  private def isEditable(implicit request:DataRequest[_]):Boolean = request.sessionData.isEditable

  private def getJsonCommon(
                             form: Form[Boolean],
                             formSearchText: Form[String],
                             ua: UserAnswers,
                             srn: String,
                             startDate: LocalDate,
                             schemeName: String,
                             optionVersion: Option[String],
                             canChange: Boolean,
                             isAmendmentInProgress:Boolean)(implicit messages: Messages): JsObject = {
    val endDate = Quarters.getQuarter(startDate).endDate

    val viewAllAmendmentsLink = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, startDate).url

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "formSearchText" -> formSearchText,
      "isAmendmentInProgress" -> isAmendmentInProgress,
      "viewAllAmendmentsLink" -> viewAllAmendmentsLink,
      "viewModel" -> viewModel(NormalMode, srn, startDate, schemeName, optionVersion),
      "radios" -> Radios.yesNo(form("value")),
      "quarterStartDate" -> getFormattedStartDate(startDate),
      "quarterEndDate" -> getFormattedEndDate(endDate),
      "canChange" -> canChange,
      "searchURL" -> controllers.routes.AFTSummaryController.onSearchMember(srn, startDate, optionVersion).url
    )
  }

  private def getJson(form: Form[Boolean],
                      formSearchText: Form[String],
                      ua: UserAnswers,
                      srn: String,
                      startDate: LocalDate,
                      schemeName: String,
                      optionVersion: Option[String],
                      isEditable:Boolean,
                      isAmendmentInProgress:Boolean)(implicit messages: Messages): JsObject =
    getJsonCommon( form, formSearchText, ua,  srn, startDate, schemeName, optionVersion, isEditable, isAmendmentInProgress
    ) ++ Json.obj( "list" -> aftSummaryHelper.summaryListData(ua, srn, startDate))

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, startDate, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
      schemeName = schemeName
    )
  }
}