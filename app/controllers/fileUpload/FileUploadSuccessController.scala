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

import controllers.actions._
import helpers.ChargeTypeHelper
import models.LocalDateBinder._
import models.{AccessType, ChargeType, GenericViewModel, NormalMode}
import navigators.CompoundNavigator
import pages.SchemeNameQuery
import pages.fileUpload.UploadedFileName
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class FileUploadSuccessController @Inject()(
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

  def onPageLoad(srn: String, startDate: String, accessType: AccessType, version: Int, chargeType: ChargeType): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      val ua = request.userAnswers

      val viewModel = GenericViewModel(
        submitUrl = navigator.nextPage(ChargeTypeHelper.getCheckYourAnswersPage(chargeType), NormalMode, ua, srn,
          startDate, accessType, version).url,
        returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
        schemeName = ua.get(SchemeNameQuery).getOrElse("the scheme")
      )

      renderer.render(template = "fileUpload/fileUploadSuccess.njk",
        Json.obj( "fileName" -> ua.get(UploadedFileName(chargeType).path),
          "chargeTypeText" -> chargeType.toString,
          "viewModel" -> viewModel))
        .map(Ok(_))
    }
}

