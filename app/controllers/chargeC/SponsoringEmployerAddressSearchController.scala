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

import forms.chargeC.SponsoringEmployerAddressSearchFormProvider
import pages.chargeC.{SponsoringEmployerAddressSearchPage, WhichTypeOfSponsoringEmployerPage}
import config.FrontendAppConfig
import connectors.AddressLookupConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import javax.inject.Inject
import models.LocalDateBinder._
import models.{GenericViewModel, Index, Mode, TolerantAddress, UserAnswers}
import models.requests.DataRequest
import navigators.CompoundNavigator
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SponsoringEmployerAddressSearchController @Inject()(override val messagesApi: MessagesApi,
                                                          userAnswersCacheConnector: UserAnswersCacheConnector,
                                                          navigator: CompoundNavigator,
                                                          identify: IdentifierAction,
                                                          getData: DataRetrievalAction,
                                                          allowAccess: AllowAccessActionProvider,
                                                          requireData: DataRequiredAction,
                                                          formProvider: SponsoringEmployerAddressSearchFormProvider,
                                                          addressLookupConnector: AddressLookupConnector,
                                                          val controllerComponents: MessagesControllerComponents,
                                                          config: FrontendAppConfig,
                                                          renderer: Renderer
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>

        val viewModel = GenericViewModel(
          submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, index).url,
          returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
          schemeName = schemeName)

        val json = Json.obj(
          "form" -> form,
          "viewModel" -> viewModel,
          "sponsorName" -> sponsorName,
          "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, index).url
        )

        renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] = (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>
        form.bindFromRequest().fold(
          formWithErrors => {
            val viewModel = GenericViewModel(
              submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, index).url,
              returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
              schemeName = schemeName)

            val json = Json.obj(
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "sponsorName" -> sponsorName,
              "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, index).url
            )

            renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(BadRequest(_))
          },
          value =>
            addressLookupConnector.addressLookupByPostCode(value).flatMap {
              case Nil =>
                val viewModel = GenericViewModel(
                submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, index).url,
                returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
                schemeName = schemeName)

                val json = Json.obj(
                  "form" -> formWithError("chargeC.employerAddressSearch.error.invalid"),
                  "viewModel" -> viewModel,
                  "sponsorName" -> sponsorName,
                  "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, index).url
                )

                renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(BadRequest(_))

              case addresses =>
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(SponsoringEmployerAddressSearchPage(index), addresses))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(SponsoringEmployerAddressSearchPage(index), mode, updatedAnswers, srn, startDate))
            }
        )

      }
  }

  protected def formWithError(message: String)(implicit request: DataRequest[AnyContent]): Form[String] = {
    form.withError("value", message)
  }
}
