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

package pages.chargeC
import models.{TolerantAddress, UserAnswers}
import pages.QuestionPage
import play.api.libs.json.JsPath

import scala.util.Try

case class SponsoringEmployerAddressResultsPage(index: Int) extends QuestionPage[TolerantAddress] {
  override def path: JsPath = JsPath \ SponsoringEmployerAddressResultsPage.toString

  override def cleanup(value: Option[TolerantAddress], userAnswers: UserAnswers): Try[UserAnswers] =
   userAnswers.removeWithCleanup(SponsoringEmployerAddressPage(index)).flatMap { ua =>
      super.cleanup(value, ua)
    }
}

object SponsoringEmployerAddressResultsPage {
  override def toString: String = "sponsoringEmployerAddressResults"
}
