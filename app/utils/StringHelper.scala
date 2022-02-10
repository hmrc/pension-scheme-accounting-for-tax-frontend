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
  private def ook(s: String, delimiter: Char): Seq[String] = {
    var isInQuotes: Boolean = false
    var current: String = ""
    val lastCharIndex = s.length - 1
    s.zipWithIndex.foldLeft[Seq[String]](Nil) { case (acc, Tuple2(c, i)) =>
      def isDoubleQuotes = c == '"'

      def isDelimiter = c == delimiter

      def isLastChar = i == lastCharIndex

      if (isLastChar) {
        if (!isDelimiter) {
          current += c
        }
        acc ++ Seq(current)
      } else {
        if (isInQuotes) {
          if (isDoubleQuotes) {
            isInQuotes = false
          }
          current += c
          acc
        } else {
          if (isDoubleQuotes) {
            isInQuotes = true
            current += c
            acc
          } else {
            if (c == delimiter) {
              val updatedAcc = acc ++ Seq(current)
              current = ""
              updatedAcc
            } else {
              current += c
              acc
            }
          }
        }
      }
    }
  }

  def split(s: String, delimiter: Char): Seq[String] = {
    if (s.contains('"')) {
      ook(s, delimiter)
    } else {
      s.split(delimiter)
    }
  }
}
