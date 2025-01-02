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

package controllers.chargeC

import audit.{AddressLookupAuditEvent, AuditService}
import config.FrontendAppConfig
import connectors.AddressLookupConnector
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.SponsoringEmployerAddressSearchFormProvider
import models.LocalDateBinder._
import models.{AccessType, Index, Mode}
import navigators.CompoundNavigator
import pages.chargeC.SponsoringEmployerAddressSearchPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.chargeC.SponsoringEmployerAddressSearchView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

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
                                                          view: SponsoringEmployerAddressSearchView
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
    implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, sponsorName, employerType) =>

        val submitCall = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, accessType, version, index)
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
        val empType = Messages(s"chargeC.employerType.${employerType.toString}")
        val enterManuallyUrl = routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url

        Future.successful(Ok(view(form, schemeName, submitCall, returnUrl, sponsorName, empType, enterManuallyUrl)))
      }
  }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
    implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, sponsorName, employerType) =>
        val submitCall = routes.SponsoringEmployerAddressSearchController.onSubmit(mode, srn, startDate, accessType, version, index)
        val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url
        val empType = Messages(s"chargeC.employerType.${employerType.toString}")
        val enterManuallyUrl = routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url
        form.bindFromRequest().fold(
          formWithErrors => {
            Future.successful(BadRequest(view(formWithErrors, schemeName, submitCall, returnUrl, sponsorName, empType, enterManuallyUrl)))
          },
          value =>
            addressLookupConnector.addressLookupByPostCode(value).flatMap {
              case Nil =>
                Future.successful(BadRequest(view(formWithError("chargeC.employerAddressSearch.error.invalid"),
                  schemeName, submitCall, returnUrl, sponsorName, empType, enterManuallyUrl)))
              case addresses =>
                auditService.sendEvent(AddressLookupAuditEvent(value))
                for {
                  updatedAnswers <- Future.fromTry(request.userAnswers.set(SponsoringEmployerAddressSearchPage(index), addresses))
                  _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(SponsoringEmployerAddressSearchPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
        )

      }
  }

  protected def formWithError(message: String): Form[String] = {
    form.withError("value", message)
  }
}
