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

package fileUploadParsers

import base.SpecBase
import helpers.ParserHelper
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

class McCloudParserSpec extends SpecBase
  with Matchers with MockitoSugar with BeforeAndAfterEach with ParserHelper {
  //scalastyle:off magic.number

  override def beforeEach(): Unit = {

  }

  "McCloud parser" must {
    "give correct value when only first in group has a value" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d"), 0) mustBe 2
    }
    "give correct value when only second in group has a value" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "", "d"), 0) mustBe 2
    }
    "give correct value when only third in group has a value" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "", "", "d"), 0) mustBe 2
    }

    "give correct value when only one group" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c"), 0) mustBe 1
    }

    "give correct value when empty" in {
      McCloudParser.countNoOfSchemes(Nil, 0) mustBe 0
    }

    "give correct value when 2 groups only are present" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d", "e", "f"), 0) mustBe 2
    }

    "give correct value when 3 groups are present" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d", "e", "f", "g"), 0) mustBe 3
    }

    "give correct value when 4 groups are present" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), 0) mustBe 4
    }

    "give correct value when 4 groups are present entirely" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"), 0) mustBe 4
    }

    "give correct value when 5 groups are present starting from element 0" in {
      McCloudParser.countNoOfSchemes(Seq("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m"), 0) mustBe 5
    }

    "give correct value when 4 groups are present starting from element 1" in {
      McCloudParser.countNoOfSchemes(Seq("z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"), 1) mustBe 4
    }

    "give correct value when 5 groups are present starting from element 1" in {
      McCloudParser.countNoOfSchemes(Seq("z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m"), 1) mustBe 5
    }
  }
}

