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

package models.chargeG

import play.api.data.Form
import uk.gov.hmrc.govukfrontend.views.viewmodels.radios.RadioItem
import uk.gov.hmrc.govukfrontend.views.viewmodels.table.Table
import viewmodels.Link

case class AddMembersViewModel(form: Form[_],
                               quarterStart: String,
                               quarterEnd: String,
                               table: Table,
                               pageLinksSeq: Seq[Link],
                               paginationStatsStartMember: Int,
                               paginationStatsLastMember: Int,
                               paginationStatsTotalMembers: Int,
                               canChange: Boolean,
                               radios: Seq[RadioItem])
