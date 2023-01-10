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

package controllers.mccloud

import models.ChargeType
import models.ChargeType._
import play.api.mvc.Result
import play.api.mvc.Results.Redirect
import uk.gov.hmrc.viewmodels.Text.Message

import scala.concurrent.Future

trait CommonMcCloud {
  def ordinal(index: Option[Int]): Option[Message] = {
    index match {
      case Some(i) if i > 0 && i < 5 => Some(Message(s"mccloud.scheme.ref$i"))
      case _ => None
    }
  }

  def lifetimeOrAnnual(chargeType: ChargeType): Option[Message] = {
    chargeType match {
      case ChargeTypeAnnualAllowance => Some(Message("chargeType.description.annualAllowance"))
      case ChargeTypeLifetimeAllowance => Some(Message("chargeType.description.lifeTimeAllowance"))
      case _ => None
    }
  }

  protected def sessionExpired: Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
}
