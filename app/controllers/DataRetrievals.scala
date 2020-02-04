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

import akka.http.scaladsl.model.HttpHeader.ParsingResult.Ok
import models.chargeA.ChargeDetails
import models.chargeB.ChargeBDetails
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.{MemberDetails, YearRange}
import models.requests.DataRequest
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeE.AnnualAllowanceYearPage
import pages.{PSTRQuery, QuestionPage, SchemeNameQuery}
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

object DataRetrievals {

  def retrieveSchemeName(block: String => Future[Result])
                        (implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(SchemeNameQuery) match {
      case Some(schemeName) => block(schemeName)
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

  def retrieveSchemeAndMember(memberPage: QuestionPage[MemberDetails])(block: (String, String) => Future[Result])
                             (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeAndMemberChargeG(memberPage: QuestionPage[models.chargeG.MemberDetails])(block: (String, String) => Future[Result])
                                    (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def retrieveSchemeAndSponsoringEmployer(index: Int)(block: (String, String) => Future[Result])
                                         (implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(IsSponsoringEmployerIndividualPage(index)), ua.get(SponsoringOrganisationDetailsPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)), ua.get(SchemeNameQuery)) match {
      case (Some(false), Some(company), _, Some(schemeName)) => block(schemeName, company.name)
      case (Some(true), _, Some(individual), Some(schemeName)) => block(schemeName, individual.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

  def cyaChargeA(chargeDetails: QuestionPage[ChargeDetails],
                 srn: String)
                (block: (ChargeDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeB(chargeDetails: QuestionPage[ChargeBDetails],
                 srn: String)
                (block: (ChargeBDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeC(isSponsoringEmployerIndividual: QuestionPage[Boolean],
                 sponsoringIndividualDetails: QuestionPage[MemberDetails],
                 sponsoringOrganisationDetails: QuestionPage[SponsoringOrganisationDetails],
                 sponsoringEmployerAddress: QuestionPage[SponsoringEmployerAddress],
                 chargeDetails: QuestionPage[ChargeCDetails],
                 srn: String)
                (block: (Boolean, Either[models.MemberDetails, SponsoringOrganisationDetails], SponsoringEmployerAddress, ChargeCDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {

    (
      request.userAnswers.get(isSponsoringEmployerIndividual),
      request.userAnswers.get(sponsoringEmployerAddress),
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(isSponsoringEmployerIndividual), Some(sponsoringEmployerAddress), Some(chargeDetails), Some(schemeName)) =>
        (
          request.userAnswers.get(sponsoringIndividualDetails),
          request.userAnswers.get(sponsoringOrganisationDetails)
        ) match {
          case (Some(individual), None) =>
            block(isSponsoringEmployerIndividual, Left(individual), sponsoringEmployerAddress, chargeDetails, schemeName)
          case (None, Some(organisation)) =>
            block(isSponsoringEmployerIndividual, Right(organisation), sponsoringEmployerAddress, chargeDetails, schemeName)
          case _ =>
            Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
        }
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeD(memberDetails: QuestionPage[models.MemberDetails],
                 chargeDetails: QuestionPage[models.chargeD.ChargeDDetails],
                 srn: String)
                (block: (models.MemberDetails, models.chargeD.ChargeDDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(memberDetails),
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(chargeDetails), Some(schemeName)) =>
        block(memberDetails, chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeE(memberDetails: QuestionPage[MemberDetails],
                 annualAllowanceYear: QuestionPage[YearRange],
                 chargeDetails: QuestionPage[models.chargeE.ChargeEDetails],
                 srn: String)
                (block: (MemberDetails, YearRange, models.chargeE.ChargeEDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(memberDetails),
      request.userAnswers.get(annualAllowanceYear),
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(taxYear), Some(chargeEDetails), Some(schemeName)) =>
        block(memberDetails, taxYear, chargeEDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeF(chargeDetails: QuestionPage[models.chargeF.ChargeDetails],
                 srn: String)
                (block: (models.chargeF.ChargeDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeG(chargeDetails: QuestionPage[models.chargeG.ChargeDetails],
                 memberDetails: QuestionPage[models.chargeG.MemberDetails],
                 chargeAmounts: QuestionPage[models.chargeG.ChargeAmounts],
                 srn: String)
                (block: (models.chargeG.ChargeDetails, models.chargeG.MemberDetails, models.chargeG.ChargeAmounts, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetails),
      request.userAnswers.get(memberDetails),
      request.userAnswers.get(chargeAmounts),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(memberDetails), Some(chargeAmounts), Some(schemeName)) =>
        block(chargeDetails, memberDetails, chargeAmounts, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

}
