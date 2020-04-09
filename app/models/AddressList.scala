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

package models

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.viewmodels._

sealed trait AddressList

object AddressList extends Enumerable.Implicits {

  case object Option1 extends WithName("option1") with AddressList
  case object Option2 extends WithName("option2") with AddressList

  val values: Seq[AddressList] = Seq(
    Option1,
    Option2
  )

  def radios(form: Form[_])(implicit messages: Messages): Seq[Radios.Item] = {

    val field = form("value")
    val items = Seq(
      Radios.Radio(msg"addressList.option1", Option1.toString),
      Radios.Radio(msg"addressList.option2", Option2.toString)
    )

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[AddressList] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
