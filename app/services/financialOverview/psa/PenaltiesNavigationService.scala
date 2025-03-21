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

package services.financialOverview.psa

import connectors.ListOfSchemesConnector
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, EventReportingCharges, getPenaltyType}
import controllers.financialOverview.psa.routes._
import models.ChargeDetailsFilter.History
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, ListSchemeDetails, PenaltySchemes}
import play.api.Logger
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.Inject
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesNavigationService @Inject()(listOfSchemesConnector: ListOfSchemesConnector) {

  private val logger = Logger(classOf[PenaltiesNavigationService])

  def navFromPenaltiesTypePage(penalties: Seq[PsaFSDetail], psaId: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val yearsSeq: Seq[Int] = penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .map(_.periodEndDate.getYear).distinct.sorted.reverse

    (penaltyType, yearsSeq.size) match {
      case (AccountingForTaxPenalties, 1) => navFromAFTYearsPage(penalties, yearsSeq.head, psaId, journeyType)
      case (EventReportingCharges, 1) => navFromERYearsPage(penalties, yearsSeq.head, EventReportingCharges, journeyType)
      case (_, 1) => navFromNonAftYearsPage(penalties, yearsSeq.head, psaId, penaltyType, journeyType)
      case (_, size) if size > 1 => Future.successful(Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType, journeyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromERYearsPage(penalties: Seq[PsaFSDetail], year: Int, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter): Future[Result] = {
    if (journeyType == History) {
      Future.successful(Redirect(ClearedPenaltiesAndChargesController.onPageLoad(year.toString, penaltyType)))
    } else {
      val uniquePstrs = penalties
        .filter(p => getPenaltyType(p.chargeType) == EventReportingCharges)
        .map(_.pstr).distinct

      uniquePstrs.length match {
        case 1 =>
          logger.debug(s"Skipping the select scheme page as only 1 scheme is available")
          Future.successful(Redirect(AllPenaltiesAndChargesController.onPageLoad(year.toString, uniquePstrs.head, penaltyType)))
        case 0 => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        case _ => Future.successful(Redirect(SelectSchemeController.onPageLoad(penaltyType, year.toString)))
      }
    }
  }

  def navFromAFTYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, journeyType: ChargeDetailsFilter)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val quartersSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodEndDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (journeyType == History) {
      Future.successful(Redirect(ClearedPenaltiesAndChargesController.onPageLoad(year.toString, AccountingForTaxPenalties)))
    } else if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectPenaltiesQuarterController.onPageLoad(year.toString)))
    } else if (quartersSeq.size == 1) {
      logger.debug(s"Skipping the select quarter page")
      navFromQuartersPage(penalties, quartersSeq.head, psaId)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }


  def navFromNonAftYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    if (journeyType == History) {
      Future.successful(Redirect(ClearedPenaltiesAndChargesController.onPageLoad(year.toString, penaltyType)))
    } else {
      penaltySchemes(year, psaId, penaltyType, penalties).map { schemes =>

        if (schemes.size > 1) {
          Redirect(SelectSchemeController.onPageLoad(penaltyType, year.toString))
        } else if (schemes.size == 1) {
          logger.debug(s"Skipping the select scheme page for year $year and type $penaltyType")
          Redirect(AllPenaltiesAndChargesController.onPageLoad(year.toString, schemes.head.pstr, penaltyType))
        } else {
          Redirect(controllers.routes.SessionExpiredController.onPageLoad)
        }
      }
    }
  }

  def navFromQuartersPage(penalties: Seq[PsaFSDetail], startDate: LocalDate, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    penaltySchemes(startDate, psaId, penalties).map { schemes =>
      if (schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(AccountingForTaxPenalties, startDate.toString))
      } else if (schemes.size == 1) {
        logger.debug(s"Skipping the select scheme page for startDate $startDate and type AFT")
        Redirect(AllPenaltiesAndChargesController.onPageLoadAFT(startDate.toString, schemes.head.pstr))
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  def penaltySchemes(startDate: LocalDate, psaId: String, penalties: Seq[PsaFSDetail])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

    val filteredPenalties = penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodStartDate == startDate)

    penaltySchemes(filteredPenalties, psaId)
  }

  def penaltySchemes(year: Int, psaId: String, penaltyType: PenaltyType, penalties: Seq[PsaFSDetail])
                    (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] = {

    val filteredPenalties = penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .filter(_.periodEndDate.getYear == year)

    penaltySchemes(filteredPenalties, psaId)
  }

  private def penaltySchemes(filteredPenalties: Seq[PsaFSDetail], psaId: String)
                            (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PenaltySchemes]] =
    for {
      listOfSchemes <- getListOfSchemes(psaId)
    } yield {

      val penaltyPstrs: Seq[String] = filteredPenalties.map(_.pstr).distinct
      val schemesWithPstr: Seq[ListSchemeDetails] = listOfSchemes.filter(_.pstr.isDefined)

      val associatedSchemes: Seq[PenaltySchemes] = schemesWithPstr
        .filter(scheme => penaltyPstrs.contains(scheme.pstr.get))
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber), None))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None, None))

      associatedSchemes ++ unassociatedSchemes
    }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFSDetail], startDate: LocalDate, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFSDetail]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate == startDate)
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  def unassociatedSchemes(seqPsaFS: Seq[PsaFSDetail], year: Int, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[PsaFSDetail]] = {
    for {
      listOfSchemes <- getListOfSchemes(psaId)
      schemesWithPstr = listOfSchemes.filter(_.pstr.isDefined)
    } yield
      seqPsaFS
        .filter(_.periodStartDate.getYear == year)
        .filter(psaFS => !schemesWithPstr.map(_.pstr.get).contains(psaFS.pstr))
  }

  private def getListOfSchemes(psaId: String)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Seq[ListSchemeDetails]] =
    listOfSchemesConnector.getListOfSchemes(psaId).map {
      case Right(list) => list.schemeDetails.getOrElse(Nil)
      case _ => Seq.empty[ListSchemeDetails]
    }
}
