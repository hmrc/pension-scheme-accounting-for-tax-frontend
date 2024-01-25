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

package controllers.mccloud

import controllers.base.ControllerSpecBase
import matchers.JsonMatchers
import models.ChargeType
import org.scalatest.{OptionValues, TryValues}
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.Text.Message

class CommonMcCloudSpec extends ControllerSpecBase
  with MockitoSugar with NunjucksSupport with JsonMatchers with OptionValues with TryValues {

  private val commonMcCloud = new CommonMcCloud {}

  "ordinal" must {
    "return correct value for index zero" in {
      commonMcCloud.ordinal(Some(0)) mustBe None
    }

    "return correct value for invalid index < min" in {
      commonMcCloud.ordinal(Some(-1)) mustBe None
    }


    "return correct value for invalid index > max" in {
      commonMcCloud.ordinal(Some(5)) mustBe None
    }

    "return correct value for indexes 1..4" in {
      (1 to 4).foreach { index =>
        commonMcCloud.ordinal(Some(index)) mustBe Some(Message(s"mccloud.scheme.ref$index"))
      }
    }
  }

  "lifetimeOrAnnual" must {
    "return correct value for annual allowance" in {
      commonMcCloud.lifetimeOrAnnual(ChargeType.ChargeTypeAnnualAllowance) mustBe Some(Message("chargeType.description.annualAllowance"))
    }
    "return correct value for lifetime allowance" in {
      commonMcCloud.lifetimeOrAnnual(ChargeType.ChargeTypeLifetimeAllowance) mustBe Some(Message("chargeType.description.lifeTimeAllowance"))
    }

    "return correct value for invalid charge type" in {
      commonMcCloud.lifetimeOrAnnual(ChargeType.ChargeTypeDeRegistration) mustBe None
    }
  }
}
