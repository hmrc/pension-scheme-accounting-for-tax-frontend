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

package controllers

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import config.FrontendAppConfig
import controllers.actions._
import javax.inject.Inject
import models.GenericViewModel
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.ExecutionContext

class ConfirmationController @Inject()(
                                        override val messagesApi: MessagesApi,
                                        identify: IdentifierAction,
                                        getData: DataRetrievalAction,
                                        requireData: DataRequiredAction,
                                        val controllerComponents: MessagesControllerComponents,
                                        renderer: Renderer,
                                        config: FrontendAppConfig
                                      )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeNameWithPSTRAndQuarter { (schemeName, pstr, quarter) =>

        val quarterStartDate = LocalDate.parse(quarter.startDate).format(DateTimeFormatter.ofPattern("d MMMM"))
        val quarterEndDate = LocalDate.parse(quarter.endDate).format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
        val submittedDate = DateTimeFormatter.ofPattern("d MMMM yyyy 'at' hh:mm a").format(LocalDateTime.now())
        val listSchemesUrl = config.yourPensionSchemesUrl

        val json = Json.obj(
          fields = "srn" -> srn,
          "pstr" -> pstr,
          "pensionSchemesUrl" -> listSchemesUrl,
          "quarterStartDate" -> quarterStartDate,
          "quarterEndDate" -> quarterEndDate,
          "submittedDate" -> submittedDate,
          "viewModel" -> GenericViewModel(
            submitUrl = controllers.routes.SignOutController.signOut(srn).url,
            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
            schemeName = schemeName)
        )
        renderer.render("confirmation.njk", json).map(Ok(_))
      }
  }
}
