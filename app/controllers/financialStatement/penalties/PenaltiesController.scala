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

package controllers.financialStatement.penalties

import config.Constants._
import config.FrontendAppConfig
import controllers.actions._
import models.LocalDateBinder._
import models.financialStatement.PenaltyType._
import models.financialStatement.{PenaltiesViewModel, PenaltyType, PsaFSDetail}
import models.requests.IdentifierRequest
import models.{PenaltiesFilter, Quarters}
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.{PenaltiesService, SchemeService}
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.financialStatement.penalties.PenaltiesView

class PenaltiesController @Inject()(identify: IdentifierAction,
                                    allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                    override val messagesApi: MessagesApi,
                                    val controllerComponents: MessagesControllerComponents,
                                    penaltiesService: PenaltiesService,
                                    schemeService: SchemeService,
                                    penaltiesView: PenaltiesView,
                                    config: FrontendAppConfig
                                   )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  //noinspection ScalaStyle
  def onPageLoadAft(startDate: LocalDate, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] =
    (identify andThen allowAccess()).async {
    implicit request =>

      val title: String = Messages("penalties.aft.title",
        LocalDate.parse(startDate).format(dateFormatterStartDate),
          Quarters.getQuarter(startDate).endDate.format(dateFormatterDMY))

      penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>

        val chargeRefsIndex: String => String = cr => penaltiesCache.penalties.map(_.chargeReference).indexOf(cr).toString
        val viewModel: Future[PenaltiesViewModel] = if (identifier.matches(srnRegex)) {
          schemeService.retrieveSchemeDetails(request.idOrException, identifier) map { schemeDetails =>

            val filteredPsaFS: Seq[PsaFSDetail] = penaltiesCache.penalties
              .filter(_.pstr == schemeDetails.pstr)
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: Table = penaltiesService.getPsaFsTable(filteredPsaFS, identifier, chargeRefsIndex, AccountingForTaxPenalties, journeyType)

            PenaltiesViewModel(
              title,
              schemeAssociated = true,
              Some(schemeDetails.schemeName),
              schemeDetails.pstr,
              penaltyTable,
              config.managePensionsSchemeOverviewUrl,
              penaltiesCache.psaName
            )
          }
        } else {
          penaltiesService.unassociatedSchemes(penaltiesCache.penalties, startDate, request.psaIdOrException.id) map { penalties =>

            val pstrs: Seq[String] = penalties.map(_.pstr)

            val filteredPsaFS: Seq[PsaFSDetail] = penalties
              .filter(_.periodStartDate == startDate)
              .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)

            val penaltyTable: Table = penaltiesService.getPsaFsTable(filteredPsaFS, identifier, chargeRefsIndex, AccountingForTaxPenalties, journeyType)

            PenaltiesViewModel(
              title,
              false,
              None,
              pstrs(identifier.toInt),
              penaltyTable,
              config.managePensionsSchemeOverviewUrl,
              penaltiesCache.psaName
            )
          }
        }

        viewModel.flatMap(model => Future.successful(Ok(penaltiesView(model))))
      }
  }

  def onPageLoadContract(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, ContractSettlementCharges, journeyType)

  }

  def onPageLoadInfoNotice(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, InformationNoticePenalties, journeyType)

  }

  def onPageLoadPension(year: String, identifier: String, journeyType: PenaltiesFilter): Action[AnyContent] = (identify andThen allowAccess()).async {
    implicit request =>
        onPageLoad(year.toInt, identifier, PensionsPenalties, journeyType)

  }

  private def onPageLoad(year: Int, identifier: String, penaltyType: PenaltyType, journeyType: PenaltiesFilter)
                        (implicit request: IdentifierRequest[AnyContent]): Future[Result] = {
    penaltiesService.getPenaltiesForJourney(request.psaIdOrException.id, journeyType).flatMap { penaltiesCache =>
      val title: String = Messages("penalties.nonAft.title", Messages(s"penaltyType.${penaltyType.toString}"), year.toString)
      val chargeRefsIndex: String => String = cr => penaltiesCache.penalties.map(_.chargeReference).indexOf(cr).toString
      val viewModel: Future[PenaltiesViewModel] = if (identifier.matches(srnRegex)) {
        schemeService.retrieveSchemeDetails(request.idOrException, identifier) map { schemeDetails =>

          val filteredPsaFS: Seq[PsaFSDetail] = penaltiesCache.penalties
            .filter(_.pstr == schemeDetails.pstr)
            .filter(_.periodEndDate.getYear == year)
            .filter(p => getPenaltyType(p.chargeType) == penaltyType)

          val penaltyTable: Table = penaltiesService.getPsaFsTable(filteredPsaFS, identifier, chargeRefsIndex, penaltyType, journeyType)

          PenaltiesViewModel(
            title,
            true,
            Some(schemeDetails.schemeName),
            schemeDetails.pstr,
            penaltyTable,
            config.managePensionsSchemeOverviewUrl,
            penaltiesCache.psaName
          )
        }
      } else {
        penaltiesService.unassociatedSchemes(penaltiesCache.penalties, year, request.psaIdOrException.id) map { penalties =>

          val pstrs: Seq[String] = penaltiesCache.penalties.map(_.pstr)

          val filteredPsaFS: Seq[PsaFSDetail] = penalties
            .filter(_.periodEndDate.getYear == year)
            .filter(p => getPenaltyType(p.chargeType) == penaltyType)

          val penaltyTable: Table = penaltiesService.getPsaFsTable(filteredPsaFS, identifier, chargeRefsIndex, penaltyType, journeyType)

          PenaltiesViewModel(
            title,
            false,
            None,
            pstrs(identifier.toInt),
            penaltyTable,
            config.managePensionsSchemeOverviewUrl,
            penaltiesCache.psaName
          )
        }
      }
      viewModel.flatMap(model => Future.successful(Ok(penaltiesView(model))))
    }
  }
}
