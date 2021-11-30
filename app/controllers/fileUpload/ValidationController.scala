/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.DataRetrievals
import controllers.actions._
import fileUploadParsers.{AnnualAllowanceParser, LifetimeAllowanceParser}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.{AFTQuarter, AccessType, Index, MemberDetails, UploadId, UploadedSuccessfully, UserAnswers, YearRange}
import navigators.CompoundNavigator
import pages.QuarterPage
import pages.chargeE._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.{AFTService, UserAnswersService}
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.http.HttpClient
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
    httpClient: HttpClient,
    aftService: AFTService,
    userAnswersCacheConnector: UserAnswersCacheConnector,
    userAnswersService: UserAnswersService,
)(implicit ec: ExecutionContext, appConfig: FrontendAppConfig)
  extends FrontendBaseController
    with I18nSupport with NunjucksSupport {

  def onPageLoad(srn: String, startDate: LocalDate, accessType: AccessType, version: Int, chargeType: String, uploadId: UploadId): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {

      implicit request =>

        DataRetrievals.retrievePSTR { pstr =>
          val l = for {
            ud <- uploadDetails(uploadId)
            contents <- upscanInitiateConnector.download(ud.downloadUrl)
          } yield {
            contents.body.split("\n").toList
          }

          val foo = l.map { lines => {

            chargeType match {
              case "annual-allowance-charge" =>
                val updatedUserAnswers = AnnualAllowanceParser.parse(request.userAnswers, lines)

                updatedUserAnswers.fold(
                  x => {
                    renderer.render(template = "fileUpload/invalid.njk",
                      Json.obj(
                        "chargeType" -> chargeType,
                        "chargeTypeText" -> chargeType.replace("-", " "),
                        "srn" -> srn, "startDate" -> Some(startDate),
                        "viewModel" -> x))
                      .map(Ok(_))
                  },
                  y => {
                    userAnswersCacheConnector.save(request.internalId, y.data)
                    Future.successful(Redirect(controllers.chargeE.routes.CheckYourAnswersController.onClick(
                      srn, startDate.toString, accessType, version, Index(1))))
                  }
                )

              case "lifetime-allowance-charge" =>
                val updatedUserAnswers = LifetimeAllowanceParser.parse(request.userAnswers, lines)
                userAnswersCacheConnector.save(request.internalId, updatedUserAnswers.data)
                Future.successful(Redirect(controllers.chargeD.routes.CheckYourAnswersController.onClick(
                  srn, startDate.toString, accessType, version, Index(1))))
              case _ => ???
            }}
          }

          foo.flatMap(a => a)
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
}
