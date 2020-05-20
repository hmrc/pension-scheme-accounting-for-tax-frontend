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
import models.AccessMode.PageAccessModeCompile
import models.LocalDateBinder._
import models.{GenericViewModel, Mode, NormalMode, Quarters, UserAnswers}
import models.requests.DataRequest
import navigators.CompoundNavigator
import pages.AFTSummaryPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.{AFTService, AllowAccessService, RequestCreationService, SchemeService}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.AFTSummaryHelper
import utils.DateHelper.dateFormatterDMY
import play.twirl.api.Html

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
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    config: FrontendAppConfig,
    aftSummaryHelper: AFTSummaryHelper,
    aftService: AFTService,
    allowService: AllowAccessService,
    requestCreationService: RequestCreationService,
    schemeService: SchemeService
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()
  private val dateFormatterStartDate = DateTimeFormatter.ofPattern("d MMMM")

  private def getFormattedEndDate(date: LocalDate): String = date.format(dateFormatterDMY)

  private def getFormattedStartDate(date: LocalDate): String = date.format(dateFormatterStartDate)

  def onPageLoad(srn: String, startDate: LocalDate, optionVersion: Option[String]): Action[AnyContent] =
    (identify andThen updateData(srn, startDate, optionVersion, optionCurrentPage = Some(AFTSummaryPage)) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage))).async { implicit request =>
      schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap { schemeDetails =>
        val json =
          getJson(form, request.userAnswers, srn, startDate, schemeDetails.schemeName, optionVersion, request.sessionData.isEditable)
        renderer.render("aftSummary.njk", json).map(Ok(_))
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
              val json = getJson(formWithErrors, ua, srn, startDate, schemeName, optionVersion, request.sessionData.isEditable)
              renderer.render(template = "aftSummary.njk", json).map(BadRequest(_))
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

  private def getJson(form: Form[Boolean],
                      ua: UserAnswers,
                      srn: String,
                      startDate: LocalDate,
                      schemeName: String,
                      optionVersion: Option[String],
                      canChange: Boolean)(implicit request: DataRequest[AnyContent]): JsObject = {
    val endDate = Quarters.getQuarter(startDate).endDate
    val versionNumber = optionVersion.getOrElse(request.aftVersion.toString)
    val viewAllAmendmentsLink = viewAmendmentsLink(versionNumber, srn, startDate)

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "isAmendment" -> request.isAmendment,
      "viewAllAmendmentsLink" -> viewAllAmendmentsLink.toString(),
      "list" -> aftSummaryHelper.summaryListData(ua, srn, startDate),
      "viewModel" -> viewModel(NormalMode, srn, startDate, schemeName, optionVersion),
      "radios" -> Radios.yesNo(form("value")),
      "quarterStartDate" -> getFormattedStartDate(startDate),
      "quarterEndDate" -> getFormattedEndDate(endDate),
      "canChange" -> canChange
    )
  }

  private def viewAmendmentsLink(version: String, srn: String, startDate: LocalDate)(implicit messages: Messages,
                                                                                     request: DataRequest[AnyContent]): Html = {

    val linkText = if (request.sessionData.sessionAccessData.accessMode == PageAccessModeCompile) {
      messages("allAmendments.view.changes.draft.link")
    } else {
      messages("allAmendments.view.changes.submission.link")
    }
    val viewAllAmendmentsUrl = controllers.amend.routes.ViewAllAmendmentsController.onPageLoad(srn, startDate, version).url

    Html(s"${Html(s"""<a id=view-amendments-link href=$viewAllAmendmentsUrl class="govuk-link"> $linkText</a>""".stripMargin).toString()}")
  }

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, version: Option[String]): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, startDate, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
      schemeName = schemeName
    )
  }
}
