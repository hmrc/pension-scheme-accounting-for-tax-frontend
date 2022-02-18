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
  private val delimiter = ','

  private case class AccumulatedState(result: Seq[String], isInQuotes: Boolean, currentElement: String)

  private def stripLeadingAndTrailingDoubleQuotes(s: String): String = {
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

  private val processCharacter: (AccumulatedState, Char) => AccumulatedState = (acc,c) =>
    acc.isInQuotes match {
      case true if c == doubleQuotes => AccumulatedState(acc.result, isInQuotes = false, acc.currentElement + c)
      case true => AccumulatedState(acc.result, isInQuotes = acc.isInQuotes, acc.currentElement + c)
      case false if c == doubleQuotes => AccumulatedState(acc.result, isInQuotes = true, acc.currentElement + c)
      case false if c == delimiter => AccumulatedState(acc.result :+ stripLeadingAndTrailingDoubleQuotes(acc.currentElement), isInQuotes = false, EMPTY)
      case _ => AccumulatedState(acc.result, isInQuotes = false, acc.currentElement + c)
    }

  def split(s: String): Seq[String] = {
    if (s.contains(doubleQuotes)) {
      val accumulatedState = s.foldLeft(AccumulatedState(result = Nil, isInQuotes = false, currentElement = EMPTY))(processCharacter)
      accumulatedState.result :+ stripLeadingAndTrailingDoubleQuotes(accumulatedState.currentElement)
    } else {
      s.split(delimiter)
    }
  }
}
