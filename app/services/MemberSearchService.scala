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

package services

import pages.AFTStatusQuery
import pages.IsPsaSuspendedQuery
import pages.PSAEmailQuery
import pages.PSTRQuery
import pages.QuarterPage
import pages.SchemeNameQuery
import pages.SchemeStatusQuery
import play.api.mvc.Request
import uk.gov.hmrc.domain.PsaId
import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import connectors.AFTConnector
import connectors.MinimalPsaConnector
import javax.inject.Singleton
import models.AFTOverview
import models.AccessMode
import models.LocalDateBinder._
import models.Quarters
import models.SchemeStatus.statusByName
import models.SessionAccessData
import models.requests.OptionalDataRequest
import models.SchemeDetails
import models.SessionData
import models.UserAnswers
import pages.PSANameQuery
import play.api.i18n.Messages
import play.api.libs.json._
import uk.gov.hmrc.http.HeaderCarrier
import utils.AFTSummaryHelper
import utils.DateHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class MemberSearchService @Inject()(
    aftConnector: AFTConnector,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    config: FrontendAppConfig,
    aftSummaryHelper: AFTSummaryHelper
) {
  private val ninoRegex = "[[A-Z]&&[^DFIQUV]][[A-Z]&&[^DFIQUVO]] ?\\d{2} ?\\d{2} ?\\d{2} ?[A-D]{1}".r

  def search(ua: UserAnswers, srn: String, startDate: LocalDate, searchText:String)(implicit messages: Messages) = {
    aftSummaryHelper.summaryListData(ua, srn, startDate)
//Seq[MemberSearch]
  }

}
