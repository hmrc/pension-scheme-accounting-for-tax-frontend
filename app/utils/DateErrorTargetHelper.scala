/*
 * Copyright 2025 HM Revenue & Customs
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

object DateErrorTargetHelper {
  val targetField: (String, String) => Map[String, String] = (key: String, message: String) => message match {
    case invalidDay if message.contains("day") => Map(key -> s"$key.day")
    case invalidMonth if message.contains("month") => Map(key -> s"$key.month")
    case invalidYear if message.contains("year") => Map(key -> s"$key.year")
    case _ if message.contains("day") && message.contains("month") && message.contains("year") => Map(key -> s"$key.year")
    case _ => Map(key -> s"$key.day")
  }
}