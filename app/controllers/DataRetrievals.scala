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

package controllers

import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.requests.DataRequest
import models.{Index, MemberDetails, Quarter, SponsoringEmployerType, YearRange}
import pages.chargeC._
import pages.chargeD.ChargeDetailsPage
import pages.chargeE.AnnualAllowanceYearPage
import pages.{PSTRQuery, QuarterPage, QuestionPage, SchemeNameQuery}
import play.api.libs.json.Reads
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future
import java.time.LocalDate

import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}

object DataRetrievals {

  def retrieveSchemeName(block: String => Future[Result])
                        (implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(SchemeNameQuery) match {
      case Some(schemeName) => block(schemeName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeNameWithPSTRAndQuarter(block: (String, String, Quarter) => Future[Result])
                                          (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(PSTRQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(pstr), Some(quarter)) => block(schemeName, pstr, quarter)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeAndQuarter(block: (String, Quarter) => Future[Result])
                             (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) => block(schemeName,quarter)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }


  def retrievePSTR(block: String => Future[Result])
                  (implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(PSTRQuery) match {
      case Some(pstr) => block(pstr)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeAndMember(memberPage: QuestionPage[MemberDetails])
                             (block: (String, String) => Future[Result])
                             (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeMemberChargeG(memberPage: QuestionPage[models.chargeG.MemberDetails])(block: (String, String) => Future[Result])
                                    (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeAndSponsoringEmployer(index: Int)
                                         (block: (String, String) => Future[Result])
                                         (implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(WhichTypeOfSponsoringEmployerPage(index)), ua.get(SponsoringOrganisationDetailsPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)), ua.get(SchemeNameQuery)) match {
      case (Some(SponsoringEmployerTypeOrganisation), Some(company), _, Some(schemeName)) => block(schemeName, company.name)
      case (Some(SponsoringEmployerTypeIndividual), _, Some(individual), Some(schemeName)) => block(schemeName, individual.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def cyaChargeGeneric[A](chargeDetailsPage: QuestionPage[A],
                          srn: String, startDate: LocalDate)
                         (block: (A, String) => Future[Result])
                         (implicit request: DataRequest[AnyContent], reads: Reads[A]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
    }
  }

  def cyaChargeC(index: Index, srn: String, startDate: LocalDate)
                (block: (SponsoringEmployerType, Either[models.MemberDetails, SponsoringOrganisationDetails], SponsoringEmployerAddress, ChargeCDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {

    (
      request.userAnswers.get(WhichTypeOfSponsoringEmployerPage(index)),
      request.userAnswers.get(SponsoringEmployerAddressPage(index)),
      request.userAnswers.get(ChargeCDetailsPage(index)),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(whichTypeOfSponsoringEmployer), Some(sponsoringEmployerAddress), Some(chargeDetails), Some(schemeName)) =>
        (
          request.userAnswers.get(SponsoringIndividualDetailsPage(index)),
          request.userAnswers.get(SponsoringOrganisationDetailsPage(index))
        ) match {
          case (Some(individual), None) =>
            block(whichTypeOfSponsoringEmployer, Left(individual), sponsoringEmployerAddress, chargeDetails, schemeName)
          case (None, Some(organisation)) =>
            block(whichTypeOfSponsoringEmployer, Right(organisation), sponsoringEmployerAddress, chargeDetails, schemeName)
          case _ =>
            Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
        }
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
    }
  }

  def cyaChargeD(index: Index, srn: String, startDate: LocalDate)
                (block: (models.MemberDetails, models.chargeD.ChargeDDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeD.MemberDetailsPage(index)),
      request.userAnswers.get(ChargeDetailsPage(index)),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(chargeDetails), Some(schemeName)) =>
        block(memberDetails, chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
    }
  }

  def cyaChargeE(index: Index, srn: String, startDate: LocalDate)
                (block: (MemberDetails, YearRange, models.chargeE.ChargeEDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeE.MemberDetailsPage(index)),
      request.userAnswers.get(AnnualAllowanceYearPage(index)),
      request.userAnswers.get(pages.chargeE.ChargeDetailsPage(index)),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(taxYear), Some(chargeEDetails), Some(schemeName)) =>
        block(memberDetails, taxYear, chargeEDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
    }
  }

  def cyaChargeG(index: Index, srn: String, startDate: LocalDate)
                (block: (models.chargeG.ChargeDetails, models.chargeG.MemberDetails, models.chargeG.ChargeAmounts, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeG.ChargeDetailsPage(index)),
      request.userAnswers.get(pages.chargeG.MemberDetailsPage(index)),
      request.userAnswers.get(pages.chargeG.ChargeAmountsPage(index)),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(memberDetails), Some(chargeAmounts), Some(schemeName)) =>
        block(chargeDetails, memberDetails, chargeAmounts, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, None)))
    }
  }

}
