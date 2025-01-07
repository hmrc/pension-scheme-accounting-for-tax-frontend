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

package controllers.chargeD

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import handlers.ErrorHandler
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, AddMembersViewModel, ChargeType, Member, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeD.AddMembersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.AddMembersService.mapChargeXMembersToTableTwirlMigration
import services.ChargePaginationService
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.DateHelper.dateFormatterDMY
import viewmodels.TwirlRadios
import views.html.chargeD.AddMembersView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AddMembersController @Inject()(override val messagesApi: MessagesApi,
                                     userAnswersCacheConnector: UserAnswersCacheConnector,
                                     navigator: CompoundNavigator,
                                     identify: IdentifierAction,
                                     getData: DataRetrievalAction,
                                     allowAccess: AllowAccessActionProvider,
                                     requireData: DataRequiredAction,
                                     formProvider: AddMembersFormProvider,
                                     val controllerComponents: MessagesControllerComponents,
                                     chargePaginationService: ChargePaginationService,
                                     deleteChargeHelper: DeleteChargeHelper,
                                     errorHandler:ErrorHandler,
                                     view: AddMembersView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def form: Form[Boolean] = formProvider("chargeD.addMembers.error")

  private def renderPage(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int)(implicit
    request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) =>
        renderPageWithStatus(srn, startDate, form, schemeName, quarter, accessType, version, pageNumber, Ok)
      case _ => futureSessionExpired
    }
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      renderPage(srn, startDate, accessType, version, pageNumber = 1)
    }

  def onPageLoadWithPageNo(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      renderPage(srn, startDate, accessType, version, pageNumber)
    }

  private def futureSessionExpired:Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
              case (Some(schemeName), Some(quarter)) =>
                renderPageWithStatus(srn, startDate, formWithErrors, schemeName, quarter, accessType, version, pageNumber, BadRequest)
              case _ => futureSessionExpired
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddMembersPage, value))
              _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                chargeType = Some(ChargeType.ChargeTypeLifetimeAllowance))
            } yield Redirect(navigator.nextPage(AddMembersPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
  }

  // scalastyle:off parameter.number
  private def renderPageWithStatus(srn: String,
    startDate: LocalDate,
    form: Form[_],
    schemeName: String,
    quarter: AFTQuarter,
    accessType: AccessType,
    version: Int,
    pageNumber: Int,
    status: Status
  )(implicit request: DataRequest[AnyContent]):  Future[Result]  = {

    val optionPaginatedMembersInfo = chargePaginationService.getItemsPaginated(
      pageNo = pageNumber,
      ua = request.userAnswers,
      viewUrl = viewUrl(srn, startDate, accessType, version),
      removeUrl = removeUrl(srn, startDate, request.userAnswers, accessType, version),
      chargeType = ChargeType.ChargeTypeLifetimeAllowance
    )

    optionPaginatedMembersInfo.map { pmi =>
      val viewModel = AddMembersViewModel(
        schemeName = schemeName,
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
        canChange = !request.isViewOnly,
        quarterStart = quarter.startDate.format(dateFormatterDMY),
        quarterEnd = quarter.endDate.format(dateFormatterDMY),
        paginationStatsStartMember = pmi.paginationStats.startMember,
        paginationStatsLastMember = pmi.paginationStats.lastMember,
        paginationStatsTotalMembers = pmi.paginationStats.totalMembers,
        radios= TwirlRadios.yesNo(form("value"))
      )
      val submitCall = routes.AddMembersController.onSubmit(srn, startDate, accessType, version, pageNumber)
      val table = mapToTable(pmi.membersForCurrentPage, !request.isViewOnly, pmi.paginationStats.totalAmount)
      val pageLinksSeq = chargePaginationService.pagerNavSeq(
        pmi.paginationStats,
        controllers.chargeD.routes.AddMembersController.onPageLoadWithPageNo(srn, startDate, accessType, version, _)
      )
      Future.successful(status(view(form, viewModel, submitCall, table, pageLinksSeq)))
    }.getOrElse(Future.successful(NotFound))
  }

  private def mapToTable(members: Seq[Member], canChange: Boolean, totalAmount:BigDecimal)(implicit messages: Messages): Table =
    mapChargeXMembersToTableTwirlMigration("chargeD", members, canChange, Some(totalAmount))

  private def viewUrl(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Int => Call =
    controllers.chargeD.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, _)

  private def removeUrl(srn: String, startDate: LocalDate, ua: UserAnswers,
    accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int => Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeD.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, _)
    } else {
      controllers.chargeD.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, _)
    }

}
