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

package utils

import base.SpecBase

class StringHelperSpec extends SpecBase {

  "split" must {
    "work where the string is empty" in {
      StringHelper.split("") mustBe Seq("")
    }
    "work for a simple string of elements without quotes" in {
      StringHelper.split("a,bc,def,g") mustBe Seq("a", "bc", "def", "g")
    }
    "work where there are no double quotes in string" in {
      StringHelper.split("a,b,c") mustBe Seq("a", "b", "c")
    }
    "work for double quotes containing comma at start of list" in {
      StringHelper.split(""""a,b",c,d""") mustBe Seq(
        """a,b""",
        "c",
        "d"
      )
    }
    "work for double quotes containing comma in middle of list" in {
      StringHelper.split("""a,"b,c",d""") mustBe Seq(
        "a",
        """b,c""",
        "d"
      )
    }
    "work for double quotes containing comma at end of list" in {
      StringHelper.split("""a,b,"c,d"""") mustBe Seq(
        "a",
        "b",
        """c,d"""
      )
    }

    "work for double quotes containing no comma in middle of list" in {
      StringHelper.split("""a,"b and c",d""") mustBe Seq(
        "a",
        """b and c""",
        "d"
      )
    }

    "leave double quotes containing no comma in middle of list when not first and last characters" in {
      StringHelper.split("""a,b"c"d,e""") mustBe Seq(
        "a",
        """b"c"d""",
        "e"
      )
    }

    "leave double quotes containing a comma in middle of list when not first and last characters" in {
      StringHelper.split("""a,b"c,x"d,e""") mustBe Seq(
        "a",
        """b"c,x"d""",
        "e"
      )
    }

    "work for double quotes containing comma at end of list but quotes not ended" in {
      StringHelper.split("""a,b,"c,d""") mustBe Seq(
        "a",
        "b",
        """"c,d"""
      )
    }

    "work for double quotes containing comma at end of list but ending with a comma" in {
      StringHelper.split("""a,b,"c,d",""") mustBe Seq(
        "a",
        "b",
        """c,d""",
        ""
      )
    }

    "strip trailing and leading spaces" in {
      StringHelper.split(""" a , "b and c"  ,   d   """) mustBe Seq(
        "a",
        """b and c""",
        "d"
      )
    }

  }

}
