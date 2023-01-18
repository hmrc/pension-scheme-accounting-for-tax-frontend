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

import controllers.actions._
import controllers.fileUpload.WhatYouWillNeedController.FileTypes
import controllers.fileUpload.WhatYouWillNeedController.FileTypes.Instructions
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.LocalDateBinder._
import models.{AccessType, ChargeType, Enumerable, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.{IsPublicServicePensionsRemedyPage, SchemeNameQuery}
import pages.fileUpload.WhatYouWillNeedPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class WhatYouWillNeedController @Inject()(
    override val messagesApi: MessagesApi,
    identify: IdentifierAction,
    getData: DataRetrievalAction,
    allowAccess: AllowAccessActionProvider,
    requireData: DataRequiredAction,
    val controllerComponents: MessagesControllerComponents,
    renderer: Renderer,
    navigator: CompoundNavigator
)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport {

  // scalastyle:off
  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val ua = request.userAnswers

      val psr = chargeType match {
        case ChargeTypeLifetimeAllowance | ChargeTypeAnnualAllowance => ua.get(IsPublicServicePensionsRemedyPage(chargeType, optIndex = None))
        case _ => None
      }

      // TODO: rename val to perhaps separate out links by more appropriate name convention rather than _1, _2
      val downloadLinks: (Json.JsValueWrapper, Json.JsValueWrapper) = (psr, chargeType) match {
          case (Some(true), ChargeTypeAnnualAllowance) => // PSR, AA
            (controllers.routes.FileDownloadController.templateFile(ChargeTypeAnnualAllowance, Some(true)).url,
              controllers.routes.FileDownloadController.instructionsFile(ChargeTypeAnnualAllowance, Some(true)).url)
          case (Some(true), ChargeTypeLifetimeAllowance) => // PSR, LTA
            (controllers.routes.FileDownloadController.templateFile(ChargeTypeLifetimeAllowance, Some(true)).url,
              controllers.routes.FileDownloadController.instructionsFile(ChargeTypeLifetimeAllowance, Some(true)).url)
          case (Some(false), ChargeTypeAnnualAllowance) => // NON PSR, AA
            (controllers.routes.FileDownloadController.templateFile(ChargeTypeAnnualAllowance, Some(false)).url,
              controllers.routes.FileDownloadController.instructionsFile(ChargeTypeAnnualAllowance, Some(false)).url)
          case (Some(false), ChargeTypeLifetimeAllowance) => // NON PSR, LTA
            (controllers.routes.FileDownloadController.templateFile(ChargeTypeLifetimeAllowance, Some(false)).url,
              controllers.routes.FileDownloadController.instructionsFile(ChargeTypeLifetimeAllowance, Some(false)).url)
          case (None, _) => // OVERSEAS TRANSFER
            (controllers.routes.FileDownloadController.templateFile(_, None).url,
              controllers.routes.FileDownloadController.instructionsFile(_, None).url)
        }

      val viewModel = GenericViewModel(
        submitUrl = navigator.nextPage(WhatYouWillNeedPage(chargeType), NormalMode, ua, srn, startDate, accessType, version).url,
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
        schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")
      )

      renderer.render(template = "fileUpload/whatYouWillNeed.njk",
        Json.obj(
          "chargeType" -> chargeType.toString,
          "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
          "srn" -> srn, "startDate" -> Some(startDate),
          "fileDownloadTemplateLink" -> downloadLinks._1,
          "fileDownloadInstructionsLink" -> downloadLinks._2,
          "viewModel" -> viewModel,
          "psr" -> psr))
        .map(Ok(_))
    }
}