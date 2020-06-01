/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.chargeE

import java.time.LocalDate

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.requests.DataRequest
import models.{GenericViewModel, NormalMode, Quarter}
import navigators.CompoundNavigator
import pages.chargeE.AddMembersPage
import pages.{QuarterPage, SchemeNameQuery, ViewOnlyAccessiblePage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.ChargeEService
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}
import utils.DateHelper.dateFormatterDMY

import scala.concurrent.{ExecutionContext, Future}

class AddMembersController @Inject()(override val messagesApi: MessagesApi,
                                     userAnswersCacheConnector: UserAnswersCacheConnector,
                                     navigator: CompoundNavigator,
                                     identify: IdentifierAction,
                                     getData: DataRetrievalAction,
                                     allowAccess: AllowAccessActionProvider,
                                     requireData: DataRequiredAction,
                                     formProvider: AddMembersFormProvider,
                                     val controllerComponents: MessagesControllerComponents,
                                     chargeEHelper: ChargeEService,
                                     config: FrontendAppConfig,
                                     renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  def form: Form[Boolean] = formProvider("chargeE.addMembers.error")

  def onPageLoad(srn: String, startDate: LocalDate): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, Some(ViewOnlyAccessiblePage))).async { implicit request =>
      (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
        case (Some(schemeName), Some(quarter)) =>
          renderer.render(template = "chargeE/addMembers.njk", getJson(srn, startDate, form, schemeName, quarter)).map(Ok(_))

        case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
    }

  def onSubmit(srn: String, startDate: LocalDate): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
              case (Some(schemeName), Some(quarter)) =>
                renderer.render(template = "chargeE/addMembers.njk", getJson(srn, startDate, formWithErrors, schemeName, quarter)).map(BadRequest(_))

              case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddMembersPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AddMembersPage, NormalMode, updatedAnswers, srn, startDate))
          }
        )
  }

  private def getJson(srn: String, startDate: LocalDate, form: Form[_], schemeName: String, quarter: Quarter)(
      implicit request: DataRequest[AnyContent]): JsObject = {

    val viewModel = GenericViewModel(submitUrl = routes.AddMembersController.onSubmit(srn, startDate).url,
                                     returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate).url,
                                     schemeName = schemeName)

    val members = chargeEHelper.getAnnualAllowanceMembers(request.userAnswers, srn, startDate)

    Json.obj(
      "srn" -> srn,
      "startDate" -> Some(startDate),
      "form" -> form,
      "viewModel" -> viewModel,
      "radios" -> Radios.yesNo(form("value")),
      "quarterStart" -> quarter.startDate.format(dateFormatterDMY),
      "quarterEnd" -> quarter.endDate.format(dateFormatterDMY),
      "table" -> Json.toJson(chargeEHelper.mapToTable(members, !request.isViewOnly)),
      "canChange" -> !request.isViewOnly
    )

  }

}
