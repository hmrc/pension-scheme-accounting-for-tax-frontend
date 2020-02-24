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

package controllers.chargeE

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.SchemeDetailsConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.{GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.SchemeNameQuery
import pages.chargeE.WhatYouWillNeedPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController

import scala.concurrent.ExecutionContext

class WhatYouWillNeedController @Inject()(
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer,
                                           schemeDetailsConnector: SchemeDetailsConnector,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           config: FrontendAppConfig
                                         )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async {
    implicit request =>
      val ua = request.userAnswers

      val viewModel = GenericViewModel(
        submitUrl = navigator.nextPage(WhatYouWillNeedPage, NormalMode, ua, srn, startDate).url,
        returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
        schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")
      )

      renderer.render(template = "chargeE/whatYouWillNeed.njk",
        Json.obj("srn" -> srn,
          "startDate" -> startDate, "viewModel" -> viewModel)).map(Ok(_))
  }
}
