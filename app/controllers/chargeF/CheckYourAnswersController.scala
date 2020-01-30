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

package controllers.chargeF

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.AFTConnector
import controllers.DataRetrievals
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.{GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.chargeF.CheckYourAnswersPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.CheckYourAnswersHelper

import scala.concurrent.ExecutionContext

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           requireData: DataRequiredAction,
                                           aftConnector: AFTConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer
                                          )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val helper = new CheckYourAnswersHelper(request.userAnswers, srn)

        val viewModel = GenericViewModel(
          submitUrl = routes.CheckYourAnswersController.onClick(srn).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.chargeFDate.get,
          helper.chargeFAmount.get
        )

        val answers = if(request.viewOnly) seqRows.map(_.copy(actions = Nil)) else seqRows

        renderer.render("check-your-answers.njk",
          Json.obj(
            "list" -> answers,
            "viewModel" -> viewModel,
            "chargeName" -> "chargeF",
            "canChange" -> !request.viewOnly
          )).map(Ok(_))
      }
  }

  def onClick(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        aftConnector.fileAFTReturn(pstr, request.userAnswers).map { _ =>
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn))
        }
      }
  }
}
