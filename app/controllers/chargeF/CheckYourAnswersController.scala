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

package controllers.chargeF

import com.google.inject.Inject
import config.FrontendAppConfig
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.ErrorHelper.recoverFrom5XX
import helpers.{CYAChargeFHelper, DeleteChargeHelper}
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{AccessType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.ViewOnlyAccessiblePage
import pages.chargeF.{ChargeDetailsPage, CheckYourAnswersPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}

import java.time.LocalDate
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

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.cyaChargeGeneric(ChargeDetailsPage, srn, startDate, accessType, version) { (chargeDetails, schemeName) =>
        val helper = new CYAChargeFHelper(srn, startDate, accessType, version)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.chargeFDate(chargeDetails),
          helper.chargeFAmount(chargeDetails)
        )

        renderer
          .render(
            "check-your-answers.njk",
            Json.obj(
              "list" -> helper.rows(request.isViewOnly, seqRows),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, version).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              ),
              "returnToSummaryLink" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
              "chargeName" -> "chargeF",
              "removeChargeUrl" -> getDeleteChargeUrl(srn, startDate, accessType, version),
              "canChange" -> !request.isViewOnly
            )
          )
          .map(Ok(_))
      }
    }

  private def getDeleteChargeUrl(srn: String, startDate: String, accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): String =
    if(deleteChargeHelper.isLastCharge(request.userAnswers) && request.aftVersion > 1) {
      routes.RemoveLastChargeController.onPageLoad(srn, startDate, accessType, version).url
    } else {
      routes.DeleteChargeController.onPageLoad(srn, startDate, accessType, version).url
    }

  def onClick(srn: String, startDate: LocalDate, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        aftService.fileCompileReturn(pstr, request.userAnswers).map { _ =>
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        } recoverWith recoverFrom5XX(srn, startDate)
      }
  }
}
