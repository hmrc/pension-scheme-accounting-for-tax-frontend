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

package controllers.fileUpload

import config.FrontendAppConfig
import connectors.cache.FileUploadOutcomeConnector
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.fileUpload.FileUploadOutcomeStatus.ValidationErrorsLessThanMax
import models.fileUpload.{FileUploadOutcome, ValidationErrorForRendering}
import models.{AccessType, ChargeType}
import pages.SchemeNameQuery
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsError, JsResultException, JsSuccess}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.InvalidView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ValidationErrorsAllController @Inject()(appConfig: FrontendAppConfig,
                                              override val messagesApi: MessagesApi,
                                              val controllerComponents: MessagesControllerComponents,
                                              identify: IdentifierAction,
                                              getData: DataRetrievalAction,
                                              allowAccess: AllowAccessActionProvider,
                                              requireData: DataRequiredAction,
                                              fileUploadOutcomeConnector: FileUploadOutcomeConnector,
                                              view: InvalidView
                                             )(implicit val executionContext: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>

        val schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme")
        val fileDownloadInstructionLink =
          controllers.routes.FileDownloadController.instructionsFile(chargeType, request.userAnswers.isPublicServicePensionsRemedy(chargeType)).url
        val returnToFileUpload = appConfig.failureEndpointTarget(srn, startDate, accessType, version, chargeType)
        val returnUrl= controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate.toString, accessType, version).url

        fileUploadOutcomeConnector.getOutcome.flatMap {
          case Some(outcome@FileUploadOutcome(ValidationErrorsLessThanMax, _, _)) =>
            outcome.json.validate[Seq[ValidationErrorForRendering]] match {
              case JsSuccess(value, _) => Future.successful(Ok(view(ChargeType.fileUploadText(chargeType), schemeName, returnUrl,
                returnToFileUpload, fileDownloadInstructionLink, value, fileDownloadInstructionLink)))
              case JsError(errors) => throw JsResultException(errors)
            }
          case _ => Future.successful(NotFound)
        }
    }
}
