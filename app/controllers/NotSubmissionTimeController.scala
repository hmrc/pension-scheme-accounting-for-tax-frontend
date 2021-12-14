/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.actions.IdentifierAction
import helpers.FormatHelper
import renderer.Renderer
import play.api.mvc.Results.Ok
import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class NotSubmissionTimeController @Inject()(renderer: Renderer,
                                            identify: IdentifierAction
                                           )(implicit val executionContext: ExecutionContext) {

  def onPageLoad(srn: String, startDate: LocalDate) = {
    identify.async {
      implicit request =>

        val date = FormatHelper.dateFormatter(startDate)

        startDate.format(FormatHelper.dateFormatter))

        renderer.render("notSubmissionTime.njk").map(Ok(_))
    }
  }
}
