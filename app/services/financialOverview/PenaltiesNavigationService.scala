/*
 * Copyright 2022 HM Revenue & Customs
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

package services.financialOverview

import connectors.cache.FinancialInfoCacheConnector
import connectors.{FinancialStatementConnector, ListOfSchemesConnector, MinimalConnector}
import controllers.financialOverview.psa.routes._
import controllers.financialOverview.routes._
import models.ChargeDetailsFilter.All
import models.financialStatement.PaymentOrChargeType._
import models.financialStatement.PenaltyType.{AccountingForTaxPenalties, ContractSettlementCharges, InformationNoticePenalties, getPenaltyType}
import models.financialStatement.{PenaltyType, PsaFSDetail}
import models.{ChargeDetailsFilter, ListSchemeDetails, PenaltySchemes}
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDate
import javax.inject.Inject
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class PenaltiesNavigationService @Inject()(fsConnector: FinancialStatementConnector,
                                           fiCacheConnector: FinancialInfoCacheConnector,
                                           listOfSchemesConnector: ListOfSchemesConnector,
                                           minimalConnector: MinimalConnector) {

  def navFromPSADashboard(payments: Seq[PsaFSDetail], pstr: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val paymentTypes: Seq[PenaltyType] = payments.map(p => getPenaltyType(p.chargeType)).distinct

    if (paymentTypes.size > 1) {
      Future.successful(Redirect(PenaltyTypeController.onPageLoad(All)))
    } else if (paymentTypes.size == 1) {
      navFromPenaltiesTypePage(payments, pstr, paymentTypes.head)
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromPenaltiesTypePage(penalties: Seq[PsaFSDetail], psaId: String, penaltyType: PenaltyType)
                              (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val yearsSeq: Seq[Int] = penalties
      .filter(p => getPenaltyType(p.chargeType) == penaltyType)
      .map(_.periodEndDate.getYear).distinct.sorted.reverse

    println("\n\n\n\npenaltyType,yearsSeq " + penaltyType + yearsSeq)
    (penaltyType, yearsSeq.size) match {
      case (AccountingForTaxPenalties, 1) => navFromAFTYearsPage(penalties, yearsSeq.head, AccountingForTaxPenalties, psaId, All)
      case (_, 1) => navFromNonAftYearsPage(penalties, yearsSeq.head, psaId, penaltyType, All)
      case (_, size) if size > 1 => Future.successful(Redirect(SelectPenaltiesYearController.onPageLoad(penaltyType)))
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def navFromAFTYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val quartersSeq = penalties
      .filter(p => getPenaltyType(p.chargeType) == AccountingForTaxPenalties)
      .filter(_.periodEndDate.getYear == year)
      .map(_.periodStartDate).distinct

    if (quartersSeq.size > 1) {
      Future.successful(Redirect(SelectPenaltiesQuarterController.onPageLoad(year.toString)))
    } else if (quartersSeq.size == 1) {
      Future.successful(Redirect(SelectSchemeController.onPageLoad(penaltyType, year.toString)))
      navFromQuartersPage(penalties, quartersSeq.head, psaId)
      //Future.successful(Redirect(AllPenaltiesAndChargesController.onPageLoad(quartersSeq.head.toString, AccountingForTaxCharges)))
    } else {
      Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }


  def navFromNonAftYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, penaltyType: PenaltyType,
                             journeyType: ChargeDetailsFilter)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    penaltySchemes(year, psaId, penaltyType, penalties).map { schemes =>

      if(schemes.size > 1) {
        println("\n\n\n\n more scheme")
        Redirect(SelectSchemeController.onPageLoad(penaltyType, year.toString))
      } else if (schemes.size == 1) {
        println("\n\n\n\n single scheme")
        val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
        Redirect(AllPenaltiesAndChargesController.onPageLoad(year.toString, pstrIndex, penaltyType))
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  def navFromQuartersPage(penalties: Seq[PsaFSDetail], startDate: LocalDate, psaId: String)
                         (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    penaltySchemes(startDate, psaId, penalties).map { schemes =>
      if (schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(AccountingForTaxPenalties, startDate.toString))
      } else if (schemes.size == 1) {
        //        logger.debug(s"Skipping the select scheme page for startDate $startDate and type AFT")
        val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
        Redirect(AllPenaltiesAndChargesController.onPageLoadAFT(startDate.toString, pstrIndex))
      } else {
        Redirect(controllers.routes.SessionExpiredController.onPageLoad)
      }
    }
  }

  def navFromNonAFTPenaltyYearsPage(penalties: Seq[PsaFSDetail], year: Int, psaId: String, penaltyType: PenaltyType, journeyType: ChargeDetailsFilter)
                                   (implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Result] = {

    val penaltiesUrl = penaltyType match {
      case ContractSettlementCharges => identifier => AllPenaltiesAndChargesController.onPageLoad(year.toString, identifier, journeyType)
      case InformationNoticePenalties => identifier => AllPenaltiesAndChargesController.onPageLoad(year.toString, identifier, journeyType)
    }

    penaltySchemes(year.toInt, psaId, penaltyType, penalties).map { schemes =>
      if (schemes.size > 1) {
        Redirect(SelectSchemeController.onPageLoad(penaltyType, year.toString))
      } else if (schemes.size == 1) {
        //logger.debug(s"Skipping the select scheme page for year $year and type $penaltyType")
        schemes.head.srn match {
          case Some(srn) =>
            Redirect(penaltiesUrl(srn))
          case _ =>
            val pstrIndex: String = penalties.map(_.pstr).indexOf(schemes.head.pstr).toString
            Redirect(penaltiesUrl(pstrIndex))
        }
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
        .map(x => PenaltySchemes(Some(x.name), x.pstr.get, Some(x.referenceNumber)))

      val unassociatedSchemes: Seq[PenaltySchemes] = penaltyPstrs
        .filter(penaltyPstr => !schemesWithPstr.map(_.pstr.get).contains(penaltyPstr))
        .map(x => PenaltySchemes(None, x, None))

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
