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
import forms.{AFTSummaryFormProvider, MemberSearchFormProvider}
import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{GenericViewModel, Mode, NormalMode, Quarters, UserAnswers}
import navigators.CompoundNavigator
import pages.AFTSummaryPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services._
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

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
    schemeService: SchemeService,
    memberSearchService: MemberSearchService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def nunjucksTemplate = "aftSummary.njk"

  private val form = formProvider()
  private val memberSearchForm = memberSearchFormProvider()

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
            optionVersion
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
              val json = getJson(form, formWithErrors, ua, srn, startDate, schemeDetails.schemeName, optionVersion)
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              val preparedForm: Form[String] = memberSearchForm.fill(value)
              val searchResults = memberSearchService.search(ua, srn, startDate, value)
              val json =
                getJsonCommon(form, preparedForm, srn, startDate, schemeDetails.schemeName, optionVersion) ++
                  Json.obj("list" -> searchResults) ++
                  Json.obj("aftSummaryURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, optionVersion).url)

              renderer.render(template = nunjucksTemplate, json).map(Ok(_))
            }
          )
      }
    }

  def onSubmit(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndQuarter { (schemeName, quarter) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val ua = request.userAnswers
              val json = getJson(formWithErrors, memberSearchForm, ua, srn, startDate, schemeName, optionVersion)
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              if (!value && aftService.isSubmissionDisabled(quarter.endDate)) {
                userAnswersCacheConnector.removeAll(request.internalId).map { _ => Redirect(config.managePensionsSchemeSummaryUrl.format(srn))
                }
              } else if (!value && request.isAmendment) {
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

  private def getJsonCommon(form: Form[Boolean],
                            formSearchText: Form[String],
                            srn: String,
                            startDate: LocalDate,
                            schemeName: String,
                            optionVersion: Option[String])(implicit request: DataRequest[_]): JsObject = {
    val endDate = Quarters.getQuarter(startDate).endDate
    val versionNumber = optionVersion.getOrElse(request.aftVersion.toString)

    val viewAllAmendmentsLink = aftSummaryHelper.viewAmendmentsLink(versionNumber, srn, startDate)

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "formSearchText" -> formSearchText,
      "isAmendment" -> request.isAmendment,
      "viewAllAmendmentsLink" -> viewAllAmendmentsLink.toString(),
      "viewModel" -> viewModel(NormalMode, srn, startDate, schemeName, optionVersion),
      "radios" -> Radios.yesNo(form("value")),
      "quarterStartDate" -> startDate.format(dateFormatterStartDate),
      "quarterEndDate" -> endDate.format(dateFormatterDMY),
      "canChange" -> request.sessionData.isEditable,
      "searchURL" -> controllers.routes.AFTSummaryController.onSearchMember(srn, startDate, optionVersion).url
    )
  }

  private def getJson(form: Form[Boolean],
                      formSearchText: Form[String],
                      ua: UserAnswers,
                      srn: String,
                      startDate: LocalDate,
                      schemeName: String,
                      optionVersion: Option[String])(implicit request: DataRequest[AnyContent]): JsObject =
    getJsonCommon(form, formSearchText, srn, startDate, schemeName, optionVersion) ++ Json.obj(
      "list" -> aftSummaryHelper.summaryListData(ua, srn, startDate))

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, startDate, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
      schemeName = schemeName
    )
  }
}
