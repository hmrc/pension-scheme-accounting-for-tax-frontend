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
import play.api.libs.json.Reads
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

  def cyaChargeGeneric[A](chargeDetailsPage: QuestionPage[A],
                          srn: String)
                         (block: (A, String) => Future[Result])
                         (implicit request: DataRequest[AnyContent], reads: Reads[A]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeC(isSponsoringEmployerIndividualPage: QuestionPage[Boolean],
                 sponsoringIndividualDetailsPage: QuestionPage[MemberDetails],
                 sponsoringOrganisationDetailsPage: QuestionPage[SponsoringOrganisationDetails],
                 sponsoringEmployerAddressPage: QuestionPage[SponsoringEmployerAddress],
                 chargeDetailsPage: QuestionPage[ChargeCDetails],
                 srn: String)
                (block: (Boolean, Either[models.MemberDetails, SponsoringOrganisationDetails], SponsoringEmployerAddress, ChargeCDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {

    (
      request.userAnswers.get(isSponsoringEmployerIndividualPage),
      request.userAnswers.get(sponsoringEmployerAddressPage),
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(isSponsoringEmployerIndividual), Some(sponsoringEmployerAddress), Some(chargeDetails), Some(schemeName)) =>
        (
          request.userAnswers.get(sponsoringIndividualDetailsPage),
          request.userAnswers.get(sponsoringOrganisationDetailsPage)
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

  def cyaChargeD(memberDetailsPage: QuestionPage[models.MemberDetails],
                 chargeDetailsPage: QuestionPage[models.chargeD.ChargeDDetails],
                 srn: String)
                (block: (models.MemberDetails, models.chargeD.ChargeDDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(memberDetailsPage),
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(chargeDetails), Some(schemeName)) =>
        block(memberDetails, chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeE(memberDetailsPage: QuestionPage[MemberDetails],
                 annualAllowanceYearPage: QuestionPage[YearRange],
                 chargeDetailsPage: QuestionPage[models.chargeE.ChargeEDetails],
                 srn: String)
                (block: (MemberDetails, YearRange, models.chargeE.ChargeEDetails, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(memberDetailsPage),
      request.userAnswers.get(annualAllowanceYearPage),
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(taxYear), Some(chargeEDetails), Some(schemeName)) =>
        block(memberDetails, taxYear, chargeEDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

  def cyaChargeG(chargeDetailsPage: QuestionPage[models.chargeG.ChargeDetails],
                 memberDetailsPage: QuestionPage[models.chargeG.MemberDetails],
                 chargeAmountsPage: QuestionPage[models.chargeG.ChargeAmounts],
                 srn: String)
                (block: (models.chargeG.ChargeDetails, models.chargeG.MemberDetails, models.chargeG.ChargeAmounts, String) => Future[Result])
                (implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(memberDetailsPage),
      request.userAnswers.get(chargeAmountsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(memberDetails), Some(chargeAmounts), Some(schemeName)) =>
        block(chargeDetails, memberDetails, chargeAmounts, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, None)))
    }
  }

}
