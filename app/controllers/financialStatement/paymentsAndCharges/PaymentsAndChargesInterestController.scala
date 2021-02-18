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

package controllers.financialStatement.paymentsAndCharges

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import controllers.actions._
import helpers.FormatHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.SchemeDetails
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN_INTEREST}
import models.requests.IdentifierRequest
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import services.paymentsAndCharges.PaymentsAndChargesService
import uk.gov.hmrc.domain.{PsaId, PspId}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesInterestController @Inject()(
                                                      override val messagesApi: MessagesApi,
                                                      identify: IdentifierAction,
                                                      val controllerComponents: MessagesControllerComponents,
                                                      config: FrontendAppConfig,
                                                      schemeService: SchemeService,
                                                      paymentsAndChargesService: PaymentsAndChargesService,
                                                      fsConnector: FinancialStatementConnector,
                                                      renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val logger = Logger(classOf[PaymentsAndChargesInterestController])

  def onPageLoad(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap {
        schemeDetails =>
          fsConnector.getSchemeFS(schemeDetails.pstr).flatMap {
            schemeFS =>

              val schemeFSGroupedAndSorted: Seq[(LocalDate, Seq[SchemeFS])] =
                paymentsAndChargesService
                  .groupAndSortByStartDate(schemeFS, startDate.getYear)

              buildPage(
                schemeFSGroupedAndSorted = schemeFSGroupedAndSorted,
                startDate = startDate,
                index = index,
                schemeDetails = schemeDetails,
                srn = srn
              )
          }
      }
  }

  def onPageLoadUpcoming(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap {
        schemeDetails =>
          fsConnector.getSchemeFS(schemeDetails.pstr).flatMap {
            schemeFS =>

              val schemeFSGroupedAndSorted: Seq[(LocalDate, Seq[SchemeFS])] =
                paymentsAndChargesService.groupAndSortByStartDate(
                  paymentsAndChargesService.extractUpcomingCharges[SchemeFS](schemeFS, _.dueDate),
                  startDate.getYear
                )

              buildPage(
                schemeFSGroupedAndSorted = schemeFSGroupedAndSorted,
                startDate = startDate,
                index = index,
                schemeDetails = schemeDetails,
                srn = srn
              )
          }
      }
  }

  def onPageLoadOverdue(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = identify.async {
    implicit request =>
      schemeService.retrieveSchemeDetails(
        psaId = request.idOrException,
        srn = srn,
        schemeIdType = "srn"
      ) flatMap {
        schemeDetails =>
          fsConnector.getSchemeFS(schemeDetails.pstr).flatMap {
            schemeFS =>

              val schemeFSGroupedAndSorted: Seq[(LocalDate, Seq[SchemeFS])] =
                paymentsAndChargesService
                  .groupAndSortByStartDate(paymentsAndChargesService.getOverdueCharges(schemeFS), startDate.getYear)

              buildPage(
                schemeFSGroupedAndSorted = schemeFSGroupedAndSorted,
                startDate = startDate,
                index = index,
                schemeDetails = schemeDetails,
                srn = srn
              )
          }
      }
  }

  private def buildPage(
                         schemeFSGroupedAndSorted: Seq[(LocalDate, Seq[SchemeFS])],
                         startDate: LocalDate,
                         index: String,
                         schemeDetails: SchemeDetails,
                         srn: String
                       )(
                         implicit request: IdentifierRequest[AnyContent]
                       ): Future[Result] = {
    val chargeRefsGroupedAndSorted: Seq[(LocalDate, Seq[String])] =
      schemeFSGroupedAndSorted.map(
        dateAndFs => {
          val (date, schemeFs) = dateAndFs
          (date, schemeFs.map(_.chargeReference))
        }
      )

    (
      schemeFSGroupedAndSorted.find(_._1 == startDate),
      chargeRefsGroupedAndSorted.find(_._1 == startDate)
    ) match {
      case (Some(Tuple2(_, seqSchemeFs)), Some(Tuple2(_, seqChargeRefs))) =>
        try {
          seqSchemeFs.find(_.chargeReference == seqChargeRefs(index.toInt)) match {
            case Some(schemeFs) =>
              renderer.render(
                template = "financialStatement/paymentsAndCharges/paymentsAndChargeInterest.njk",
                ctx = summaryListData(srn, Some(schemeFs), schemeDetails.schemeName, index, request.psaId, request.pspId)
              ).map(Ok(_))
            case _ =>
              logger.warn(s"No Payments and Charge details " +
                s"found for the selected charge reference ${seqChargeRefs(index.toInt)}")
              Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
          }
        } catch {
          case _: IndexOutOfBoundsException =>
            logger.warn(
              "[paymentsAndCharges.PaymentsAndChargesInterestController][IndexOutOfBoundsException]:" +
                s"index $startDate/$index of attempted"
            )
            Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
        }
      case _ =>
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def summaryListData(srn: String, filteredSchemeFs: Option[SchemeFS], schemeName: String, index: String,
                      psaId: Option[PsaId], pspId: Option[PspId])
                     (implicit messages: Messages): JsObject = {
    filteredSchemeFs match {
      case Some(schemeFS) =>
        Json.obj(
          fields = "chargeDetailsList" -> getSummaryListRows(schemeFS),
          "tableHeader" -> messages("paymentsAndCharges.caption",
            schemeFS.periodStartDate.format(dateFormatterStartDate),
            schemeFS.periodEndDate.format(dateFormatterDMY)),
          "schemeName" -> schemeName,
          "accruedInterest" -> schemeFS.accruedInterestTotal,
          "chargeType" -> (
            if (schemeFS.chargeType == PSS_AFT_RETURN)
              PSS_AFT_RETURN_INTEREST.toString
            else
              PSS_OTC_AFT_RETURN_INTEREST.toString
            ),
          "originalAmountUrl" -> controllers.financialStatement.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
            .onPageLoad(srn, schemeFS.periodStartDate, index)
            .url,
          "returnUrl" -> config.schemeDashboardUrl(psaId, pspId).format(srn)
        )
      case _ =>
        Json.obj()
    }
  }

  private def getSummaryListRows(schemeFS: SchemeFS): Seq[SummaryList.Row] = {
    Seq(
      Row(
        key = Key(msg"paymentsAndCharges.interest", classes = Seq("govuk-!-padding-left-0", "govuk-!-width-three-quarters")),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric")
        ),
        actions = Nil
      ),
      Row(
        key = Key(
          msg"paymentsAndCharges.interestFrom".withArgs(schemeFS.periodEndDate.plusDays(46).format(dateFormatterDMY)),
          classes = Seq("govuk-table__cell--numeric", "govuk-!-padding-right-0", "govuk-!-width-three-quarters", "govuk-!-font-weight-bold")
        ),
        value = Value(
          Literal(s"${FormatHelper.formatCurrencyAmountAsString(schemeFS.accruedInterestTotal)}"),
          classes = Seq("govuk-!-width-one-quarter", "govuk-table__cell--numeric", "govuk-!-font-weight-bold")
        ),
        actions = Nil
      )
    )
  }
}
