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

package controllers.chargeE

import com.google.inject.Inject
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.ErrorHelper.recoverFrom5XX
import helpers.{CYAChargeEHelper, ChargeServiceHelper}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.{AccessType, ChargeType, CheckMode, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.{MemberFormCompleted, ViewOnlyAccessiblePage}
import pages.chargeE.{CheckYourAnswersPage, TotalChargeAmountPage}
import pages.mccloud.SchemePathHelper
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import services.AFTService
import uk.gov.hmrc.govukfrontend.views.viewmodels.summarylist.SummaryListRow
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import views.html.CheckYourAnswersView

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
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.cyaChargeE(index, srn, startDate, accessType, version) {
        (memberDetails, taxYear, chargeEDetails, pensionsRemedySummary, schemeName) =>
          val helper = new CYAChargeEHelper(srn, startDate, accessType, version)
          val pensionsSchemeSize = pensionsSchemeCount(request.userAnswers, index)
          val wasAnotherPensionSchemeVal = pensionsRemedySummary.wasAnotherPensionScheme.getOrElse(false)

          val seqRows: Seq[SummaryListRow] = Seq(
            helper.isPsprForCharge(index, pensionsRemedySummary.isPublicServicePensionsRemedy),
            helper.chargeEMemberDetails(index, memberDetails),
            helper.chargeETaxYear(index, taxYear),
            helper.chargeEDetails(index, chargeEDetails),
            helper.psprChargeDetails(index, pensionsRemedySummary).getOrElse(None),
            helper.psprSchemesChargeDetails(index, pensionsRemedySummary, wasAnotherPensionSchemeVal)
          ).flatten

          Future.successful(Ok(checkYourAnswersView(
            "chargeE",
            helper.rows(request.isViewOnly, seqRows),
            !request.isViewOnly,
            showAnotherSchemeBtn = (pensionsSchemeSize < 5 && wasAnotherPensionSchemeVal),
            selectAnotherSchemeUrl = controllers.mccloud.routes.AddAnotherPensionSchemeController
              .onPageLoad(ChargeType.ChargeTypeAnnualAllowance, CheckMode, srn, startDate, accessType, version, index, pensionsSchemeSize - 1)
              .url,
            returnToSummaryLink = controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName,
            submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, version, index).url
          )))
      }
    }

  private def pensionsSchemeCount(userAnswers: UserAnswers, index: Int): Int = {
    SchemePathHelper.path(ChargeTypeAnnualAllowance, index).readNullable[JsArray].reads(userAnswers.data).asOpt.flatten.map(_.value.size).getOrElse(0)
  }

  def onClick(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        val totalAmount = chargeServiceHelper.totalAmount(request.userAnswers, "chargeEDetails")
        for {
          updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount).flatMap { ua =>
           ua.set(MemberFormCompleted("chargeEDetails", index), true)
          })
          _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data, chargeType = Some(ChargeType.ChargeTypeAnnualAllowance), memberNo = Some(index.id))
          _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data, chargeType = Some(ChargeType.ChargeTypeAnnualAllowance))
          _ <- aftService.fileCompileReturn(pstr, updatedAnswers, srn)
        } yield {
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        }
      } recoverWith recoverFrom5XX(srn, startDate)
    }
}
