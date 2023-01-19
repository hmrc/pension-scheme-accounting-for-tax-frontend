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
import controllers.DataRetrievals
import controllers.actions.{DataRequiredAction, DataRetrievalAction, IdentifierAction}
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.{AccessType, ChargeType, GenericViewModel}
import pages.IsPublicServicePensionsRemedyPage
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class UpscanErrorController @Inject()(
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      requireData: DataRequiredAction,
                                      renderer: Renderer
                                      )(implicit ec: ExecutionContext)
                                      extends FrontendBaseController
                                      with I18nSupport {


  def quarantineError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] = Action.async { implicit request =>
    val json = Json.obj(
      "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url)
    renderer.render("fileUpload/error/quarantine.njk", json).map(Ok(_))
  }

  def rejectedError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] = Action.async { implicit request =>
    val json = Json.obj(
      "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url)
    renderer.render("fileUpload/error/rejected.njk", json).map(Ok(_))
  }

  def unknownError(srn: String, startDate: String, accessType: AccessType, version: Int): Action[AnyContent] = Action.async { implicit request =>
    val json = Json.obj(
      "returnUrl" -> controllers.routes.ChargeTypeController.onPageLoad(srn, startDate, accessType, version).url)
    renderer.render("fileUpload/error/unknown.njk", json).map(Ok(_))
  }

  def invalidHeaderOrBodyError(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val psr = chargeType match {
          case ChargeTypeLifetimeAllowance | ChargeTypeAnnualAllowance =>
            request.userAnswers.get(IsPublicServicePensionsRemedyPage(chargeType, optIndex = None))
          case _ => None
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.FileUploadController.onPageLoad(srn, startDate.toString, accessType, version, chargeType).url,
          returnUrl = config.schemeDashboardUrl(request).format(srn),
          schemeName = schemeName
        )
        val json = Json.obj(
          "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
          "fileTemplateLink" -> controllers.routes.FileDownloadController.templateFile(chargeType, psr).url,
          "fileDownloadInstructionsLink" -> controllers.routes.FileDownloadController.instructionsFile(chargeType, psr).url,
          "viewModel" -> viewModel
        )
        renderer.render("fileUpload/error/invalidHeaderOrBody.njk", json).map(Ok(_))
      }
    }

}
