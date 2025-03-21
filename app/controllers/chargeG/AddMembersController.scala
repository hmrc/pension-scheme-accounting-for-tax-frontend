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

package controllers.chargeG

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import helpers.DeleteChargeHelper
import models.LocalDateBinder._
import models.chargeG.AddMembersViewModel
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, ChargeType, Member, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeG.AddMembersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.AddMembersService.mapChargeXMembersToTableTwirlMigration
import services.ChargePaginationService
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.dateFormatterDMY
import viewmodels.TwirlRadios
import views.html.chargeG.AddMembersView

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
                                     addMembersView: AddMembersView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def form: Form[Boolean] = formProvider("chargeG.addMembers.error")

  private def renderOnPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int)(implicit
    request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) =>
        renderPage(srn, startDate, form, schemeName, quarter, accessType, version, pageNumber, Ok)
      case _ => futureSessionExpired
    }
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      renderOnPageLoad(srn, startDate, accessType, version, pageNumber = 1)
    }

  def onPageLoadWithPageNo(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      renderOnPageLoad(srn, startDate, accessType, version, pageNumber)
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
                renderPage(srn, startDate, formWithErrors, schemeName, quarter, accessType, version, pageNumber, BadRequest)
              case _ => futureSessionExpired
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddMembersPage, value))
              _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                chargeType = Some(ChargeType.ChargeTypeOverseasTransfer))
            } yield Redirect(navigator.nextPage(AddMembersPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
  }

  // scalastyle:off parameter.number
  private def renderPage(srn: String,
    startDate: LocalDate,
    form: Form[_],
    schemeName: String,
    quarter: AFTQuarter,
    accessType: AccessType,
    version: Int,
    pageNumber: Int,
    status: Status
  )(implicit request: DataRequest[AnyContent]) = {

    val optionPaginatedMembersInfo = chargePaginationService.getItemsPaginated(
      pageNo = pageNumber,
      ua = request.userAnswers,
      viewUrl = viewUrl(srn, startDate, accessType, version),
      removeUrl = removeUrl(srn, startDate, request.userAnswers, accessType, version),
      chargeType = ChargeType.ChargeTypeOverseasTransfer
    )

    optionPaginatedMembersInfo.map { pmi =>
      val viewModel = AddMembersViewModel(
        form,
        quarter.startDate.format(dateFormatterDMY),
        quarter.endDate.format(dateFormatterDMY),
        mapToTable(pmi.membersForCurrentPage, !request.isViewOnly, pmi.paginationStats.totalAmount),
        chargePaginationService.pagerNavSeq(
          pmi.paginationStats,
          controllers.chargeG.routes.AddMembersController.onPageLoadWithPageNo(srn, startDate, accessType, version, _)
        ),
        pmi.paginationStats.startMember,
        pmi.paginationStats.lastMember,
        pmi.paginationStats.totalMembers,
        !request.isViewOnly,
        TwirlRadios.yesNo(form("value"))
      )

      val submitCall = routes.AddMembersController.onSubmit(srn, startDate, accessType, version, pageNumber)
      val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

      Future.successful(status(addMembersView(viewModel, submitCall, returnUrl, schemeName)))

    }.getOrElse(Future.successful(NotFound))

  }
  private def mapToTable(members: Seq[Member], canChange: Boolean, totalAmount:BigDecimal)(implicit messages: Messages): Table =
    mapChargeXMembersToTableTwirlMigration("chargeG", members, canChange, Some(totalAmount))

  private def viewUrl(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Int => Call =
    controllers.chargeG.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, _)

  private def removeUrl(srn: String, startDate: LocalDate, ua: UserAnswers,
    accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int => Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeG.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, _)
    } else {
      controllers.chargeG.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, _)
    }

}
