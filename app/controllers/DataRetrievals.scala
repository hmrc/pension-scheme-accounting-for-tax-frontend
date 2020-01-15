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

import models.MemberDetails
import models.requests.DataRequest
import pages.chargeC.SponsoringOrganisationDetailsPage
import pages.{PSTRQuery, QuestionPage, SchemeNameQuery}
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.{PSTRQuery, Page, QuestionPage, SchemeNameQuery}
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

  def retrieveSchemeAndSponsoringEmployer(block: (String,String) => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(IsSponsoringEmployerIndividualPage), ua.get(SponsoringOrganisationDetailsPage), ua.get(SponsoringIndividualDetailsPage), ua.get(SchemeNameQuery)) match {
      case (Some(false), Some(company), _, Some(schemeName)) => block(schemeName, company.name)
      case (Some(true), _, Some(individual), Some(schemeName)) => block(schemeName, individual.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
    }
  }

}
