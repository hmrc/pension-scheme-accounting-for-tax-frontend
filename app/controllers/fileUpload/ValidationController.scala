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
import connectors.UpscanInitiateConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import fileUploadParsers.{AnnualAllowanceParser, LifetimeAllowanceParser, Parser, ValidationResult}
import models.{AccessType, Index, UploadId, UploadedSuccessfully}
import navigators.CompoundNavigator
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ValidationController @Inject()(
                                      override val messagesApi: MessagesApi,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      val controllerComponents: MessagesControllerComponents,
                                      renderer: Renderer,
                                      navigator: CompoundNavigator,
                                      upscanInitiateConnector: UpscanInitiateConnector,
                                      uploadProgressTracker: UploadProgressTracker,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      annualAllowanceParser: AnnualAllowanceParser,
                                      lifeTimeAllowanceParser: LifetimeAllowanceParser
                                    )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: String, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        val fileContent = uploadDetails(uploadId)
          .flatMap(ud => upscanInitiateConnector.download(ud.downloadUrl))
          .map(_.body.split("\n").toList)
        fileContent.flatMap { linesFromCSV =>
          parser(chargeType) match {
            case None => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            case Some(parser) => parser.parse(request.userAnswers, linesFromCSV) match {
              case ValidationResult(ua, Nil) =>
                userAnswersCacheConnector.save(request.internalId, ua.data).flatMap { _ =>
                  Future.successful(Redirect(controllers.chargeE.routes.CheckYourAnswersController
                    .onClick(srn, startDate.toString, accessType, version, Index(1))))
                }
              case ValidationResult(_, errors) =>
                renderer.render(template = "fileUpload/invalid.njk",
                  Json.obj(
                    "chargeType" -> chargeType,
                    "chargeTypeText" -> chargeType.replace("-", " "),
                    "srn" -> srn, "startDate" -> Some(startDate),
                    "viewModel" -> errors))
                  .map(Ok(_))
            }
          }
        }

    }

  private def uploadDetails(uploadId: UploadId) = {
    for (uploadResult <- uploadProgressTracker.getUploadResult(uploadId))
      yield {
        uploadResult match {
          case Some(s: UploadedSuccessfully) => s // TODO: Validation
          case _ => ???
        }
      }
  }

  private def parser(chargeType: String): Option[Parser] = {
    chargeType match {
      case "annual-allowance-charge" => Some(annualAllowanceParser)
      case "lifetime-allowance-charge" => Some(lifeTimeAllowanceParser)
      case _ => None
    }
  }
}
