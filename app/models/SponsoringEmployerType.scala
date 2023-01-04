/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.viewmodels._

sealed trait SponsoringEmployerType

object SponsoringEmployerType extends Enumerable.Implicits {

  case object SponsoringEmployerTypeIndividual extends WithName("individual") with SponsoringEmployerType
  case object SponsoringEmployerTypeOrganisation extends WithName("organisation") with SponsoringEmployerType

  val values: Seq[SponsoringEmployerType] = Seq(
    SponsoringEmployerTypeIndividual,
    SponsoringEmployerTypeOrganisation
  )

  def radios(form: Form[_]): Seq[Radios.Item] = {
    val field = form("value")
    val items = Seq(
      Radios.Radio(msg"chargeC.whichTypeOfSponsoringEmployer.individual", SponsoringEmployerTypeIndividual.toString),
      Radios.Radio(msg"chargeC.whichTypeOfSponsoringEmployer.organisation", SponsoringEmployerTypeOrganisation.toString)
    )

    Radios(field, items)
  }

  implicit val enumerable: Enumerable[SponsoringEmployerType] =
    Enumerable(values.map(v => v.toString -> v): _*)
}
