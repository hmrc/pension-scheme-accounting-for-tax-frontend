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

package controllers.chargeC

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.AddMembersFormProvider
import javax.inject.Inject
import models.requests.DataRequest
import models.{GenericViewModel, NormalMode, Quarter}
import navigators.CompoundNavigator
import pages.chargeC.AddEmployersPage
import pages.{QuarterPage, SchemeNameQuery}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.ChargeCService.{getSponsoringEmployers, mapToTable}
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import scala.concurrent.{ExecutionContext, Future}

class AddEmployersController @Inject()(override val messagesApi: MessagesApi,
                                       userAnswersCacheConnector: UserAnswersCacheConnector,
                                       navigator: CompoundNavigator,
                                       identify: IdentifierAction,
                                       getData: DataRetrievalAction,
                                       requireData: DataRequiredAction,
                                       formProvider: AddMembersFormProvider,
                                       val controllerComponents: MessagesControllerComponents,
                                       config: FrontendAppConfig,
                                       renderer: Renderer
                                       )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  def form: Form[Boolean] = formProvider("chargeC.addEmployers.error")

  private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
  def getFormattedDate(s: String): String = LocalDate.from(DateTimeFormatter.ofPattern("yyyy-MM-dd").parse(s)).format(dateFormatter)

  def onPageLoad(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
        case (Some(schemeName), Some(quarter)) =>

          renderer.render(template = "chargeC/addEmployers.njk",
            getJson(srn, form, schemeName, quarter)).map(Ok(_))

        case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
      }
  }

  def onSubmit(srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
        form.bindFromRequest().fold(
          formWithErrors => {
            (request.userAnswers.get(SchemeNameQuery), request.userAnswers.get(QuarterPage)) match {
              case (Some(schemeName), Some(quarter)) =>

                renderer.render(
                  template = "chargeC/addEmployers.njk",
                  getJson(srn, formWithErrors, schemeName, quarter)).map(BadRequest(_))

              case _ => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad()))
            }
          },
          value => {
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(AddEmployersPage, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AddEmployersPage, NormalMode, updatedAnswers, srn))
          }
        )
      }

  private def getJson(srn: String, form: Form[_], schemeName: String, quarter: Quarter
                     )(implicit request: DataRequest[AnyContent]): JsObject = {

        val viewModel = GenericViewModel(
          submitUrl = routes.AddEmployersController.onSubmit(srn).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val members = getSponsoringEmployers(request.userAnswers, srn)

        Json.obj(
          "srn" -> srn,
          "form" -> form,
          "viewModel" -> viewModel,
          "radios" -> Radios.yesNo(form("value")),
          "quarterStart" -> getFormattedDate(quarter.startDate),
          "quarterEnd" -> getFormattedDate(quarter.endDate),
          "table" -> Json.toJson(mapToTable(members, !request.viewOnly)),
          "canChange" -> !request.viewOnly
        )


    }

}
