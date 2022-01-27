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
import fileUploadParsers._
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance}
import models.requests.DataRequest
import models.{AccessType, ChargeType, Failed, InProgress, NormalMode, UploadId, UploadedSuccessfully}
import navigators.CompoundNavigator
import pages.fileUpload.ValidationPage
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport
import utils.ValidationHelper

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
                                      lifeTimeAllowanceParser: LifetimeAllowanceParser,
                                      validationHelper: ValidationHelper
                                    )(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport with NunjucksSupport {

  private def parseAndRenderResult(
                                    srn: String,
                                    startDate: LocalDate,
                                    accessType: AccessType,
                                    version: Int,
                                    chargeType: ChargeType,
                                    linesFromCSV: List[String], parser: Parser)(implicit request: DataRequest[AnyContent]): Future[Result] = {

    //removes non-printable characters like ^M$
    val filteredLinesFromCSV = linesFromCSV.map(lines => lines.replaceAll("\\p{C}", ""))
    val result = if (validationHelper.isHeaderValid(filteredLinesFromCSV.head, chargeType: ChargeType)) {
      parser.parse(startDate, filteredLinesFromCSV.tail)
    } else {
      Right(Seq(ParserValidationErrors(0, Seq("Header invalid"))))
    }
    result match {
      case Left(commitItems) =>
        val updatedUA = commitItems.foldLeft(request.userAnswers) { (acc, ci) =>
          acc.setOrException(ci.jsPath, ci.value)
        }
        userAnswersCacheConnector.save(request.internalId, updatedUA.data)
          .map(_ => Redirect(navigator.nextPage(ValidationPage(chargeType), NormalMode, updatedUA, srn, startDate, accessType, version)))
      case Right(errors) =>
        renderer.render(template = "fileUpload/invalid.njk",
          Json.obj(
            "chargeType" -> chargeType,
            "chargeTypeText" -> chargeType.toString,
            "srn" -> srn, "startDate" -> Some(startDate),
            "viewModel" -> errors))
          .map(Ok(_))
    }
  }

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: ChargeType, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        val parser = findParser(chargeType)
        uploadProgressTracker.getUploadResult(uploadId).flatMap { uploadStatus =>
          (parser, uploadStatus) match {
            case (Some(_), None | Some(Failed) | Some(InProgress)) => sessionExpired
            case (None, _) => sessionExpired
            case (Some(parser), Some(ud: UploadedSuccessfully)) =>
              upscanInitiateConnector.download(ud.downloadUrl)
                .map(_.body.split("\n").toList)
                .flatMap(linesFromCSV => parseAndRenderResult(srn, startDate, accessType, version, chargeType, linesFromCSV, parser))
          }
        }
    }

  private def sessionExpired: Future[Result] = Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))

  private def findParser(chargeType: ChargeType): Option[Parser] = {
    chargeType match {
      case ChargeTypeAnnualAllowance => Some(annualAllowanceParser)
      case ChargeTypeLifetimeAllowance => Some(lifeTimeAllowanceParser)
      case _ => None
    }
  }
}
