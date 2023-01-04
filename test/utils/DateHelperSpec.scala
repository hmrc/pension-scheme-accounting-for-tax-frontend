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

package utils

import base.SpecBase

import java.time.{LocalDate, ZoneId, ZonedDateTime}

class DateHelperSpec extends SpecBase {

  //scalastyle.off: magic.number
  private def generateDate( hour: Int):ZonedDateTime = {
    ZonedDateTime.of(2020, 4, 12, hour, 2,0,0, ZoneId.of("Europe/London"))
  }

  "formatSubmittedDate" must {
    "display correct morning am date time" in {
      val result = DateHelper.formatSubmittedDate(generateDate(2))
      result mustBe "12 April 2020 at 2:02am"
    }

    "display correct evening pm date time" in {
      val result = DateHelper.formatSubmittedDate(generateDate(17))
      result mustBe "12 April 2020 at 5:02pm"
    }

  }

  "formatDateDMY" must {
    "display correct morning am date" in {
      val result = DateHelper.formatDateDMY(LocalDate.of(2020, 4, 12))
      result mustBe "12 April 2020"
    }
  }

  "formatDateDMYString" must {
    "display correct morning am date" in {
      val result = DateHelper.formatDateDMYString("2020-04-12")
      result mustBe "12 April 2020"
    }
  }

  "formatStartDate" must {
    "display correct morning am date" in {
      val result = DateHelper.formatStartDate(LocalDate.of(2020, 4, 12))
      result mustBe "12 April"
    }
  }

}
