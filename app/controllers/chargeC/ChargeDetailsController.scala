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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.ChargeDetailsFormProvider
import javax.inject.Inject
import models.chargeC.ChargeCDetails
import models.{GenericViewModel, Index, Mode}
import navigators.CompoundNavigator
import pages.chargeC.ChargeCDetailsPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{DateInput, NunjucksSupport}

import scala.concurrent.{ExecutionContext, Future}

class ChargeDetailsController @Inject()(override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: ChargeDetailsFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      renderer: Renderer
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  val form: Form[ChargeCDetails] = formProvider()

  def onPageLoad(mode: Mode, srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen allowAccess(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>
        val preparedForm = request.userAnswers.get(ChargeCDetailsPage(index)) match {
          case Some(value) => form.fill(value)
          case None => form
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, index).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val json = Json.obj(
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "date" -> DateInput.localDate(preparedForm("paymentDate")),
          "sponsorName" -> sponsorName
        )

        renderer.render("chargeC/chargeDetails.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String, index: Index): Action[AnyContent] = (identify andThen getData andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>
        form.bindFromRequest().fold(
          formWithErrors => {

            val viewModel = GenericViewModel(
              submitUrl = routes.ChargeDetailsController.onSubmit(mode, srn, index).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "date" -> DateInput.localDate(formWithErrors("paymentDate")),
              "sponsorName" -> sponsorName
            )

            renderer.render("chargeC/chargeDetails.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(ChargeCDetailsPage(index), value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(ChargeCDetailsPage(index), mode, updatedAnswers, srn))
        )
      }
  }
}
