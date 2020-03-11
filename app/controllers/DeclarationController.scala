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

package controllers

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import javax.inject.Inject
import models.{Declaration, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.{AFTStatusQuery, DeclarationPage}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.AFTService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import models.LocalDateBinder._

import scala.concurrent.{ExecutionContext, Future}

class DeclarationController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    requireData: DataRequiredAction,
    allowAccess: AllowAccessActionProvider,
    allowSubmission: AllowSubmissionAction,
    aftService: AFTService,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    navigator: CompoundNavigator,
    val controllerComponents: MessagesControllerComponents,
    config: FrontendAppConfig,
    renderer: Renderer
)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate)
      andThen allowSubmission andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val viewModel = GenericViewModel(
          submitUrl = routes.DeclarationController.onSubmit(srn, startDate).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName
        )

        renderer.render(template = "declaration.njk", Json.obj(fields = "viewModel" -> viewModel)).map(Ok(_))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate)
      andThen allowSubmission andThen requireData).async { implicit request =>
      DataRetrievals.retrievePSTR { pstr =>
        for {
          answersWithDeclaration <- Future.fromTry(
            request.userAnswers.set(DeclarationPage, Declaration("PSA", request.psaId.id, hasAgreed = true)))
          updatedStatus <- Future.fromTry(answersWithDeclaration.set(AFTStatusQuery, value = "Submitted"))
          _ <- userAnswersCacheConnector.save(request.internalId, updatedStatus.data)
          _ <- aftService.fileAFTReturn(pstr, updatedStatus)
        } yield {
          Redirect(navigator.nextPage(DeclarationPage, NormalMode, request.userAnswers, srn, startDate))
        }
      }
    }
}
