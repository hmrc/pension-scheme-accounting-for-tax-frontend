/*
 * Copyright 2021 HM Revenue & Customs
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

package models

import org.scalatest.{FreeSpec, MustMatchers}
import play.api.libs.json.{JsString, Json}

class YearsSpec extends FreeSpec with MustMatchers {

  "writes" - {
    "must map correctly to string" in {
      val year = Year(2020)

      val result = Json.toJson(year)
      result mustBe JsString("2020")
    }
  }
}
