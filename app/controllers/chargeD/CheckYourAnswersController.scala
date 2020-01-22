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

package controllers.chargeD

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.chargeD.ChargeDDetails
import models.{GenericViewModel, Index, NormalMode}
import navigators.CompoundNavigator
import pages.{PSTRQuery, SchemeNameQuery}
import pages.chargeD.{ChargeDetailsPage, CheckYourAnswersPage, TotalChargeAmountPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.ChargeDService.getLifetimeAllowanceMembers
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.CheckYourAnswersHelper

import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           requireData: DataRequiredAction,
                                           aftConnector: AFTConnector,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer
                                          )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val helper = new CheckYourAnswersHelper(request.userAnswers, srn)
        val total = request.userAnswers.get(ChargeDetailsPage(index)).map(_.total).getOrElse(BigDecimal(0))

        val viewModel = GenericViewModel(
          submitUrl = routes.CheckYourAnswersController.onClick(srn, index).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val answers: Seq[SummaryList.Row] = Seq(
          helper.chargeDMemberDetails(index).get,
          helper.chargeDDetails(index).get,
          Seq(helper.total(total))
        ).flatten

        renderer.render("check-your-answers.njk",
          Json.obj(
            "list" -> answers,
            "viewModel" -> viewModel,
            "chargeName" -> "chargeD"
          )).map(Ok(_))
      }
  }

  def onClick(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      (request.userAnswers.get(PSTRQuery), request.userAnswers.get(ChargeDetailsPage(index))) match {
        case (Some(pstr), Some(chargeDetails)) =>

          val totalAmount: BigDecimal = getLifetimeAllowanceMembers(request.userAnswers, srn).map(_.amount).sum

          val updatedChargeDetails: ChargeDDetails = chargeDetails.copy(
            taxAt25Percent = Option(chargeDetails.taxAt25Percent.getOrElse(BigDecimal(0.00))),
            taxAt55Percent = Option(chargeDetails.taxAt55Percent.getOrElse(BigDecimal(0.00)))
          )

          for {
            ua1 <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
            ua2 <- Future.fromTry(ua1.set(ChargeDetailsPage(index), updatedChargeDetails))
            _ <- userAnswersCacheConnector.save(request.internalId, ua2.data)
            _ <- aftConnector.fileAFTReturn(pstr, ua2)
          } yield {
            Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn))
          }
        case _ =>
          Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
  }
}
