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

package services

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import connectors.{AFTConnector, MinimalPsaConnector}
import models.requests.{DataRequest, OptionalDataRequest}
import models.{Quarter, SchemeDetails, UserAnswers}
import pages._
import play.api.libs.json._
import services.AFTService._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class AFTService @Inject()(
                            aftConnector: AFTConnector,
                            userAnswersCacheConnector: UserAnswersCacheConnector,
                            schemeService: SchemeService,
                            minimalPsaConnector: MinimalPsaConnector
                          ) {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {
    val ua = if (isAtLeastOneValidCharge(answers)) {
      removeChargesHavingNoMembersOrEmployers(answers)
    } else {
      // if you are here then user must have deleted a member from one of member based charges leaving no undeleted members
      val toReinstate = getDeletedMemberOrEmployerChargeInfoToReinstate(answers)

      println("\n>>>" + toReinstate)

      answers
    }

    aftConnector.fileAFTReturn(pstr, ua).flatMap { _ =>
      ua.remove(IsNewReturn) match {
        case Success(userAnswersWithIsNewReturnRemoved) =>
          userAnswersCacheConnector
            .save(request.internalId, userAnswersWithIsNewReturnRemoved.data)
            .map(_ => ())
        case Failure(ex) => throw ex
      }
    }
  }

  def getAFTDetails(pstr: String, startDate: String, aftVersion: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[JsValue] =
    aftConnector.getAFTDetails(pstr, startDate, aftVersion)

  def retrieveAFTRequiredDetails(srn: String, optionVersion: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext, request: OptionalDataRequest[_]): Future[(SchemeDetails, UserAnswers)] = {
    for {
      schemeDetails <- schemeService.retrieveSchemeDetails(request.psaId.id, srn)
      updatedUA <- updateUserAnswersWithAFTDetails(optionVersion, schemeDetails)
      savedUA <- save(updatedUA)
    } yield {
      (schemeDetails, savedUA)
    }
  }

  private def save(ua: UserAnswers)(implicit request: OptionalDataRequest[_], hc: HeaderCarrier, ec: ExecutionContext): Future[UserAnswers] = {
    val savedJson = if (request.viewOnly) {
      userAnswersCacheConnector.save(request.internalId, ua.data)
    } else {
      userAnswersCacheConnector.saveAndLock(request.internalId, ua.data)
    }
    savedJson.map(jsVal => UserAnswers(jsVal.as[JsObject]))
  }

  private def updateUserAnswersWithAFTDetails(optionVersion: Option[String], schemeDetails: SchemeDetails)
                                             (implicit hc: HeaderCarrier, ec: ExecutionContext, request: OptionalDataRequest[_]): Future[UserAnswers] = {
    def currentUserAnswers: UserAnswers = request.userAnswers.getOrElse(UserAnswers())

    val futureUserAnswers = optionVersion match {
      case None =>
        aftConnector.getListOfVersions(schemeDetails.pstr).map { listOfVersions =>
          if (listOfVersions.isEmpty) {
            currentUserAnswers
              .setOrException(IsNewReturn, true)
              .setOrException(QuarterPage, Quarter("2020-01-01", "2020-03-31"))
              .setOrException(AFTStatusQuery, value = "Compiled")
              .setOrException(SchemeNameQuery, schemeDetails.schemeName)
              .setOrException(PSTRQuery, schemeDetails.pstr)
          } else {
            currentUserAnswers
          }
        }
      case Some(version) =>
        getAFTDetails(schemeDetails.pstr, "2020-01-01", version)
          .map(aftDetails => UserAnswers(aftDetails.as[JsObject]))
    }

    futureUserAnswers.flatMap { ua =>
      ua.get(IsPsaSuspendedQuery) match {
        case None =>
          minimalPsaConnector.isPsaSuspended(request.psaId.id).map { retrievedIsSuspendedValue =>
            ua.setOrException(IsPsaSuspendedQuery, retrievedIsSuspendedValue)
          }
        case Some(_) =>
          Future.successful(ua)
      }
    }
  }
}

object AFTService {

  private case class ChargeInfo(jsonNode: String, memberOrEmployerJsonNode: String, isDeleted: (UserAnswers, Int) => Boolean) //, reinstate: (UserAnswers, Int) => UserAnswers)

  private val chargeEInfo = ChargeInfo(
    jsonNode = "chargeEDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeE.MemberDetailsPage(index)).forall(_.isDeleted)
  )

  private val chargeDInfo = ChargeInfo(
    jsonNode = "chargeDDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeD.MemberDetailsPage(index)).forall(_.isDeleted)
  )

  private val chargeGInfo = ChargeInfo(
    jsonNode = "chargeGDetails",
    memberOrEmployerJsonNode = "members",
    isDeleted = (ua, index) => ua.get(pages.chargeG.MemberDetailsPage(index)).forall(_.isDeleted)
  )

  private val chargeCInfo = ChargeInfo(
    jsonNode = "chargeCDetails",
    memberOrEmployerJsonNode = "employers",
    isDeleted = (ua, index) =>
      (ua.get(pages.chargeC.IsSponsoringEmployerIndividualPage(index)), ua.get(pages.chargeC.SponsoringIndividualDetailsPage(index)), ua.get(pages.chargeC.SponsoringOrganisationDetailsPage(index))) match {
        case (Some(true), Some(individual), _) => individual.isDeleted
        case (Some(false), _, Some(organisation)) => organisation.isDeleted
        case _ => true
      }
  )

  private def countMembersOrEmployers(ua: UserAnswers, chargeInfo: ChargeInfo, isDeleted:Boolean = false): Int = {
    (ua.data \ chargeInfo.jsonNode \ chargeInfo.memberOrEmployerJsonNode).validate[JsArray] match {
      case JsSuccess(array, _) =>
        array.value.indices.map(index => chargeInfo.isDeleted(ua, index)).count(_ == isDeleted)
      case JsError(_) => 0
    }
  }

  def isAtLeastOneValidCharge(ua: UserAnswers): Boolean = {
    ua.get(pages.chargeA.ChargeDetailsPage).isDefined ||
      ua.get(pages.chargeB.ChargeBDetailsPage).isDefined ||
      countMembersOrEmployers(ua, chargeCInfo) > 0 ||
      countMembersOrEmployers(ua, chargeDInfo) > 0 ||
      countMembersOrEmployers(ua, chargeEInfo) > 0 ||
      ua.get(pages.chargeF.ChargeDetailsPage).isDefined ||
      countMembersOrEmployers(ua, chargeGInfo) > 0
  }

  private def getDeletedMemberOrEmployerChargeInfoToReinstate(ua: UserAnswers): ChargeInfo = {
    val seqChargeInfo = Seq(
      if (countMembersOrEmployers(ua, chargeCInfo) == 0 && countMembersOrEmployers(ua, chargeCInfo, isDeleted = true) > 0) Seq(chargeCInfo) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeDInfo) == 0 && countMembersOrEmployers(ua, chargeDInfo, isDeleted = true) > 0) Seq(chargeDInfo) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeEInfo) == 0 && countMembersOrEmployers(ua, chargeEInfo, isDeleted = true) > 0) Seq(chargeEInfo) else Seq.empty,
      if (countMembersOrEmployers(ua, chargeGInfo) == 0 && countMembersOrEmployers(ua, chargeGInfo, isDeleted = true) > 0) Seq(chargeGInfo) else Seq.empty
    ).flatten
    seqChargeInfo.head
  }

  private def reinstateDeletedMemberOrEmployerCharge(ua: UserAnswers, whichCharge: ChargeInfo): UserAnswers = {
    (ua.data \ whichCharge.jsonNode \ whichCharge.memberOrEmployerJsonNode).validate[JsArray] match {
      case JsSuccess(array, _) =>
        val itemToReinstate = array.value.indices.reverse.head
        


      case JsError(_) => 0
    }

    ua
  }

  def removeChargesHavingNoMembersOrEmployers(answers: UserAnswers): UserAnswers = {
    Seq(chargeCInfo, chargeDInfo, chargeEInfo, chargeGInfo).foldLeft(answers) { (currentUA, chargeInfo) =>
      if (countMembersOrEmployers(currentUA, chargeInfo) == 0) {
        currentUA.removeWithPath(JsPath \ chargeInfo.jsonNode)
      } else {
        currentUA
      }
    }
  }
}
