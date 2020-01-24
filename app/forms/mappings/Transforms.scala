/*
 * Copyright 2020 HM Revenue & Customs
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

package forms.mappings

trait Transforms {

  val regexPostcode = """^[A-Z]{1,2}[0-9][0-9A-Z]?\s?[0-9][A-Z]{2}$"""

  protected def noTransform(value: String): String = value

  protected def noSpaceWithUpperCaseTransform(value: String): String =
    toUpperCaseAlphaOnly(strip(value))

  protected def toUpperCaseAlphaOnly(value: String): String =
    value.map {
      case c if ('a' to 'z').contains(c) => c.toUpper
      case c                                    => c
    }

  protected def strip(value: String): String = {
    value.replaceAll(" ", "")
  }

  protected def minimiseSpace(value: String): String =
    value.replaceAll(" {2,}", " ")

  protected def standardiseText(s: String): String =
    s.replaceAll("""\s{1,}""", " ").trim

  protected def postCodeDataTransform(value: Option[String]): Option[String] = {
    value.map(postCodeTransform).filter(_.nonEmpty)
  }

  private[mappings] def postCodeTransform(value: String): String = {
    minimiseSpace(value.trim.toUpperCase)
  }

  private[mappings] def postCodeValidTransform(value: String): String = {
    if (value.matches(regexPostcode)) {
      if (value.contains(" ")) {
        value
      } else {
        value.substring(0, value.length - 3) + " " + value.substring(value.length - 3, value.length)
      }
    }
    else {
      value
    }
  }

  protected def countryDataTransform(value: Option[String]): Option[String] = {
    value.map(s => strip(s).toUpperCase()).filter(_.nonEmpty)
  }

}
