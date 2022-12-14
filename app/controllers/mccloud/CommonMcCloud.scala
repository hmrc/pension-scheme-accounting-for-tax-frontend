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

package controllers.mccloud

import models.ChargeType
import models.ChargeType._
import models.requests.DataRequest
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.AnyContent
import play.api.mvc.Results.Redirect

import scala.concurrent.Future

trait CommonMcCloud extends I18nSupport {
  protected def ordinal(index: Option[Int])(implicit request: DataRequest[AnyContent]): Option[String] = {
    index match {
      case Some(0) | None => Some("")
      case Some(i) if i > 0 && i < 5 => Some(Messages(s"mccloud.scheme.ref$i"))
      case _ => None
    }
  }

  protected def lifetimeOrAnnual(chargeType: ChargeType)(implicit request: DataRequest[AnyContent]): Option[String] = {
    chargeType match {
      case ChargeTypeAnnualAllowance => Some(Messages("chargeType.description.annualAllowance"))
      case ChargeTypeLifetimeAllowance => Some(Messages("chargeType.description.lifeTimeAllowance"))
      case _ => None
    }
  }

  protected def sessionExpired = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

}
