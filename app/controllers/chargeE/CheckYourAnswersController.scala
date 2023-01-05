/*
 * Copyright 2023 HM Revenue & Customs
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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import helpers.ErrorHelper.recoverFrom5XX
import helpers.{CYAChargeEHelper, ChargeServiceHelper}
import models.ChargeType.ChargeTypeAnnualAllowance
import models.LocalDateBinder._
import models.{AccessType, ChargeType, CheckMode, GenericViewModel, Index, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.ViewOnlyAccessiblePage
import pages.chargeE.{CheckYourAnswersPage, TotalChargeAmountPage}
import pages.mccloud.SchemePathHelper
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import play.api.libs.json.JsArray
import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftService: AFTService,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           chargeServiceHelper: ChargeServiceHelper,
                                           renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen
      allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage), version, accessType)).async { implicit request =>
      DataRetrievals.cyaChargeE(index, srn, startDate, accessType, version) {
        (memberDetails, taxYear, chargeEDetails, pensionsRemedySummary, schemeName) =>
        val helper = new CYAChargeEHelper(srn, startDate, accessType, version)
          val schemeCount = countSchemeSize(request.userAnswers, index)
          val wasAnotherPensionSchemeVal = getWasAnotherPensionScheme(pensionsRemedySummary.wasAnotherPensionScheme)

        val seqRows: Seq[SummaryList.Row] = Seq(
          helper.chargeEMemberDetails(index, memberDetails),
          helper.chargeETaxYear(index, taxYear),
          helper.chargeEDetails(index, chargeEDetails),
          helper.publicServicePensionsRemedyEDetails(index, pensionsRemedySummary),
          helper.publicServicePensionsRemedySchemesEDetails(index, pensionsRemedySummary, wasAnotherPensionSchemeVal)
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
                .onPageLoad(ChargeType.ChargeTypeAnnualAllowance, CheckMode, srn, startDate,
                  accessType, version, index, schemeCount-1).url,
              "returnToSummaryLink" -> controllers.routes.AFTSummaryController.onPageLoad(srn, startDate, accessType, version).url,
              "chargeName" -> "chargeE",
              "showAnotherSchemeBtn" -> (schemeCount < 5 && wasAnotherPensionSchemeVal),
              "canChange" -> !request.isViewOnly
            )
          )
          .map(Ok(_))
      }
    }

  private def countSchemeSize(userAnswers: UserAnswers, index: Int): Int = {
    SchemePathHelper.path(ChargeTypeAnnualAllowance, index).readNullable[JsArray].reads(userAnswers.data).asOpt.flatten.map(_.value.size).getOrElse(0)
  }


  private def getWasAnotherPensionScheme(v: Option[Boolean]): Boolean = {
    v match {
      case Some(booleanVal) => booleanVal
      case _ => false
    }
  }
  def onClick(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        val totalAmount = chargeServiceHelper.totalAmount(request.userAnswers, "chargeEDetails")
        for {
          updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
          _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
            chargeType = Some(ChargeType.ChargeTypeAnnualAllowance))
          _ <- aftService.fileCompileReturn(pstr, updatedAnswers)
        } yield {
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn, startDate, accessType, version))
        }
      } recoverWith recoverFrom5XX(srn, startDate)
    }
}
