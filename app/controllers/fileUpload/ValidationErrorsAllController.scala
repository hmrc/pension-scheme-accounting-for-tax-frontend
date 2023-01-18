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

package controllers.fileUpload

import config.FrontendAppConfig
import connectors.cache.FileUploadOutcomeConnector
import controllers.actions.{AllowAccessActionProvider, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.fileUpload.FileUploadOutcome
import models.fileUpload.FileUploadOutcomeStatus.ValidationErrorsLessThanMax
import models.{AccessType, ChargeType}
import pages.{IsPublicServicePensionsRemedyPage, SchemeNameQuery}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

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
                                              renderer: Renderer,
                                              fileUploadOutcomeConnector: FileUploadOutcomeConnector
                                             )(implicit val executionContext: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>

        val psr = chargeType match {
          case ChargeTypeLifetimeAllowance | ChargeTypeAnnualAllowance =>
            request.userAnswers.get(IsPublicServicePensionsRemedyPage(chargeType, optIndex = None))
          case _ => None
        }

        val schemeName = request.userAnswers.get(SchemeNameQuery).getOrElse("the scheme")
        val fileDownloadInstructionLink = controllers.routes.FileDownloadController.instructionsFile(chargeType, psr).url
        val returnToFileUpload = appConfig.failureEndpointTarget(srn, startDate, accessType, version, chargeType)
        val returnToSchemeDetails = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate.toString, accessType, version).url

        fileUploadOutcomeConnector.getOutcome.flatMap {
          case Some(FileUploadOutcome(ValidationErrorsLessThanMax, errorsJson, _)) =>
            renderer.render(template = "fileUpload/invalid.njk",
              Json.obj(
                "chargeType" -> chargeType,
                "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
                "srn" -> srn,
                "fileDownloadInstructionsLink" -> fileDownloadInstructionLink,
                "returnToFileUploadURL" -> returnToFileUpload,
                "returnToSchemeDetails" -> returnToSchemeDetails,
                "schemeName" -> schemeName
              ) ++ errorsJson
            ).map(Ok(_))
          case _ => Future.successful(NotFound)
        }
    }
}
