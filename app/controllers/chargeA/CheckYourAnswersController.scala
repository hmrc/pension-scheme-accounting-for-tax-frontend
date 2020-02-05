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

package controllers.chargeA

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.chargeA.ChargeDetails
import models.{GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.PSTRQuery
import pages.chargeA.{ChargeDetailsPage, CheckYourAnswersPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.CheckYourAnswersHelper

import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(override val messagesApi: MessagesApi,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftConnector: AFTConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           config: FrontendAppConfig,
                                           renderer: Renderer
                                          )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData andThen allowAccess(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.cyaChargeGeneric(ChargeDetailsPage, srn) { (chargeDetails, schemeName) =>
          val helper = new CheckYourAnswersHelper(request.userAnswers, srn)

          renderer.render(
            template = "check-your-answers.njk",
            ctx = Json.obj(
              "list" -> Seq(
                helper.chargeAMembers(chargeDetails),
                helper.chargeAAmountLowerRate(chargeDetails),
                helper.chargeAAmountHigherRate(chargeDetails),
                helper.total(chargeDetails.totalAmount)
              ),
              "viewModel" -> GenericViewModel(
                submitUrl = routes.CheckYourAnswersController.onClick(srn).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName
              ),
              "chargeName" -> "chargeA"
            )
          ).map(Ok(_))
      }
  }

  def onClick(srn: String): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      (request.userAnswers.get(PSTRQuery), request.userAnswers.get(ChargeDetailsPage)) match {
        case (Some(pstr), Some(chargeDetails)) =>

          val updatedChargeDetails: ChargeDetails = chargeDetails.copy(
            totalAmtOfTaxDueAtLowerRate = Option(chargeDetails.totalAmtOfTaxDueAtLowerRate.getOrElse(BigDecimal(0.00))),
            totalAmtOfTaxDueAtHigherRate = Option(chargeDetails.totalAmtOfTaxDueAtHigherRate.getOrElse(BigDecimal(0.00)))
          )

          for {
            updatedUserAnswers <- Future.fromTry(request.userAnswers.set(ChargeDetailsPage, updatedChargeDetails))
            _ <- userAnswersCacheConnector.save(request.internalId, updatedUserAnswers.data)
            _ <- aftConnector.fileAFTReturn(pstr, updatedUserAnswers)
          } yield Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, updatedUserAnswers, srn))
        case _ =>
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
  }
}
