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

package utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import services.FuzzyMatchingService

class FuzzyMatchingServiceSpec extends AnyFreeSpec with Matchers {

  private val fuzzyMatchingService = new FuzzyMatchingService
  private val inputString = "Bob Smith Steven Alastair Marrie Augustine Philip Cameron David Constantine ki Delaney Dave Atticus"

  "doFuzzyMatching" - {
    "must return false" - {
      "when search string is of 2 characters or less" in {
        val result = fuzzyMatchingService.doFuzzyMatching(searchString = "ki", inputString)
        result mustBe false
      }

      Seq("bop", "dabe").foreach { searchString =>
        s"when search string $searchString is of 3 or 4 characters and its not a 100% match" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe false
        }
      }

      Seq("Stephen", "Snath", "Merree", "Alisteir", "Auhastine", "Camiran", "Comstamtime").foreach { searchString =>
        s"when search string $searchString is 5 characters or more and its not an 80% match" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe false
        }
      }

      Seq("Bob Snath", "Stephen Alastair", "Merree Augustine", "Cameron David Comstamtime", "Marrie Augustine Philip Camiran").foreach { searchString =>
        s"when search string $searchString is of two or more words and not all the words are matching" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe false
        }
      }
    }

    "must return true" - {

      Seq("bob", "dave").foreach { searchString =>
        s"when search string $searchString is of 3 or 4 characters and its a 100% match" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe true
        }
      }

      //5, 6, 7, 8, 9 char - 1 change allowed, 10, 11 char - 2 change allowed
      Seq("Stepen", "Smath", "Alistair", "Auhustine", "Camiron", "Comstamtine").foreach { searchString =>
        s"when search string $searchString is 5 characters or more and its more than 80% match" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe true
        }
      }

      Seq("Bob Smath", "Steven Alistair", "Marrie Auhustine", "Camiron David Comstamtine", "Marree Augustine Philiv Cameron").foreach { searchString =>
        s"when search string $searchString is of two or more words and all of the words are matching" in {
          val result = fuzzyMatchingService.doFuzzyMatching(searchString, inputString)
          result mustBe true
        }
      }
    }
  }

}
