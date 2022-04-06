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

package controllers

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.{AFTSummaryFormProvider, MemberSearchFormProvider}
import helpers.AFTSummaryHelper
import models.AccessMode.{PageAccessModeCompile, PageAccessModePreCompile, PageAccessModeViewOnly}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTVersion, AccessMode, AccessType, Draft, GenericViewModel, Mode, NormalMode, Quarters, Submission, UserAnswers}
import navigators.CompoundNavigator
import pages.AFTSummaryPage
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import renderer.Renderer
import services._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class AFTSummaryController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      updateData: DataSetupAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: AFTSummaryFormProvider,
                                      memberSearchFormProvider: MemberSearchFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      schemeService: SchemeService,
                                      memberSearchService: MemberSearchService
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {
  private val logger = Logger(classOf[AFTSummaryController])
  private def nunjucksTemplate = "aftSummary.njk"

  private val form = formProvider()
  private val memberSearchForm = memberSearchFormProvider()

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen updateData(srn, startDate, version, accessType, optionCurrentPage = Some(AFTSummaryPage)) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage), version, accessType)).async { implicit request =>
      Try(request.userAnswers.data \ "chargeEDetails" \ "members" \\ "memberDetails") match {
        case Success(value) =>
          logger.warn(s"Loading aft summary page: success getting member details: size = ${value.size}")
        case _ => ()
      }

      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap { schemeDetails =>
        val json =
          getJson(
            form,
            memberSearchForm,
            request.userAnswers,
            srn,
            startDate,
            schemeDetails.schemeName,
            version,
            accessType
          )

        renderer.render(nunjucksTemplate, json).map(Ok(_))
      }
    }

  def onSearchMember(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage), version, accessType)).async { implicit request =>

      logger.warn("AFT summary controller on search member")

      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap { schemeDetails =>
        val ua = request.userAnswers
        memberSearchForm
          .bindFromRequest()
          .fold(
            formWithErrors => {
              logger.warn("AFT summary controller on search member -- errors")
              val json = getJson(form, formWithErrors, ua, srn, startDate, schemeDetails.schemeName, version, accessType)
              logger.warn(s"AFT summary controller on search member -- got json")
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              logger.warn(s"AFT summary controller on search member -- value = $value - about to search")
              val preparedForm: Form[String] = memberSearchForm.fill(value)
              val searchResults = memberSearchService.search(ua, srn, startDate, value, accessType, version)
              logger.warn(s"AFT summary controller on search member -- searchResults size = ${searchResults.size}")
              val json =
                getJsonCommon(form, preparedForm, srn, startDate, schemeDetails.schemeName, version, accessType) ++
                  Json.obj("list" -> Json.toJson(searchResults)) ++
                  Json.obj("aftSummaryURL" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url)

              renderer.render(template = nunjucksTemplate, json).map(Ok(_))
            }
          )
      }
    }

  private def confirmationPanelText(schemeName: String, startDate: LocalDate, endDate: LocalDate,formTextValue:Option[String])(implicit messages: Messages): Html = {
    val quarterStartDate = startDate.format(dateFormatterStartDate)
    val quarterEndDate = endDate.format(dateFormatterDMY)
    if(formTextValue.isDefined){
      Html(s""" <span class=govuk-caption-xl>${schemeName}</span>${messages("aft.summary.heading.search.results")+" " + messages("aft.summary.heading", quarterStartDate, quarterEndDate)}""")
      }
    else {
        Html(s""" <span class=govuk-caption-xl>${schemeName}</span>${messages("aft.summary.heading", quarterStartDate, quarterEndDate)}""")
      }
  }

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeAndQuarter { (schemeName, _) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val ua = request.userAnswers
              val json = getJson(formWithErrors, memberSearchForm, ua, srn, startDate, schemeName, version, accessType)
              renderer.render(template = nunjucksTemplate, json).map(BadRequest(_))
            },
            value => {
              Future.fromTry(request.userAnswers.set(AFTSummaryPage, value)).flatMap { answers =>
                userAnswersCacheConnector.save(request.internalId, answers.data).map { updatedAnswers =>
                  Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, UserAnswers(updatedAnswers.as[JsObject]), srn, startDate, accessType, version))
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
                            version: Int,
                            accessType: AccessType)(implicit request: DataRequest[_]): JsObject = {
    val endDate = Quarters.getQuarter(startDate).endDate
    val getLegendHtml = Json.obj("summaryheadingtext" -> confirmationPanelText(schemeName, startDate, endDate,formSearchText.value).toString())
    val submissionNumber = if (request.isCompile ){
       "Draft"
    }
    else {
      "Submission" + ' ' + request.aftVersion.toString
    }


    val returnHistoryURL = if (request.areSubmittedVersionsAvailable) {
      Json.obj("returnHistoryURL" -> controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url)
    } else {
      Json.obj()
    }

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(localDateToString(startDate)),
      "form" -> form,
      "submissionNumber" -> submissionNumber,
      "formSearchText" -> formSearchText,
      "isAmendment" -> request.isAmendment,
      "viewModel" -> viewModel(NormalMode, srn, startDate, schemeName, version, accessType),
      "radios" -> Radios.yesNo(form("value")),
      "quarterStartDate" -> startDate.format(dateFormatterStartDate),
      "quarterEndDate" -> endDate.format(dateFormatterDMY),
      "canChange" -> request.isEditable,
      "searchURL" -> controllers.routes.AFTSummaryController.onSearchMember(srn, startDate, accessType, version).url
    ) ++ returnHistoryURL ++ getLegendHtml
  }

  //scalastyle:off parameter.number
  private def getJson(form: Form[Boolean],
                      formSearchText: Form[String],
                      ua: UserAnswers,
                      srn: String,
                      startDate: LocalDate,
                      schemeName: String,
                      version: Int,
                      accessType: AccessType)(implicit request: DataRequest[AnyContent]): JsObject = {
    val amendmentsLink = if (request.isAmendment && (!request.isPrecompile || version > 2)) {
      val viewAllAmendmentsLink = aftSummaryHelper.viewAmendmentsLink(version, srn, startDate, accessType)
      Json.obj(
        "viewAllAmendmentsLink" -> viewAllAmendmentsLink.toString()
      )
    } else {
      Json.obj()
    }
    getJsonCommon(form, formSearchText, srn, startDate, schemeName, version, accessType) ++
      Json.obj(
        "list" -> aftSummaryHelper.summaryListData(ua, srn, startDate, accessType, version)
      ) ++ amendmentsLink

  }

  private def viewModel(mode: Mode, srn: String, startDate: LocalDate, schemeName: String, version: Int, accessType: AccessType): GenericViewModel = {
    GenericViewModel(
      submitUrl = routes.AFTSummaryController.onSubmit(srn, startDate, accessType, version).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      schemeName = schemeName
    )
  }
}
