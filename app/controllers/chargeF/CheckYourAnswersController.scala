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
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.CYAChargeFHelper
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.chargeF.{ChargeDetailsPage, CheckYourAnswersPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.DeleteChargeHelper

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
                                           deleteChargeHelper: DeleteChargeHelper,
                                           renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate)).async { implicit request =>
      DataRetrievals.cyaChargeGeneric(ChargeDetailsPage, srn, startDate) { (chargeDetails, schemeName) =>
        val helper = new CYAChargeFHelper(srn, startDate)

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
              "list" -> helper.rows(request.sessionData.isViewOnly, seqRows),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                schemeName = schemeName
              ),
              "chargeName" -> "chargeF",
              "removeChargeUrl" -> getDeleteChargeUrl(srn, startDate),
              "canChange" -> !request.sessionData.isViewOnly
            )
          )
          .map(Ok(_))
      }
    }

  private def getDeleteChargeUrl(srn: String, startDate: String)(implicit request: DataRequest[AnyContent]): String = {
    println(">>>>>>>>>>>>>>>>>>>>>>>.. deleteChargeHelper.hasLastChargeOnly(request.userAnswers) "
      +deleteChargeHelper.hasLastChargeOnly(request.userAnswers))

    if(deleteChargeHelper.hasLastChargeOnly(request.userAnswers) && request.sessionData.sessionAccessData.version > 1) {
      routes.RemoveLastChargeController.onPageLoad(srn, startDate).url
    } else {
      routes.DeleteChargeController.onPageLoad(srn, startDate).url
    }
  }

  def onClick(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        aftService.fileAFTReturn(pstr, request.userAnswers).map { _ =>
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate))
        }
      }
  }
}
