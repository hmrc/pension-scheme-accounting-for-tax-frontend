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

package controllers.chargeC

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import helpers.ErrorHelper.recoverFrom5XX
import helpers.{CYAChargeCHelper, ChargeServiceHelper}
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Index, NormalMode}
import navigators.CompoundNavigator
import pages.ViewOnlyAccessiblePage
import pages.chargeC._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.AFTService
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.CheckYourAnswersView

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftService: AFTService,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           chargeServiceHelper: ChargeServiceHelper,
                                           checkYourAnswersView: CheckYourAnswersView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async {
    implicit request =>
      DataRetrievals.cyaChargeC(index, srn, startDate, accessType, version) { (whichTypeOfSponsoringEmployer, sponsorDetails, address, chargeDetails, schemeName) =>
        val helper = new CYAChargeCHelper(srn, startDate, accessType, version)
        val seqRows: Seq[SummaryListRow] = Seq(
          Seq(helper.chargeCWhichTypeOfSponsoringEmployer(index, whichTypeOfSponsoringEmployer)),
          helper.chargeCEmployerDetails(index, sponsorDetails),
          Seq(helper.chargeCAddress(index, address, sponsorDetails)),
          helper.chargeCChargeDetails(index, chargeDetails)
        ).flatten

        Future.successful(Ok(checkYourAnswersView(
          "chargeC",
          helper.rows(request.isViewOnly, seqRows),
          !request.isViewOnly,
          returnToSummaryLink = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName,
          submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, version, index).url
        )))
      }
    }

  def onClick(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        val totalAmount = chargeServiceHelper.totalAmount(request.userAnswers, "chargeCDetails")
        (for {
          updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
          _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data, chargeType = Some(ChargeType.ChargeTypeAuthSurplus))
          _ <- aftService.fileCompileReturn(pstr, updatedAnswers, srn)
        } yield {
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        }) recoverWith recoverFrom5XX(srn, startDate)
      }
    }
}
