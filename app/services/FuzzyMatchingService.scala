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

package services

class FuzzyMatchingService {

  def doFuzzyMatching(searchString: String, inputString: String): Boolean = {

    if (searchString.length < 3) {
      false
    } else {
      val seqOfSearchStrings = searchString.toLowerCase().split("[ /]").toSeq
      val seqOfInputStrings = inputString.toLowerCase().split("[ /]").toSeq

      val isFound = seqOfSearchStrings.map { iSearchString =>
        seqOfInputStrings.find(_ == iSearchString) match {
          case Some(_) => true
          case _ =>
            seqOfInputStrings.exists { iInputString =>
              iSearchString.length >= 5 &&
                matchPercentage(iInputString, iSearchString) >= 80
            }
        }
      }
      !isFound.contains(false)
    }
  }

  private def matchPercentage(inputString: String, searchString: String): Int = {
    if (inputString.length >= searchString.length) {
      (inputString.length - distance(searchString, inputString)) * 100 / inputString.length
    } else {
      (searchString.length - distance(searchString, inputString)) * 100 / searchString.length
    }
  }

  //how many characters different between search string and input string
  private def distance(searchString: String, inputString: String): Int = {
    val dist = Array.tabulate(inputString.length + 1, searchString.length + 1) { (j, i) => if (j == 0) i else if (i == 0) j else 0 }

    @inline
    def minimum(i: Int*): Int = i.min

    for {
      j <- dist.indices.tail
      i <- dist(0).indices.tail
    } dist(j)(i) =
      if (inputString(j - 1) == searchString(i - 1)) {
        dist(j - 1)(i - 1)
      }
      else {
        minimum(dist(j - 1)(i) + 1, dist(j)(i - 1) + 1, dist(j - 1)(i - 1) + 1)
      }

    dist(inputString.length)(searchString.length)
  }
}
