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
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.ValidationErrorsMoreThanOrEqualToMax
import models.{AccessType, ChargeType}
import pages.SchemeNameQuery
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.GenericErrorsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ValidationErrorsSummaryController @Inject()(appConfig: FrontendAppConfig,
                                                  override val messagesApi: MessagesApi,
                                                  val controllerComponents: MessagesControllerComponents,
                                                  identify: IdentifierAction,
                                                  getData: DataRetrievalAction,
                                                  allowAccess: AllowAccessActionProvider,
                                                  requireData: DataRequiredAction,
                                                  fileUploadOutcomeConnector: FileUploadOutcomeConnector,
                                                  view: GenericErrorsView
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
        val returnToSchemeDetails = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate.toString, accessType, version).url
        fileUploadOutcomeConnector.getOutcome.flatMap {
          case Some(outcome@FileUploadOutcome(ValidationErrorsMoreThanOrEqualToMax, _, _)) =>
            val (errors, totalNumOfErrors) = generateAllErrors(outcome)
          Future.successful(Ok(view(schemeName,ChargeType.fileUploadText(chargeType), fileDownloadInstructionLink,
            totalNumOfErrors, errors, returnToFileUpload, returnToSchemeDetails)))
          case _ => Future.successful(NotFound)
        }
    }

  private def generateAllErrors(fileUploadOutcome: FileUploadOutcome): (Seq[String], Int) = {
    val numOfErrorsReads = (JsPath \ "totalError").read[Int]
    val readsErrors = (JsPath \ "errors").read[Seq[String]](JsPath.read[Seq[String]](__.read(Reads.seq[String])))

    val numberOfErrors = numOfErrorsReads.reads(fileUploadOutcome.json) match {
      case JsSuccess(total, _) => total
      case JsError(errors) => throw JsResultException(errors)
    }

    val errors = readsErrors.reads(fileUploadOutcome.json) match {
      case JsSuccess(value, _) => value
      case JsError(errors) => throw JsResultException(errors)
    }
    (errors, numberOfErrors)
  }
}
