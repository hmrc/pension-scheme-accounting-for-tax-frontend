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

package viewmodels

import uk.gov.hmrc.govukfrontend.views.Aliases.Table
import uk.gov.hmrc.govukfrontend.views.viewmodels.content.HtmlContent
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow

case class PsaChargeDetailsViewModel(
                                   heading: String,
                                   schemeAssociated: Boolean = true,
                                   psaName: String,
                                   schemeName: String,
                                   isOverdue: Boolean,
                                   period: Option[String] = None,
                                   paymentDueAmount: Option[String] = None,
                                   paymentDueDate: Option[String] = None,
                                   chargeReference: String,
                                   penaltyAmount: BigDecimal,
                                   insetText: HtmlContent,
                                   isInterestPresent: Boolean,
                                   list: Option[Seq[SummaryListRow]] = None,
                                   chargeHeaderDetails: Option[Seq[SummaryListRow]] = None,
                                   chargeAmountDetails: Option[Seq[Table]] = None,
                                   returnUrl: String,
                                   returnUrlText: String
                                 )
