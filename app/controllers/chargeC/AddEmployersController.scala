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

package controllers.chargeC

import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import handlers.ErrorHandler
import helpers.{DeleteChargeHelper, FormatHelper}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, ChargeType, Employer, GenericViewModel, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeC.AddEmployersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.ChargePaginationService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{Html, NunjucksSupport, Radios}
import utils.DateHelper.dateFormatterDMY
import viewmodels.Table
import viewmodels.Table.Cell

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AddEmployersController @Inject()(override val messagesApi: MessagesApi,
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

  def form: Form[Boolean] = formProvider("chargeC.addEmployers.error")

  private def renderPage(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, pageNumber: Int)(implicit
    request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) =>
        getJson(srn, startDate, form, schemeName, quarter, accessType, version, pageNumber).map{json =>
          renderer.render(template = "chargeC/addEmployers.njk", json)
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
                getJson(srn, startDate, formWithErrors, schemeName, quarter, accessType, version, pageNumber).map { json =>
                  renderer.render(template = "chargeC/addEmployers.njk", json).map(BadRequest(_))
                }.getOrElse(Future.successful(NotFound))
              case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddEmployersPage, value))
              _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data, chargeType = Some(ChargeType.ChargeTypeAuthSurplus))
            } yield Redirect(navigator.nextPage(AddEmployersPage, NormalMode, updatedAnswers, srn, startDate, accessType, version))
          }
        )
  }

  // scalastyle:off parameter.number
  private def getJson(srn: String,
    startDate: LocalDate,
    form: Form[_],
    schemeName: String,
    quarter: AFTQuarter,
    accessType: AccessType,
    version: Int,
    pageNumber: Int
  )(implicit request: DataRequest[AnyContent]): Option[JsObject] = {

    val viewModel = GenericViewModel(submitUrl = routes.AddEmployersController.onSubmit(srn, startDate, accessType, version, pageNumber).url,
                                     returnUrl = controllers.routes.ReturnToSchemeDetailsController
                                       .returnToSchemeDetails(srn, startDate, accessType, version).url,
                                     schemeName = schemeName)

    val optionPaginatedMembersInfo = chargePaginationService.getItemsPaginated(
      pageNo = pageNumber,
      ua = request.userAnswers,
      viewUrl = viewUrl(srn, startDate, accessType, version),
      removeUrl = removeUrl(srn, startDate, request.userAnswers, accessType, version),
      chargeType = ChargeType.ChargeTypeAuthSurplus
    )

    optionPaginatedMembersInfo.map { pmi =>
      Json.obj(
        "srn" -> srn,
        "startDate" -> Some(localDateToString(startDate)),
        "form" -> form,
        "viewModel" -> viewModel,
        "radios" -> Radios.yesNo(form("value")),
        "quarterStart" -> quarter.startDate.format(dateFormatterDMY),
        "quarterEnd" -> quarter.endDate.format(dateFormatterDMY),
        "table" -> Json.toJson(mapToTable(pmi.employersForCurrentPage, !request.isViewOnly, pmi.paginationStats.totalAmount)),
        "pageLinksSeq" -> chargePaginationService.pagerNavSeq(
          pmi.paginationStats,
          controllers.chargeC.routes.AddEmployersController.onPageLoadWithPageNo(srn, startDate, accessType, version, _)
        ),
        "paginationStatsStartMember" -> pmi.paginationStats.startMember,
        "paginationStatsLastMember" -> pmi.paginationStats.lastMember,
        "paginationStatsTotalMembers" -> pmi.paginationStats.totalMembers,
        "canChange" -> !request.isViewOnly
      )
    }

  }

  private def mapToTable(members: Seq[Employer], canChange: Boolean, totalAmount:BigDecimal)
    (implicit messages: Messages): Table = {
    val head = Seq(
      Cell(msg"addEmployers.employer.header"),
      Cell(msg"addEmployers.amount.header", classes = Seq("govuk-table__header--numeric")),
      Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.viewSponsoringEmployer")}</span>"""))
    ) ++ (
      if (canChange)
        Seq(Cell(Html(s"""<span class=govuk-visually-hidden>${messages("addEmployers.hiddenText.header.removeSponsoringEmployer")}</span>""")))
      else
        Nil
      )

    val rows = members.map { data =>
      Seq(
        Cell(Literal(data.name), classes = Seq("govuk-!-width-one-half")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__header--numeric")),
        Cell(link(data.viewLinkId, "site.view", data.viewLink, data.name), classes = Seq("govuk-!-width-one-quarter"))
      ) ++ (if (canChange) Seq(Cell(link(data.removeLinkId, "site.remove", data.removeLink, data.name), classes = Seq("govuk-!-width-one-quarter")))
      else Nil)
    }
    val totalRow = Seq(
      Seq(
        Cell(msg"addMembers.total", classes = Seq("govuk-!-font-weight-bold govuk-table__header--numeric")),
        Cell(Literal(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"),
          classes = Seq("govuk-!-font-weight-bold govuk-table__header--numeric")),
        Cell(msg"")
      ) ++ (if (canChange) Seq(Cell(msg"")) else Nil))

    Table(head = head, rows = rows ++ totalRow,attributes = Map("role" -> "table"))
  }

  private def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): Html = {
    val hiddenTag = "govuk-visually-hidden"
    Html(
      s"<a class=govuk-link id=$id href=$url>" + s"<span aria-hidden=true >${messages(text)}</span>" +
        s"<span class= $hiddenTag>${messages(text)} ${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span> </a>")
  }

  private def viewUrl(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Int => Call =
    controllers.chargeC.routes.CheckYourAnswersController.onPageLoad(srn, startDate, accessType, version, _)

  private def removeUrl(srn: String, startDate: LocalDate, ua: UserAnswers,
    accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Int => Call =
    if (request.isAmendment && deleteChargeHelper.isLastCharge(ua)) {
      controllers.chargeC.routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version, _)
    } else {
      controllers.chargeC.routes.DeleteEmployerController.onPageLoad(srn, startDate, accessType, version, _)
    }

}
