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
import fileUploadParsers.CsvParser

class StringHelperSpec extends SpecBase {

  "split" must {
    "work where the string is empty" in {
      StringHelper.split("") mustBe Seq("")
    }
    "work for a simple string of elements without quotes" in {
      CsvParser.split("a,bc,def,g").flatten mustBe Seq("a", "bc", "def", "g")
    }
    "work where there are no double quotes in string" in {
      CsvParser.split("a,b,c").flatten mustBe Seq("a", "b", "c")
    }
    "work for double quotes containing comma at start of list" in {
      CsvParser.split(""""a,b",c,d""").flatten mustBe Seq(
        """a,b""",
        "c",
        "d"
      )
    }
    "work for double quotes containing comma in middle of list" in {
      CsvParser.split("""a,"b,c",d""").flatten mustBe Seq(
        "a",
        """b,c""",
        "d"
      )
    }
    "work for double quotes containing comma at end of list" in {
      CsvParser.split("""a,b,"c,d"""").flatten mustBe Seq(
        "a",
        "b",
        """c,d"""
      )
    }

    "work for double quotes containing no comma in middle of list" in {
      CsvParser.split("""a,"b and c",d""").flatten mustBe Seq(
        "a",
        """b and c""",
        "d"
      )
    }

    "leave double quotes containing no comma in middle of list when not first and last characters" in {
      CsvParser.split("""a,b"c"d,e""").flatten mustBe Seq(
        "a",
        """b"c"d""",
        "e"
      )
    }

    "leave double quotes containing a comma in middle of list when not first and last characters" in {
      CsvParser.split("a,\"bc,xd\",e").flatten mustBe Seq(
        "a",
        """bc,xd""",
        "e"
      )
    }

    "work for double quotes containing comma at end of list but quotes not ended" in {
      CsvParser.split("""a,b,"c,d""").flatten mustBe Seq(
        "a",
        "b",
        """c,d"""
      )
    }

    "work for double quotes containing comma at end of list but ending with a comma" in {
      CsvParser.split("""a,b,"c,d",""").flatten mustBe Seq(
        "a",
        "b",
        """c,d""",
        ""
      )
    }

    "strip trailing and leading spaces" in {
      CsvParser.split(""" a , "b and c"  ,   d   """).flatten mustBe Seq(
        "a",
        "b and c",
        "d"
      )
    }

  }

}
