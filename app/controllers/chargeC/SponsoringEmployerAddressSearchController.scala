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

package controllers.chargeC

import java.time.LocalDate

import audit.{AuditService, AddressLookupAuditEvent}
import config.FrontendAppConfig
import connectors.AddressLookupConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.SponsoringEmployerAddressSearchFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.{Mode, GenericViewModel, AccessType, Index}
import navigators.CompoundNavigator
import pages.chargeC.SponsoringEmployerAddressSearchPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{AnyContent, MessagesControllerComponents, Action}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{Future, ExecutionContext}

class SponsoringEmployerAddressSearchController @Inject()(override val messagesApi: MessagesApi,
                                                          userAnswersCacheConnector: UserAnswersCacheConnector,
                                                          navigator: CompoundNavigator,
                                                          identify: IdentifierAction,
                                                          getData: DataRetrievalAction,
                                                          allowAccess: AllowAccessActionProvider,
                                                          requireData: DataRequiredAction,
                                                          formProvider: SponsoringEmployerAddressSearchFormProvider,
                                                          addressLookupConnector: AddressLookupConnector,
                                                          auditService:AuditService,
                                                          val controllerComponents: MessagesControllerComponents,
                                                          config: FrontendAppConfig,
                                                          renderer: Renderer
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>

        val viewModel = GenericViewModel(
          submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, accessType, version, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName)

        val json = Json.obj(
          "form" -> form,
          "viewModel" -> viewModel,
          "sponsorName" -> sponsorName,
          "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url
        )

        renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(Ok(_))
      }
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
    implicit request =>
      DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>
        form.bindFromRequest().fold(
          formWithErrors => {
            val viewModel = GenericViewModel(
              submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, accessType, version, index).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName)

            val json = Json.obj(
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "sponsorName" -> sponsorName,
              "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url
            )

            renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(BadRequest(_))
          },
          value =>
            addressLookupConnector.addressLookupByPostCode(value).flatMap {
              case Nil =>
                val viewModel = GenericViewModel(
                submitUrl = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, accessType, version, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName)

                val json = Json.obj(
                  "form" -> formWithError("chargeC.employerAddressSearch.error.invalid"),
                  "viewModel" -> viewModel,
                  "sponsorName" -> sponsorName,
                  "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url
                )

                renderer.render("chargeC/sponsoringEmployerAddressSearch.njk", json).map(BadRequest(_))

              case addresses =>
                auditService.sendEvent(AddressLookupAuditEvent(value))
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(SponsoringEmployerAddressSearchPage(index), addresses))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(SponsoringEmployerAddressSearchPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
        )

      }
  }

  protected def formWithError(message: String): Form[String] = {
    form.withError("value", message)
  }
}
