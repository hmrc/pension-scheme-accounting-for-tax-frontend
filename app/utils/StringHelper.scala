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

import org.apache.commons.lang3.StringUtils.EMPTY

object StringHelper {
  private val doubleQuotes = '"'

  private def stripLeadingAndTrailingDoubleQuotes(s:String): String = {
    val trimmed = s.trim
    val length = trimmed.length

    if (length > 1) {
      if (trimmed.head == doubleQuotes && trimmed.last == doubleQuotes) {
        trimmed.substring(1, length - 1)
      } else {
        trimmed
      }
    } else {
      trimmed
    }
  }

  private def splitForQuotedElements(s: String, delimiter: Char): Seq[String] = {
    case class AccumulatedState(result: Seq[String], isInQuotes: Boolean, currentElement: String)

    val lastCharIndex = s.length - 1
    s.zipWithIndex.foldLeft(AccumulatedState(result = Nil, isInQuotes = false, currentElement = EMPTY)) { case (acc, Tuple2(currentChar, index)) =>
      def isLastChar = index == lastCharIndex
      def isDelimiter = currentChar == delimiter
      def isDoubleQuotes = currentChar == doubleQuotes
      if (isLastChar) {
        AccumulatedState(acc.result :+
          stripLeadingAndTrailingDoubleQuotes(if (isDelimiter) acc.currentElement else acc.currentElement + currentChar), acc.isInQuotes, EMPTY)
      } else {
        if (acc.isInQuotes) {
          AccumulatedState(acc.result, isInQuotes = if (isDoubleQuotes) false else acc.isInQuotes, acc.currentElement + currentChar)
        } else {
          if (isDoubleQuotes) {
            AccumulatedState(acc.result, isInQuotes = true, acc.currentElement + currentChar)
          } else {
            if (isDelimiter) {
              AccumulatedState(acc.result :+ stripLeadingAndTrailingDoubleQuotes(acc.currentElement), isInQuotes = false, EMPTY)
            } else {
              AccumulatedState(acc.result, isInQuotes = false, acc.currentElement + currentChar)
            }
          }
        }
      }
    }.result
  }

  def split(s: String, delimiter: Char): Seq[String] = {
    if (s.contains('"')) {
      splitForQuotedElements(s, delimiter)
    } else {
      s.split(delimiter).map(_.trim)
    }
  }
}
