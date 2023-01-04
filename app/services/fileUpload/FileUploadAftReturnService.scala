/*
 * Copyright 2023 HM Revenue & Customs
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

package services.fileUpload
import connectors.cache.UserAnswersCacheConnector
import helpers.{ChargeServiceHelper, ChargeTypeHelper}
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.requests.DataRequest
import models.{AmendedChargeStatus, ChargeType, MemberDetails, UserAnswers}
import pages.{chargeD => chargeDStatus, chargeE => chargeEStatus, chargeG => chargeGStatus}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileUploadAftReturnService @Inject()(
                                            userAnswersCacheConnector: UserAnswersCacheConnector,
                                            chargeServiceHelper: ChargeServiceHelper
                                          ) {
  val acceptedChargeTypes = List(ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer)
  def preProcessAftReturn(chargeType: ChargeType,
                          ua: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[UserAnswers] = {
    val userAnswersWithTotalAmount = updateTotalAmount(chargeType, ua)
    val updatedUserAnswers = setMemberStatus(userAnswersWithTotalAmount, chargeType)(request)
    userAnswersCacheConnector.save(request.internalId, updatedUserAnswers.data).map(_ => updatedUserAnswers)
  }

  private def updateTotalAmount(chargeType: ChargeType, ua: UserAnswers): UserAnswers = {
    if(acceptedChargeTypes.contains(chargeType)) totalCharges(chargeType, ua) else ua
  }

  private def totalCharges(chargeType: ChargeType, ua: UserAnswers): UserAnswers = {
    val totalAmount = chargeServiceHelper.totalAmount(ua, getChargeTypeText(chargeType))
    ua.setOrException(ChargeTypeHelper.getTotalChargeAmountPage(chargeType), totalAmount)
  }

  private def setMemberStatus(ua: UserAnswers, chargeType: ChargeType)(implicit request: DataRequest[_]): UserAnswers = {
    val userAnswersWithMemberStatus = ua.getAllMembersInCharge[MemberDetails](charge = getChargeTypeText(chargeType))
      .zipWithIndex.foldLeft(ua) { case (acc, Tuple2(_, index)) =>
      val memberStatusPage = getMemberStatusPage(chargeType, index)
      if (request.isAmendment) {
        acc.setOrException(memberStatusPage, AmendedChargeStatus.Added.toString)
      } else {
        acc
      }
    }
    userAnswersWithMemberStatus
  }

  private def getMemberStatusPage(chargeType: ChargeType, index: Int)  = {
    val memberStatusPageMap = Map("annualAllowance" -> chargeEStatus.MemberStatusPage(index),
      "lifeTimeAllowance" -> chargeDStatus.MemberStatusPage(index),
      "overseasTransfer" -> chargeGStatus.MemberStatusPage(index))

    memberStatusPageMap(chargeType.toString)
  }


  private def getChargeTypeText(chargeType: ChargeType): String = {
    val map = Map("annualAllowance" -> "chargeEDetails",
      "lifeTimeAllowance" -> "chargeDDetails",
      "overseasTransfer" -> "chargeGDetails")
    map.getOrElse(chargeType.toString, "")
  }
}