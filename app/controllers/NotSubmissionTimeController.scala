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

package controllers

import config.FrontendAppConfig
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, IdentifierAction}
import models.CommonQuarters
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results.Ok
import play.api.mvc.{Action, AnyContent}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import views.html.NotSubmissionTimeView

class NotSubmissionTimeController  @Inject()(override val messagesApi: MessagesApi,
                                             notSubmissionTimeView: NotSubmissionTimeView,
                                             identify: IdentifierAction,
                                             appConfig: FrontendAppConfig,
                                             allowAccess: AllowAccessActionProviderForIdentifierRequest
                                            )(implicit val executionContext: ExecutionContext)
  extends CommonQuarters with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = {
    (identify andThen allowAccess(Some(srn))).async {
      implicit request =>
        Future.successful(Ok(notSubmissionTimeView(
          appConfig.schemeDashboardUrl(request).format(srn),
          getNextQuarterDateAndFormat(startDate)
        )))
    }
  }

  private def getNextQuarterDateAndFormat(startDate: LocalDate): String = {
    val firstDayOfNextQuarter = getQuarter(startDate).endDate.plusDays(1)

    val dateFormatterDMY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

    firstDayOfNextQuarter.format(dateFormatterDMY)
  }
}
