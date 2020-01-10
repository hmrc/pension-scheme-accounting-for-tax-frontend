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

package generators

import models.UserAnswers
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.TryValues
import pages._
import pages.chargeC.{IsSponsoringEmployerIndividualPage, SponsoringEmployerAddressPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeE.DeleteMemberPage
import pages.chargeF.ChargeDetailsPage
import play.api.libs.json.{JsValue, Json}

trait UserAnswersGenerator extends TryValues {
  self: Generators =>

  val generators: Seq[Gen[(QuestionPage[_], JsValue)]] =
    arbitrary[(SponsoringIndividualDetailsPage.type, JsValue)] ::
    arbitrary[(SponsoringEmployerAddressPage.type, JsValue)] ::
    arbitrary[(SponsoringOrganisationDetailsPage.type, JsValue)] ::
    arbitrary[(IsSponsoringEmployerIndividualPage.type, JsValue)] ::
    arbitrary[(AFTSummaryPage.type, JsValue)] ::
    arbitrary[(DeleteMemberPage.type, JsValue)] ::
    arbitrary[(ChargeTypePage.type, JsValue)] ::
    arbitrary[(ChargeDetailsPage.type, JsValue)] ::
    Nil

  implicit lazy val arbitraryUserData: Arbitrary[UserAnswers] = {

    import models._

    Arbitrary {
      for {
        id      <- nonEmptyString
        data    <- generators match {
          case Nil => Gen.const(Map[QuestionPage[_], JsValue]())
          case _   => Gen.mapOf(oneOf(generators))
        }
      } yield UserAnswers (
        data = data.foldLeft(Json.obj()) {
          case (obj, (path, value)) =>
            obj.setObject(path.path, value).get
        }
      )
    }
  }
}
