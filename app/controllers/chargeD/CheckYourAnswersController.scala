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

package controllers.chargeD

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.ErrorHelper.recoverFrom5XX
import helpers.{CYAChargeDHelper, ChargeServiceHelper}
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.chargeD.ChargeDDetails
import models.{AccessType, ChargeType, CheckMode, GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.chargeD.{ChargeDetailsPage, CheckYourAnswersPage, TotalChargeAmountPage}
import pages.mccloud.SchemePathHelper
import pages.{MemberFormCompleted, PSTRQuery, QuarterPage, ViewOnlyAccessiblePage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}

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
                                           config: FrontendAppConfig,
                                           val controllerComponents: MessagesControllerComponents,
                                           chargeServiceHelper: ChargeServiceHelper,
                                           renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.cyaChargeD(index, srn, startDate, accessType, version) { (memberDetails, chargeDetails, pensionsRemedySummary, schemeName) =>
        val helper = new CYAChargeDHelper(srn, startDate, accessType, version)
        val pensionsSchemeSize = pensionsSchemeCount(request.userAnswers, index)
        val wasAnotherPensionSchemeVal = pensionsRemedySummary.wasAnotherPensionScheme.getOrElse(false)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.isPsprForChargeD(isPsrAlwaysTrue(request.userAnswers), index, pensionsRemedySummary.isPublicServicePensionsRemedy),
          helper.chargeDMemberDetails(index, memberDetails),
          helper.chargeDDetails(index, chargeDetails),
          Seq(helper.total(chargeDetails.total)),
          helper.psprChargeDetails(index, pensionsRemedySummary).getOrElse(None),
          helper.psprSchemesChargeDetails(index, pensionsRemedySummary, wasAnotherPensionSchemeVal)
        ).flatten

        renderer
          .render(
            "check-your-answers.njk",
            Json.obj(
              "list" -> helper.rows(request.isViewOnly, seqRows),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn, startDate, accessType, version, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              ),
              "selectAnotherSchemeUrl" -> controllers.mccloud.routes.AddAnotherPensionSchemeController
                .onPageLoad(ChargeType.ChargeTypeLifetimeAllowance, CheckMode, srn, startDate, accessType, version, index, pensionsSchemeSize - 1)
                .url,
              "returnToSummaryLink" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
              "chargeName" -> "chargeD",
              "showAnotherSchemeBtn" -> (pensionsSchemeSize < 5 && wasAnotherPensionSchemeVal),
              "canChange" -> !request.isViewOnly
            )
          )
          .map(Ok(_))
      }
    }

  private def isPsrAlwaysTrue(ua: UserAnswers): Boolean = {
    ua.get(QuarterPage) match {
      case Some(aftQuarter) =>
        val mccloudPsrAlwaysTrueStartDate = config.mccloudPsrAlwaysTrueStartDate
        aftQuarter.startDate.isAfter(mccloudPsrAlwaysTrueStartDate) || aftQuarter.startDate.isEqual(mccloudPsrAlwaysTrueStartDate)
      case _ => false
    }
  }

  private def pensionsSchemeCount(userAnswers: UserAnswers, index: Int): Int = {
    SchemePathHelper
      .path(ChargeTypeLifetimeAllowance, index)
      .readNullable[JsArray]
      .reads(userAnswers.data)
      .asOpt
      .flatten
      .map(_.value.size)
      .getOrElse(0)
  }

  def onClick(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      (request.userAnswers.get(PSTRQuery), request.userAnswers.get(ChargeDetailsPage(index))) match {
        case (Some(pstr), Some(chargeDetails)) =>
          val totalAmount: BigDecimal = chargeServiceHelper.totalAmount(request.userAnswers, "chargeDDetails")
          val updatedChargeDetails: ChargeDDetails = chargeDetails.copy(
            taxAt25Percent = Option(chargeDetails.taxAt25Percent.getOrElse(BigDecimal(0.00))),
            taxAt55Percent = Option(chargeDetails.taxAt55Percent.getOrElse(BigDecimal(0.00)))
          )

          (for {
            ua1 <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
            ua2 <- Future.fromTry(ua1.set(ChargeDetailsPage(index), updatedChargeDetails))
            ua3 <- Future.fromTry(ua2.set(MemberFormCompleted("chargeDDetails",index), true))
            _ <- userAnswersCacheConnector.savePartial(request.internalId, ua3.data, chargeType = Some(ChargeType.ChargeTypeLifetimeAllowance))
            _ <- userAnswersCacheConnector.savePartial(request.internalId,
              ua3.data,
              chargeType = Some(ChargeType.ChargeTypeLifetimeAllowance),
              memberNo = Some(index.id))
            _ <- aftService.fileCompileReturn(pstr, ua2)
          } yield {
            Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
          }) recoverWith recoverFrom5XX(srn, startDate)
        case _ =>
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
      }
    }
}
