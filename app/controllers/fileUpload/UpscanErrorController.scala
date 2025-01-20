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
import controllers.DataRetrievals
import controllers.actions.{AllowAccessActionProviderForIdentifierRequest, DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.{AccessType, ChargeType}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.fileUpload.error.{InvalidHeaderOrBodyView, QuarantineView, RejectedView, UnknownView}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanErrorController @Inject()(
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       requireData: DataRequiredAction,
                                       allowAccess: AllowAccessActionProviderForIdentifierRequest,
                                       invalidHeaderOrBodyView: InvalidHeaderOrBodyView,
                                       quarantineView: QuarantineView,
                                       rejectedView: RejectedView,
                                       unknownView: UnknownView
                                      )(implicit ec: ExecutionContext)
                                      extends FrontendBaseController
                                      with I18nSupport {


  def quarantineError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))) { implicit request =>
      Ok(quarantineView(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url))
  }

  def rejectedError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))) { implicit request =>
      Ok(rejectedView(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url))
  }

  def unknownError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn))) { implicit request =>
     Ok(unknownView(controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url))
  }

  def invalidHeaderOrBodyError(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen allowAccess(Some(srn)) andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val submitUrl = routes.FileUploadController.onPageLoad(srn, startDate.toString, accessType, version, chargeType).url
        val returnUrl = config.schemeDashboardUrl(request).format(srn)
        val isPsr = request.userAnswers.isPublicServicePensionsRemedy(chargeType)
        val fileTemplateLink = controllers.routes.FileDownloadController.templateFile(chargeType, isPsr).url
        val fileDownloadInstructionsLink = controllers.routes.FileDownloadController.instructionsFile(chargeType, isPsr).url
        Future.successful(Ok(invalidHeaderOrBodyView(ChargeType.fileUploadText(chargeType), schemeName, submitUrl,
          returnUrl, fileTemplateLink, fileDownloadInstructionsLink)))
      }
    }

}
