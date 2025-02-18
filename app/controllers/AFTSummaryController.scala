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

package controllers

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.{AFTSummaryFormProvider, MemberSearchFormProvider}
import helpers.AFTSummaryHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, NormalMode, Quarters, UserAnswers}
import navigators.CompoundNavigator
import pages.{AFTSummaryPage, ChargeTypePage}
import play.api.Logger
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.JsObject
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import play.twirl.api.Html
import services._
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}
import uk.gov.hmrc.govukfrontend.views.html.components.{Hint => GovukHint}
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.Text
import viewmodels.{AFTSummaryViewModel, Radios}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}
import views.html.AFTSummaryView

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
                                      aftSummaryView: AFTSummaryView,
                                      aftSummaryHelper: AFTSummaryHelper,
                                      schemeService: SchemeService,
                                      memberSearchService: MemberSearchService
                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {
  private val logger = Logger(classOf[AFTSummaryController])

  private val form = formProvider()
  private val memberSearchForm = memberSearchFormProvider()

  private def btnText(searchForm: Form[_])(implicit messages: Messages) = searchForm.value match {
    case Some(_) => messages("aft.summary.searchAgain.button")
    case _ => messages("aft.summary.search.button")
  }

  private def hint()(implicit messages: Messages) = GovukHint(
    content = Text(messages("aft.summary.search.hint"))
  )

  private def summarySearchHeadingText(searchForm: Form[_])(implicit messages: Messages) = searchForm.value match {
    case Some(_) => messages("aft.summary.heading.search.results") + " "
    case _ => ""
  }

  private def getAmendmentsLink(srn: String,
                                startDate: LocalDate,
                                version: Int,
                                accessType: AccessType)(implicit request: DataRequest[AnyContent]): Option[Html] = {
    if (request.isAmendment && (!request.isPrecompile || version > 2)) {
      Some(aftSummaryHelper.viewAmendmentsLink(version, srn, startDate, accessType))
    } else {
      None
    }
  }

  private def getAftSummaryViewModel(
                                      srn: String,
                                      startDate: LocalDate,
                                      version: Int,
                                      accessType: AccessType,
                                      schemeName: String
                                    )(implicit messages: Messages) = {
    AFTSummaryViewModel(
      aftSummaryURL = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
      returnHistoryURL = controllers.amend.routes.ReturnHistoryController.onPageLoad(srn, startDate).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
      searchHint = hint(),
      searchUrl = controllers.routes.AFTSummaryController.onSearchMember(srn, startDate, accessType, version),
      schemeName = schemeName,
      submitCall = routes.AFTSummaryController.onSubmit(srn, startDate, accessType, version)
    )
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen updateData(srn, startDate, version, accessType, optionCurrentPage = Some(AFTSummaryPage)) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage), version, accessType)).async { implicit request =>
      Try(request.userAnswers.data \ "chargeEDetails" \ "members" \\ "memberDetails") match {
        case Success(value) =>
          logger.info(s"Loading aft summary page: success getting member details: size = ${value.size}")
        case _ => ()
      }

      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap { schemeDetails =>
        val viewModel = getAftSummaryViewModel(srn, startDate, version, accessType, schemeDetails.schemeName)
        Future.successful(Ok(aftSummaryView(
          btnText = btnText(memberSearchForm),
          canChange = request.isEditable,
          form = form,
          memberSearchForm = memberSearchForm,
          summaryList = aftSummaryHelper.summaryListData(request.userAnswers, srn, startDate, accessType, version),
          membersList = Seq(),
          quarterEndDate = Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
          quarterStartDate = startDate.format(dateFormatterStartDate),
          radios = Radios.yesNo(form("value")),
          submissionNumber = getSubmissionNumber(schemeDetails.schemeName, version),
          summarySearchHeadingText = summarySearchHeadingText(memberSearchForm),
          viewAllAmendmentsLink = getAmendmentsLink(srn, startDate, version, accessType),
          viewModel
        )))
      }
    }

  //noinspection ScalaStyle
  def onSearchMember(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage), version, accessType)).async { implicit request =>

      logger.info("AFT summary controller on search member")

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
              val viewModel = getAftSummaryViewModel(srn, startDate, version, accessType, schemeDetails.schemeName)

              logger.warn("AFT summary controller on search member -- errors")
              logger.warn("AFT summary controller on search member -- got json")
              Future.successful(BadRequest(aftSummaryView(
                btnText = btnText(formWithErrors),
                canChange = request.isEditable,
                form = form,
                memberSearchForm = formWithErrors,
                summaryList = aftSummaryHelper.summaryListData(ua, srn, startDate, accessType, version),
                membersList = Seq(),
                quarterEndDate = Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
                quarterStartDate = startDate.format(dateFormatterStartDate),
                radios = Radios.yesNo(form("value")),
                submissionNumber = getSubmissionNumber(schemeDetails.schemeName, version),
                summarySearchHeadingText = summarySearchHeadingText(formWithErrors),
                viewAllAmendmentsLink = getAmendmentsLink(srn, startDate, version, accessType),
                viewModel
              )))
            },
            value => {
              logger.info(s"AFT summary controller on search member -- value = $value - about to search")
              val preparedForm: Form[String] = memberSearchForm.fill(value)
              val searchResults = memberSearchService.search(ua, srn, startDate, value, accessType, version)
              logger.info(s"AFT summary controller on search member -- searchResults size = ${searchResults.size}")
              val membersRows = memberSearchService.search(ua, srn, startDate, value, accessType, version)
              membersRows.map( row =>
                row.actions.map( action =>
                  action.items
                )
              )
              val viewModel = getAftSummaryViewModel(srn, startDate, version, accessType, schemeDetails.schemeName)

              Future.successful(Ok(aftSummaryView(
                btnText = btnText(preparedForm),
                canChange = request.isEditable,
                form = form,
                memberSearchForm = preparedForm,
                summaryList = aftSummaryHelper.summaryListData(request.userAnswers, srn, startDate, accessType, version),
                membersList = memberSearchService.search(ua, srn, startDate, value, accessType, version),
                quarterEndDate = Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
                quarterStartDate = startDate.format(dateFormatterStartDate),
                radios = Radios.yesNo(form("value")),
                submissionNumber = getSubmissionNumber(schemeDetails.schemeName, version),
                summarySearchHeadingText = summarySearchHeadingText(preparedForm),
                viewAllAmendmentsLink = getAmendmentsLink(srn, startDate, version, accessType),
                viewModel
              )))
            }
          )
      }
    }


  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, optionPage = Some(AFTSummaryPage), version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeAndQuarter { (schemeName, _) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val ua = request.userAnswers
              val viewModel = getAftSummaryViewModel(srn, startDate, version, accessType, schemeName)

              Future.successful(BadRequest(aftSummaryView(
                btnText = btnText(memberSearchForm),
                canChange = request.isEditable,
                form = formWithErrors,
                memberSearchForm = memberSearchForm,
                summaryList = aftSummaryHelper.summaryListData(ua, srn, startDate, accessType, version),
                membersList = Seq(),
                quarterEndDate = Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY),
                quarterStartDate = startDate.format(dateFormatterStartDate),
                radios = Radios.yesNo(formWithErrors("value")),
                submissionNumber = getSubmissionNumber(schemeName, version),
                summarySearchHeadingText = summarySearchHeadingText(memberSearchForm),
                viewAllAmendmentsLink = getAmendmentsLink(srn, startDate, version, accessType),
                viewModel
              )))
            },
            value => {
              for {
                userAnswers <- Future.fromTry(request.userAnswers.removeWithCleanup(ChargeTypePage))
                updatedAnswers <- Future.fromTry(userAnswers.set(AFTSummaryPage,value))
                _ <-  userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield {
                Redirect(navigator.nextPage(AFTSummaryPage, NormalMode, UserAnswers(updatedAnswers.data.as[JsObject]), srn, startDate, accessType, version))
              }
            }
          )
      }
    }

  private def getSubmissionNumber(schemeName: String, version: Int)(implicit request: DataRequest[_]) = {
    (request.isCompile, request.isAmendment, request.isViewOnly) match {
      case (true, true, _) =>  "Draft"
      case (true, false, _) => schemeName
      case _ => "Submission" + ' ' + version
    }
  }


}
