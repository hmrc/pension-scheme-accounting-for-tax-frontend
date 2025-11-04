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
import helpers.{DeleteChargeHelper, FormatHelper}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, AddEmployersViewModel, ChargeType, Employer, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeC.AddEmployersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc._
import services.ChargePaginationService
import uk.gov.hmrc.govukfrontend.views.Aliases.{HtmlContent, Table, Text}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.{HeadCell, TableRow}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.dateFormatterDMY
import viewmodels.TwirlRadios
import views.html.chargeC.AddEmployersView

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
                                       view: AddEmployersView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def form: Form[Boolean] = formProvider("chargeC.addEmployers.error")

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
  private def renderPageWithStatus(srn: String,
    startDate: LocalDate,
    form: Form[?],
    schemeName: String,
    quarter: AFTQuarter,
    accessType: AccessType,
    version: Int,
    pageNumber: Int,
    status: Status
  )(implicit request: DataRequest[AnyContent]): Future[Result] = {

    val optionPaginatedMembersInfo = chargePaginationService.getItemsPaginated(
      pageNo = pageNumber,
      ua = request.userAnswers,
      viewUrl = viewUrl(srn, startDate, accessType, version),
      removeUrl = removeUrl(srn, startDate, request.userAnswers, accessType, version),
      chargeType = ChargeType.ChargeTypeAuthSurplus
    )

    optionPaginatedMembersInfo.map { pmi =>
      val viewModel = AddEmployersViewModel(
        schemeName,
        controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
        quarter.startDate.format(dateFormatterDMY),
        quarter.endDate.format(dateFormatterDMY),
        canChange = !request.isViewOnly,
        paginationStatsStartMember = pmi.paginationStats.startMember,
        paginationStatsLastMember = pmi.paginationStats.lastMember,
        paginationStatsTotalMembers = pmi.paginationStats.totalMembers,
        radios = TwirlRadios.yesNo(form("value"))
      )
       val submitCall = routes.AddEmployersController.onSubmit(srn, startDate, accessType, version, pageNumber)
       val table =  mapToTable(pmi.employersForCurrentPage, !request.isViewOnly, pmi.paginationStats.totalAmount)
        val pageLinksSeq = chargePaginationService.pagerNavSeq(
          pmi.paginationStats,
          controllers.chargeC.routes.AddEmployersController.onPageLoadWithPageNo(srn, startDate, accessType, version, _)
        )
      Future.successful(status(view(form, viewModel, submitCall, table, pageLinksSeq)))
    }.getOrElse(Future.successful(NotFound))

  }

  private def mapToTable(members: Seq[Employer], canChange: Boolean, totalAmount:BigDecimal)
                        (implicit messages: Messages): Table = {
    val head = Seq(
        HeadCell(Text(Messages("addEmployers.employer.header"))),
        HeadCell(Text(Messages("addEmployers.amount.header")), classes = "govuk-table__header--numeric"),
        HeadCell(HtmlContent(s"""<span class=\"govuk-visually-hidden\">${messages("addEmployers.hiddenText.header.viewSponsoringEmployer")}</span>"""))
      ) ++ (
        if (canChange)
          Seq(HeadCell(HtmlContent(s"""<span class=\"govuk-visually-hidden\">${messages("addEmployers.hiddenText.header.removeSponsoringEmployer")}</span>""")))
        else
          Nil
        )

    val rows = members.map { data =>
      Seq(
        TableRow(Text(data.name), classes = "govuk-!-width-one-half"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(data.amount)}"), classes = "govuk-!-width-one-quarter govuk-table__header--numeric"),
        TableRow(link(s"\"${data.viewLinkId}\"", "site.view", s"\"${data.viewLink}\"", data.name), classes = "govuk-!-width-one-quarter")
      ) ++ (if (canChange) Seq(TableRow(link(s"\"${data.removeLinkId}\"", "site.remove", s"\"${data.removeLink}\"", data.name), classes = "govuk-!-width-one-quarter"))
      else Nil)
    }
    val totalRow = Seq(
      Seq(
        TableRow(Text(Messages("addMembers.total")), classes = "govuk-!-font-weight-bold govuk-table__header--numeric"),
        TableRow(Text(s"${FormatHelper.formatCurrencyAmountAsString(totalAmount)}"), classes = "govuk-!-font-weight-bold govuk-table__header--numeric"),
        TableRow(Text(""))
      ) ++ (if (canChange) Seq(TableRow(Text(""))) else Nil))

    Table(rows ++ totalRow, Some(head), attributes = Map("role" -> "table"))
  }

  private def link(id: String, text: String, url: String, name: String)(implicit messages: Messages): HtmlContent = {
    val hiddenTag = "govuk-visually-hidden"
    HtmlContent(
      s"<a class=\"govuk-link\" id=$id href=$url><span aria-hidden=\"true\">${messages(text)}</span>" +
        s"<span class=\"$hiddenTag\">${messages(text)} ${messages(s"chargeC.addEmployers.visuallyHidden", name)}</span></a>")
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
