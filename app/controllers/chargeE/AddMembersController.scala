/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.chargeE

import java.time.LocalDate
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import handlers.ErrorHandler
import helpers.DeleteChargeHelper

import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{Member, GenericViewModel, NormalMode, AFTQuarter, ChargeType, UserAnswers, AccessType}
import navigators.CompoundNavigator
import pages.chargeE.AddMembersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.AddMembersService.mapChargeXMembersToTable
import services.ChargePaginationService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{Radios, NunjucksSupport}
import utils.DateHelper.dateFormatterDMY
import viewmodels.Table

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
                                     renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def form: Form[Boolean] = formProvider("chargeE.addMembers.error")

  private def renderPage(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int)(implicit
    request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) =>
        getJson(srn, startDate, form, schemeName, quarter, accessType, version, pageNumber).map{json =>
          renderer.render(template = "chargeE/addMembers.njk", json)
            .map(Ok(_))
        }.getOrElse(errorHandler.onClientError(request.request, NOT_FOUND))

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

  private def futureSessionExpired:Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))

  def onSubmit(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
              case (Some(schemeName), Some(quarter)) =>
                getJson(srn, startDate, formWithErrors, schemeName, quarter, accessType, version, pageNumber).map { json =>
                    renderer.render(template = "chargeE/addMembers.njk", json).map(BadRequest(_))
                }.getOrElse(Future.successful(NotFound))
              case _ => futureSessionExpired
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddMembersPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AddMembersPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
  }

  // scalastyle:off parameter.number
  // scalastyle:off method.length
  private def getJson(srn: String,
    startDate: LocalDate,
    form: Form[_],
    schemeName: String,
    quarter: AFTQuarter,
    accessType: AccessType,
    version: Int,
    pageNumber: Int
  )(implicit request: DataRequest[AnyContent]): Option[JsObject] = {
    val viewModel = GenericViewModel(submitUrl = routes.AddMembersController.onSubmit(srn, startDate, accessType, version, pageNumber).url,
                                     returnUrl = controllers.routes.ReturnToSchemeDetailsController
                                       .returnToSchemeDetails(srn, startDate, accessType, version).url,
                                     schemeName = schemeName)

    val optionPaginatedMembersInfo = chargePaginationService.getItemsPaginated(
      pageNo = pageNumber,
      ua = request.userAnswers,
      viewUrl = viewUrl(srn, startDate, accessType, version),
      removeUrl = removeUrl(srn, startDate, request.userAnswers, accessType, version),
      chargeType = ChargeType.ChargeTypeAnnualAllowance
    )

    optionPaginatedMembersInfo.map { pmi =>
      Json.obj(
        "srn" -> srn,
        "startDate" -> Some(startDate),
        "form" -> form,
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(form("value")),
        "quarterStart" -> quarter.startDate.format(dateFormatterDMY),
        "quarterEnd" -> quarter.endDate.format(dateFormatterDMY),
        "table" -> Json.toJson(mapToTable(pmi.membersForCurrentPage, !request.isViewOnly, pmi.paginationStats.totalAmount)),
        "pageLinksSeq" -> chargePaginationService.pagerNavSeq(
          pmi.paginationStats,
          controllers.chargeE.routes.AddMembersController.onPageLoadWithPageNo(srn, startDate, accessType, version, _)
        ),
        "paginationStatsStartMember" -> pmi.paginationStats.startMember,
        "paginationStatsLastMember" -> pmi.paginationStats.lastMember,
        "paginationStatsTotalMembers" -> pmi.paginationStats.totalMembers,
        "canChange" -> !request.isViewOnly
      )
    }
  }

  private def mapToTable(members: Seq[Member], canChange: Boolean, totalAmount:BigDecimal)(implicit messages: Messages): Table =
    mapChargeXMembersToTable("chargeE", members, canChange, Some(totalAmount))

  private def viewUrl(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Int => Call =
    controllers.chargeE.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, _)

  private def removeUrl(srn: String, startDate: LocalDate, ua: UserAnswers,
    accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int => Call =
    if(request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeE.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, _)
    } else {
      controllers.chargeE.routes.DeleteMemberController.onPageLoad(srn, startDate, accessType, version, _)
    }
}
