/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.fileUpload

import config.FrontendAppConfig
import connectors.cache.FileUploadOutcomeConnector
import controllers.actions._
import helpers.ChargeTypeHelper
import models.fileUpload.FileUploadOutcomeStatus.{GeneralError, SessionExpired, Success, UpscanInvalidHeaderOrBody, UpscanUnknownError, ValidationErrorsLessThanMax, ValidationErrorsMoreThanOrEqualToMax}
import models.LocalDateBinder._
import models.fileUpload.FileUploadOutcome
import models.{AccessType, ChargeType, NormalMode}
import navigators.CompoundNavigator
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class ProcessingRequestController @Inject()(val appConfig: FrontendAppConfig,
                                            override val messagesApi: MessagesApi,
                                            identify: IdentifierAction,
                                            getData: DataRetrievalAction,
                                            allowAccess: AllowAccessActionProvider,
                                            requireData: DataRequiredAction,
                                            val controllerComponents: MessagesControllerComponents,
                                            renderer: Renderer,
                                            fileUploadOutcomeConnector: FileUploadOutcomeConnector,
                                            navigator: CompoundNavigator
                                           )(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] = {
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        def headerContentAndRedirect(optionOutcome: Option[FileUploadOutcome]): (String, String, String) = {
          optionOutcome match {
            case None =>
              Tuple3(
                "messages__processingRequest__h1_processing",
                "messages__processingRequest__content_processing",
                controllers.fileUpload.routes.ProcessingRequestController.onPageLoad(srn, startDate, accessType, version, chargeType).url
              )
            case Some(FileUploadOutcome(Success, _, Some(fileName))) =>
              val url = navigator.nextPage(ChargeTypeHelper.getCheckYourAnswersPage(chargeType), NormalMode, request.userAnswers, srn,
                startDate, accessType, version).url
              Tuple3(
                "messages__processingRequest__h1_processed",
                Messages("messages__processingRequest__content_processed", fileName),
                url
              )
            case Some(FileUploadOutcome(Success, _, None)) =>
              Tuple3(
                "messages__processingRequest__h1_processed",
                Messages("messages__processingRequest__content_processed", Messages("messages__theFile")),
                controllers.routes.ConfirmationController.onPageLoad(srn, startDate, accessType, version).url
              )
            case Some(FileUploadOutcome(UpscanInvalidHeaderOrBody, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                routes.UpscanErrorController.invalidHeaderOrBodyError(srn, startDate.toString, accessType, version, chargeType).url
              )
            case Some(FileUploadOutcome(UpscanUnknownError, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                routes.UpscanErrorController.unknownError(srn, startDate.toString, accessType, version).url
              )
            case Some(FileUploadOutcome(SessionExpired, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                controllers.routes.SessionExpiredController.onPageLoad.url
              )
            case Some(FileUploadOutcome(ValidationErrorsLessThanMax, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                controllers.fileUpload.routes.ValidationErrorsAllController.onPageLoad(srn, startDate, accessType, version, chargeType).url
              )
            case Some(FileUploadOutcome(ValidationErrorsMoreThanOrEqualToMax, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                controllers.fileUpload.routes.ValidationErrorsSummaryController.onPageLoad(srn, startDate, accessType, version, chargeType).url
              )
            case Some(FileUploadOutcome(GeneralError, _, _)) =>
              Tuple3(
                "messages__processingRequest__h1_failure",
                "messages__processingRequest__content_failure",
                controllers.fileUpload.routes.ProblemWithServiceController.onPageLoad(srn, startDate, accessType, version).url
              )
            case Some(outcome) => throw new RuntimeException(s"Unknown outcome: $outcome")
          }
        }

        fileUploadOutcomeConnector.getOutcome.flatMap { optionOutcome =>
          val (header, content, redirect) = headerContentAndRedirect(optionOutcome)
          val json = Json.obj(
            "pageTitle" -> header,
            "heading" -> header,
            "content" -> content,
            "continueUrl" -> redirect
          )
          renderer.render("fileUpload/processingRequest.njk", json).map(Ok(_))
        }
    }
  }

}

