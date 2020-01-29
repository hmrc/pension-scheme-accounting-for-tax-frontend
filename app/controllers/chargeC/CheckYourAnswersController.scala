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

package controllers.chargeC

import com.google.inject.Inject
import config.FrontendAppConfig
import connectors.AFTConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import models.{GenericViewModel, Index, NormalMode}
import navigators.CompoundNavigator
import pages.chargeC.{CheckYourAnswersPage, TotalChargeAmountPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, SummaryList}
import utils.CheckYourAnswersHelper
import services.ChargeCService._

import scala.concurrent.{ExecutionContext, Future}

class CheckYourAnswersController @Inject()(config: FrontendAppConfig,
                                           override val messagesApi: MessagesApi,
                                           identify: IdentifierAction,
                                           getData: DataRetrievalAction,
                                           allowAccess: AllowAccessActionProvider,
                                           requireData: DataRequiredAction,
                                           aftConnector: AFTConnector,
                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                           navigator: CompoundNavigator,
                                           val controllerComponents: MessagesControllerComponents,
                                           renderer: Renderer
                                          )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen allowAccess(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val helper = new CheckYourAnswersHelper(request.userAnswers, srn)

        val viewModel = GenericViewModel(
          submitUrl = routes.CheckYourAnswersController.onClick(srn, index).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val answers: Seq[SummaryList.Row] = Seq(
          Seq(helper.chargeCIsSponsoringEmployerIndividual(index).get),
          helper.chargeCEmployerDetails(index),
          Seq(helper.chargeCAddress(index).get),
          helper.chargeCChargeDetails(index).get
        ).flatten

        renderer.render("check-your-answers.njk",
          Json.obj(
            "list" -> answers,
            "viewModel" -> viewModel,
            "chargeName" -> "chargeC"
          )).map(Ok(_))
      }
  }

  def onClick(srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        val totalAmount = getSponsoringEmployers(request.userAnswers, srn).map(_.amount).sum
        for {
          updatedAnswers <- Future.fromTry(request.userAnswers.set(TotalChargeAmountPage, totalAmount))
          _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
          _ <- aftConnector.fileAFTReturn(pstr, updatedAnswers)
        } yield {
          Redirect(navigator.nextPage(CheckYourAnswersPage, NormalMode, request.userAnswers, srn))
        }
      }
  }
}
