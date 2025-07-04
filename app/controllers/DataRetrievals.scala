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

package controllers

import models.LocalDateBinder._
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringEmployerAddress, SponsoringOrganisationDetails}
import models.mccloud.{PensionsRemedySchemeSummary, PensionsRemedySummary}
import models.requests.DataRequest
import models.{AFTQuarter, AccessType, ChargeType, Index, MemberDetails, SponsoringEmployerType, UserAnswers, YearRange}
import pages._
import pages.chargeC._
import pages.chargeD.ChargeDetailsPage
import pages.chargeE.AnnualAllowanceYearPage
import pages.mccloud._
import play.api.Logger
import play.api.libs.json.{JsArray, Reads}
import play.api.mvc.Results.Redirect
import play.api.mvc.{AnyContent, Result}
import utils.DateHelper.{dateFormatterDMY, dateFormatterStartDate}

import java.time.LocalDate
import scala.concurrent.Future

object DataRetrievals {
  private val logger = Logger("DataRetrievals")

  def retrieveSchemeName(block: String => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(SchemeNameQuery) match {
      case Some(schemeName) => block(schemeName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeWithPSTR(block: (String, String) => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(PSTRQuery)) match {

      case (Some(schemeName), Some(pstr)) => block(schemeName, pstr)
      case (None, Some(_)) =>
        logger.warn(s"Redirecting to session expired, failed to get schemeName, but retrieved pstr")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      case (Some(_),None) => logger.warn(s"Redirecting to session expired, failed to get pstr, but retrieved schemeName")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      case _ =>
        logger.warn(s"Redirecting to session expired, failed to get schemeName and pstr")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeNameWithEmailAndQuarter(block: (String, String, AFTQuarter) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(EmailQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(email), Some(quarter)) => block(schemeName, email, quarter)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrievePSAAndSchemeDetailsWithAmendment(block: (String, String, String, AFTQuarter, Boolean, Int) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(SchemeNameQuery), ua.get(PSTRQuery), ua.get(EmailQuery), ua.get(QuarterPage)) match {
      case (Some(schemeName), Some(pstr), Some(email), Some(quarter)) =>
        block(schemeName, pstr, email, quarter, request.isAmendment, request.aftVersion)
      case (a, b, c, d) =>
        logger.warn(s"Redirecting to session expired (with pstr). UA state:- name: $a pstr: $b email: $c quarter: $d")
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  private def defaultValue = "Unknown"

  private def logDetailsWhenDefaultValueUsed(schemeName: String, email: String, startDate: String, endDate: String): Unit = {
    if (schemeName == defaultValue || email == defaultValue || startDate == defaultValue || endDate == defaultValue) {
      val s1 = if (schemeName == defaultValue) "scheme name" else ""
      val s2 = if (email == defaultValue) "email" else ""
      val s3 = if (startDate == defaultValue) "start date" else ""
      val s4 = if (endDate == defaultValue) "end date" else ""

      logger.error(
        s"Retrieving user answer details: one ore more retrieved items has default value of unknown: $s1 $s2 $s3 $s4")
    }
  }

  def retrievePSAAndSchemeDetailsWithAmendmentNoPSTR(block: (String, String, String, String, Boolean, Int) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers


    val schemeName = ua.get(SchemeNameQuery).getOrElse(defaultValue)
    val email = ua.get(EmailQuery).getOrElse(defaultValue)

    val (startDate, endDate) = ua.get(QuarterPage) match {
      case Some(aftQuarter) =>
        (
          aftQuarter.startDate.format(dateFormatterStartDate),
          aftQuarter.endDate.format(dateFormatterDMY)
        )
      case _ => (defaultValue, defaultValue)
    }

    logDetailsWhenDefaultValueUsed(schemeName, email, startDate, endDate)

    block(schemeName, email, startDate, endDate, request.isAmendment, request.aftVersion)
  }

  def retrieveSchemeAndQuarter(block: (String, AFTQuarter) => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
      case (Some(schemeName), Some(quarter)) => block(schemeName, quarter)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrievePSTR(block: String => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {
    request.userAnswers.get(PSTRQuery) match {
      case Some(pstr) => block(pstr)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeAndMember(memberPage: QuestionPage[MemberDetails])(block: (String, String) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeMemberChargeG(memberPage: QuestionPage[models.chargeG.MemberDetails])(block: (String, String) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(memberPage)) match {
      case (Some(schemeName), Some(memberDetails)) => block(schemeName, memberDetails.fullName)
      case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeAndSponsoringEmployer(index: Int)(block: (String, String) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(WhichTypeOfSponsoringEmployerPage(index)),
      ua.get(SponsoringOrganisationDetailsPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)),
      ua.get(SchemeNameQuery)) match {
      case (Some(SponsoringEmployerTypeOrganisation), Some(company), _, Some(schemeName)) =>
        block(schemeName, company.name)
      case (Some(SponsoringEmployerTypeIndividual), _, Some(individual), Some(schemeName)) =>
        block(schemeName, individual.fullName)
      case _ =>
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def retrieveSchemeEmployerTypeAndSponsoringEmployer(index: Int)(block: (String, String, SponsoringEmployerType) => Future[Result])(
    implicit request: DataRequest[AnyContent]): Future[Result] = {
    val ua = request.userAnswers
    (ua.get(WhichTypeOfSponsoringEmployerPage(index)),
      ua.get(SponsoringOrganisationDetailsPage(index)),
      ua.get(SponsoringIndividualDetailsPage(index)),
      ua.get(SchemeNameQuery)) match {
      case (Some(SponsoringEmployerTypeOrganisation), Some(company), _, Some(schemeName)) =>
        block(schemeName, company.name, SponsoringEmployerTypeOrganisation)
      case (Some(SponsoringEmployerTypeIndividual), _, Some(individual), Some(schemeName)) =>
        block(schemeName, individual.fullName, SponsoringEmployerTypeIndividual)
      case _ =>
        Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
    }
  }

  def cyaChargeGeneric[A](chargeDetailsPage: QuestionPage[A], srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    block: (A, String) => Future[Result])(implicit request: DataRequest[AnyContent], reads: Reads[A]): Future[Result] = {
    (
      request.userAnswers.get(chargeDetailsPage),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(schemeName)) =>
        block(chargeDetails, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
    }
  }

  def cyaChargeC(index: Index, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    block: (SponsoringEmployerType,
      Either[models.MemberDetails, SponsoringOrganisationDetails],
      SponsoringEmployerAddress,
      ChargeCDetails,
      String) => Future[Result])(implicit request: DataRequest[AnyContent]): Future[Result] = {

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
            Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
        }
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
    }
  }

  def cyaChargeD(index: Index, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    block: (models.MemberDetails, models.chargeD.ChargeDDetails, PensionsRemedySummary, String) => Future[Result])(
                  implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeD.MemberDetailsPage(index)),
      request.userAnswers.get(ChargeDetailsPage(index)),
      getPensionsRemedySummary(request.userAnswers, index, ChargeType.ChargeTypeLifetimeAllowance),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(chargeDetails), pensionsRemedySummary, Some(schemeName)) =>
        block(memberDetails, chargeDetails, pensionsRemedySummary, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
    }
  }

  private def getPensionsRemedySchemeSummary(ua: UserAnswers,
                                             index: Index,
                                             chargeType: ChargeType,
                                             wasAnotherPensionScheme: Option[Boolean]): List[PensionsRemedySchemeSummary] = {
    val pensionsSchemeSize = pensionsSchemeCount(ua, index, chargeType)
    val pensionsRemedySchemeSummaryList = (pensionsSchemeSize, wasAnotherPensionScheme) match {
      case (0, Some(false)) =>
        List(
          PensionsRemedySchemeSummary(
            0,
            None,
            ua.get(TaxYearReportedAndPaidPage(chargeType, index, None)),
            ua.get(TaxQuarterReportedAndPaidPage(chargeType, index, None)),
            ua.get(ChargeAmountReportedPage(chargeType, index, None))
          ))
      case (_, _) =>
        (0 until pensionsSchemeSize).map { schemeIndex =>
          PensionsRemedySchemeSummary(
            schemeIndex,
            ua.get(EnterPstrPage(chargeType, index, schemeIndex)),
            ua.get(TaxYearReportedAndPaidPage(chargeType, index, Some(schemeIndex))),
            ua.get(TaxQuarterReportedAndPaidPage(chargeType, index, Some(schemeIndex))),
            ua.get(ChargeAmountReportedPage(chargeType, index, Some(schemeIndex)))
          )
        }.toList
    }
    pensionsRemedySchemeSummaryList
  }

  private def getPensionsRemedySummary(ua: UserAnswers, index: Index, chargeType: ChargeType): PensionsRemedySummary = {
    val isPublicServicePensionsRemedy = ua.get(pages.IsPublicServicePensionsRemedyPage(chargeType, Some(index)))
    val isChargeInAdditionReported = ua.get(pages.mccloud.IsChargeInAdditionReportedPage(chargeType, index))
    val wasAnotherPensionScheme = ua.get(pages.mccloud.WasAnotherPensionSchemePage(chargeType, index))
    val pensionsRemedySchemeSummary = getPensionsRemedySchemeSummary(ua, index, chargeType, wasAnotherPensionScheme)

    PensionsRemedySummary(isPublicServicePensionsRemedy, isChargeInAdditionReported, wasAnotherPensionScheme, pensionsRemedySchemeSummary)
  }

  private def pensionsSchemeCount(userAnswers: UserAnswers, index: Int, chargeType: ChargeType): Int = {
    SchemePathHelper.path(chargeType, index).readNullable[JsArray].reads(userAnswers.data).asOpt.flatten.map(_.value.size).getOrElse(0)
  }

  def cyaChargeE(index: Index, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    block: (MemberDetails, YearRange, models.chargeE.ChargeEDetails, PensionsRemedySummary, String) => Future[Result])(
                  implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeE.MemberDetailsPage(index)),
      request.userAnswers.get(AnnualAllowanceYearPage(index)),
      request.userAnswers.get(pages.chargeE.ChargeDetailsPage(index)),
      getPensionsRemedySummary(request.userAnswers, index, ChargeType.ChargeTypeAnnualAllowance),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(memberDetails), Some(taxYear), Some(chargeEDetails), pensionsRemedySummary, Some(schemeName)) =>
        block(memberDetails, taxYear, chargeEDetails, pensionsRemedySummary, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
    }
  }

  def cyaChargeG(index: Index, srn: String, startDate: LocalDate, accessType: AccessType, version: Int)(
    block: (models.chargeG.ChargeDetails, models.chargeG.MemberDetails, models.chargeG.ChargeAmounts, String) => Future[Result])(
                  implicit request: DataRequest[AnyContent]): Future[Result] = {
    (
      request.userAnswers.get(pages.chargeG.ChargeDetailsPage(index)),
      request.userAnswers.get(pages.chargeG.MemberDetailsPage(index)),
      request.userAnswers.get(pages.chargeG.ChargeAmountsPage(index)),
      request.userAnswers.get(SchemeNameQuery)
    ) match {
      case (Some(chargeDetails), Some(memberDetails), Some(chargeAmounts), Some(schemeName)) =>
        block(chargeDetails, memberDetails, chargeAmounts, schemeName)
      case _ =>
        Future.successful(Redirect(controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version)))
    }
  }

}
