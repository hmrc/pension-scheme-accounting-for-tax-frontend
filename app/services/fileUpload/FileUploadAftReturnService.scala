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

package services.fileUpload
import connectors.cache.UserAnswersCacheConnector
import helpers.ChargeServiceHelper
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import models.requests.DataRequest
import models.{ChargeType, UserAnswers}
import pages.chargeE.TotalChargeAmountPage
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class FileUploadAftReturnService @Inject()(
                                            userAnswersCacheConnector: UserAnswersCacheConnector,
                                            chargeServiceHelper: ChargeServiceHelper
                                          ) {

  def preProcessAftReturn(chargeType: ChargeType,
                          ua: UserAnswers)(implicit ec: ExecutionContext, hc: HeaderCarrier, request: DataRequest[_]): Future[UserAnswers] = {
    for {
      updatedAnswers <- Future.fromTry(updateTotalAmount(chargeType, ua))
      _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
    } yield {
      updatedAnswers
    }
  }

  private def updateTotalAmount(chargeType: ChargeType, ua: UserAnswers): Try[UserAnswers] = {
    chargeType match {
      case ChargeTypeAnnualAllowance |  ChargeTypeLifetimeAllowance | ChargeTypeOverseasTransfer =>
        val totalAmount = chargeServiceHelper.totalAmount(ua, getChargeTypeText(chargeType))
        ua.set(TotalChargeAmountPage, totalAmount)
      case _ => Try(ua)
    }
  }

  private def getChargeTypeText(chargeType: ChargeType): String = {
    chargeType match {
      case ChargeTypeAnnualAllowance   => "chargeEDetails"
      case ChargeTypeLifetimeAllowance => "chargeDDetails"
      case ChargeTypeOverseasTransfer  => "chargeGDetails"
    }
  }
}