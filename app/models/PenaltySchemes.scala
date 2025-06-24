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

package models

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.Aliases.{Hint, RadioItem, Text}
import viewmodels.Radios.Radio
import viewmodels.{LabelClasses, Radios}

import scala.language.implicitConversions

case class PenaltySchemes(name: Option[String], pstr: String, srn: Option[String], hintText: Option[DisplayHint])

object PenaltySchemes extends Enumerable.Implicits {

  def values(schemes: Seq[PenaltySchemes]): Seq[String] = schemes.map(_.pstr)

  def radios(form: Form[?], schemes: Seq[PenaltySchemes], hintClass: Seq[String] = Nil, areLabelsBold: Boolean = true)
            (implicit messages: Messages):
      Seq[RadioItem] = {

    val radio: Seq[Radio] = schemes.map { scheme =>
      if (scheme.name.isDefined) {
        Radios.Radio(
          label = Text(Messages(s"${scheme.name.getOrElse("")} (${scheme.pstr})")),
          value = scheme.pstr,
          hint = getHint(scheme, hintClass),
          labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil))
        )
      } else {
        Radios.Radio(
          label = Text(s"${scheme.pstr}" ),
          value = scheme.pstr,
          hint = getHint(scheme, hintClass),
          labelClasses = Some(LabelClasses(classes = if(areLabelsBold) Seq("govuk-!-font-weight-bold") else Nil))
        )
      }
    }

    Radios(form("value"), radio)
  }

  implicit def enumerable(schemes: Seq[PenaltySchemes]): Enumerable[PenaltySchemes] =
    Enumerable(schemes.map(v => v.pstr -> v)*)

  private def getHint(schemes: PenaltySchemes, hintClass: Seq[String])(implicit messages: Messages): Option[Hint] =
    schemes.hintText match {
      case Some(hint) => Some(Hint(content = Text(Messages(s"${hint.toString}")), id = Some("hint-id"), classes = hintClass.mkString(" ")))
      case _ => None
    }
}
