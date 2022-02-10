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

object StringHelper {

  //scalastyle:off cyclomatic.complexity
  private def splitForQuotedElements(s: String, delimiter: Char): Seq[String] = {
    case class SplitState(acc: Seq[String], isInQuotes: Boolean, current: String)
    val lastCharIndex = s.length - 1
    val result = s.zipWithIndex.foldLeft[SplitState](SplitState(acc = Nil, isInQuotes = false, current = "")) { case (acc, Tuple2(c, index)) =>
      def isDoubleQuotes = c == '"'
      def isDelimiter = c == delimiter
      def isLastChar = index == lastCharIndex

      if (isLastChar) {
        val curr = if (isDelimiter) {
          acc.current
        } else {
          acc.current + c
        }
        SplitState(acc.acc ++ Seq(curr), acc.isInQuotes, acc.current)
      } else {
        if (acc.isInQuotes) {
          SplitState(acc.acc, isInQuotes = if (isDoubleQuotes) false else acc.isInQuotes, acc.current + c)
        } else {
          if (isDoubleQuotes) {
            SplitState(acc.acc, isInQuotes = true, acc.current + c)
          } else {
            if (c == delimiter) {
              SplitState(acc.acc ++ Seq(acc.current), isInQuotes = false, "")
            } else {
              SplitState(acc.acc, isInQuotes = false, acc.current + c)
            }
          }
        }
      }
    }
    result.acc
  }


  def split(s: String, delimiter: Char): Seq[String] = {
    if (s.contains('"')) {
      splitForQuotedElements(s, delimiter)
    } else {
      s.split(delimiter)
    }
  }
}
