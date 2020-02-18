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
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import AFTService._

class AFTService @Inject()(
                            aftConnector: AFTConnector,
                            userAnswersCacheConnector: UserAnswersCacheConnector,
                            schemeService: SchemeService,
                            minimalPsaConnector: MinimalPsaConnector
                          ) {

  def fileAFTReturn(pstr: String, answers: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[Unit] = {
    val userAnswersWithInvalidMemberBasedChargesRemoved = removeChargesHavingNoMembersOrEmployers(answers)

    aftConnector.fileAFTReturn(pstr, userAnswersWithInvalidMemberBasedChargesRemoved).flatMap { _ =>
      userAnswersWithInvalidMemberBasedChargesRemoved.remove(IsNewReturn) match {
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
  private case class ChargeInfo(jsonNode: String, memberOrEmployerJsonNode: String, isDeleted: (UserAnswers, Int) => Boolean)

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

  private def countNonDeletedMembersOrEmployers(ua:UserAnswers, chargeInfo: ChargeInfo):Int = {
    (ua.data \ chargeInfo.jsonNode \ chargeInfo.memberOrEmployerJsonNode).validate[JsArray] match {
      case JsSuccess(array, _) =>
        array.value.indices.map(index => chargeInfo.isDeleted(ua, index)).count(_ == false)
      case JsError(_) => 0
    }
  }

  def removeChargesHavingNoMembersOrEmployers(answers: UserAnswers): UserAnswers = {
    Seq(chargeEInfo, chargeDInfo, chargeGInfo, chargeCInfo).foldLeft(answers) { (currentUA, chargeInfo) =>
      if (countNonDeletedMembersOrEmployers(currentUA, chargeInfo) == 0) {
        currentUA.removeWithPath(JsPath \ chargeInfo.jsonNode)
      } else {
        currentUA
      }
    }
  }
}
