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

package controllers.paymentsAndCharges

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.FinancialStatementConnector
import connectors.cache.FinancialInfoCacheConnector
import controllers.actions._
import helpers.FormatHelper
import javax.inject.Inject
import models.LocalDateBinder._
import models.financialStatement.SchemeFS
import models.financialStatement.SchemeFSChargeType.{PSS_AFT_RETURN, PSS_AFT_RETURN_INTEREST, PSS_OTC_AFT_RETURN_INTEREST}
import play.api.Logger
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc._
import renderer.Renderer
import services.SchemeService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.SummaryList.{Key, Row, Value}
import uk.gov.hmrc.viewmodels.Text.Literal
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import scala.concurrent.{ExecutionContext, Future}

class PaymentsAndChargesInterestController @Inject()(override val messagesApi: MessagesApi,
                                                     identify: IdentifierAction,
                                                     val controllerComponents: MessagesControllerComponents,
                                                     config: FrontendAppConfig,
                                                     schemeService: SchemeService,
                                                     fsConnector: FinancialStatementConnector,
                                                     fiCacheConnector: FinancialInfoCacheConnector,
                                                     renderer: Renderer
                                                    )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, index: String): Action[AnyContent] = identify.async {
    implicit request =>
      fiCacheConnector.fetch flatMap {
        case Some(jsValue) =>
          val chargeRefs: Seq[String] = (jsValue \ "chargeRefs").as[Seq[String]]
          schemeService.retrieveSchemeDetails(request.psaId.id, srn).flatMap {
            schemeDetails =>
              fsConnector.getSchemeFS(schemeDetails.pstr).flatMap {
                seqSchemeFS =>
                  val filteredSchemeFs = seqSchemeFS.find(_.chargeReference == chargeRefs(index.toInt))
                  filteredSchemeFs match {
                    case Some(_) =>
                      renderer
                        .render(template = "paymentsAndCharges/paymentsAndChargeInterest.njk",
                          summaryListData(srn, filteredSchemeFs, schemeDetails.schemeName, index))
                        .map(Ok(_))
                    case _ =>
                      Logger.warn(s"No Payments and Charge details " +
                        s"found for the selected charge reference ${chargeRefs(index.toInt)}")
                      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
                  }
              }
          }
        case _ =>
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
  }

  def summaryListData(srn: String, filteredSchemeFs: Option[SchemeFS], schemeName: String, index: String)
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
          "originalAmountUrl" -> controllers.paymentsAndCharges.routes.PaymentsAndChargeDetailsController
            .onPageLoad(srn, schemeFS.periodStartDate, index)
            .url,
          "returnUrl" -> config.managePensionsSchemeSummaryUrl.format(srn)
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
          msg"paymentsAndCharges.interestDue".withArgs(LocalDate.now().format(dateFormatterDMY)),
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
