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

import java.time.LocalDate

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.DataRetrievals
import controllers.actions.AllowAccessActionProvider
import controllers.actions.DataRequiredAction
import controllers.actions.DataRetrievalAction
import controllers.actions.IdentifierAction
import models.LocalDateBinder._
import models.GenericViewModel
import models.NormalMode
import navigators.CompoundNavigator
import pages.chargeF.ChargeDetailsPage
import pages.chargeF.CheckYourAnswersPage
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import uk.gov.hmrc.viewmodels.SummaryList
import utils.CheckYourAnswersHelper

import scala.concurrent.ExecutionContext

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftService: AFTService,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.cyaChargeGeneric(ChargeDetailsPage, srn, startDate) { (chargeDetails, schemeName) =>
        val helper = new CheckYourAnswersHelper(request.userAnswers, srn, startDate)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.chargeFDate(chargeDetails),
          helper.chargeFAmount(chargeDetails)
        )

        renderer
          .render(
            "check-your-answers.njk",
            Json.obj(
              "srn" -> srn,
              "startDate" -> Some(startDate),
              "list" -> helper.rows(request.viewOnly, seqRows),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName
              ),
              "chargeName" -> "chargeF",
              "canChange" -> !request.viewOnly
            )
          )
          .map(Ok(_))
      }
    }

  def onClick(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        aftService.fileAFTReturn(pstr, request.userAnswers).map { _ =>
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate))
        }
      }
    }
}
