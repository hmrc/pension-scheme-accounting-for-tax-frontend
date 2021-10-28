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
import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import models.LocalDateBinder._
import forms.chargeC.SponsoringEmployerAddressResultsFormProvider

import javax.inject.Inject
import models.{AccessType, GenericViewModel, Index, Mode, TolerantAddress}
import models.chargeC.SponsoringEmployerAddress
import models.requests.DataRequest
import navigators.CompoundNavigator
import pages.chargeC.{SponsoringEmployerAddressPage, SponsoringEmployerAddressResultsPage, SponsoringEmployerAddressSearchPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{ExecutionContext, Future}

class SponsoringEmployerAddressResultsController @Inject()(override val messagesApi: MessagesApi,
                                                           userAnswersCacheConnector: UserAnswersCacheConnector,
                                                           userAnswersService: UserAnswersService,
                                                           navigator: CompoundNavigator,
                                                           identify: IdentifierAction,
                                                           getData: DataRetrievalAction,
                                                           allowAccess: AllowAccessActionProvider,
                                                           requireData: DataRequiredAction,
                                                           formProvider: SponsoringEmployerAddressResultsFormProvider,
                                                           val controllerComponents: MessagesControllerComponents,
                                                           config: FrontendAppConfig,
                                                           renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      presentPage(mode, srn, startDate, index, form, Ok, accessType, version)
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            presentPage(mode, srn, startDate, index, formWithErrors, BadRequest, accessType, version)
          },
          value => {
            request.userAnswers.get(SponsoringEmployerAddressSearchPage(index)) match {
              case Some(addresses)  if addresses(value).toAddress.isDefined =>
                val address = addresses(value).toAddress.get.copy(country = "GB")
                for {
                  updatedAnswers <- Future.fromTry(userAnswersService.set(SponsoringEmployerAddressPage(index), address, mode))
                  _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
                } yield Redirect(navigator.nextPage(SponsoringEmployerAddressResultsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
              case Some(addresses) => for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(SponsoringEmployerAddressResultsPage(index), addresses(value), mode))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect( routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index))
              case None =>
                Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
            }

          }
        )
    }

  private def transformAddressesForTemplate(seqTolerantAddresses:Seq[TolerantAddress]):Seq[JsObject] = {
    for ((row, i) <- seqTolerantAddresses.zipWithIndex) yield {
      Json.obj(
        "value" -> i,
        "text" -> row.print
      )
    }
  }

  private def presentPage(mode: Mode, srn: String, startDate: LocalDate, index: Index, form:Form[Int], status:Status,
                          accessType: AccessType, version: Int)(implicit request: DataRequest[AnyContent]): Future[Result] = {
    DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, sponsorName, employerType) =>
      request.userAnswers.get(SponsoringEmployerAddressSearchPage(index)) match {
        case None => Future.successful(Redirect(controllers.routes.SessionExpiredController.onPageLoad))
        case Some(addresses) =>
          val viewModel = GenericViewModel(
            submitUrl = routes.SponsoringEmployerAddressResultsController.onSubmit(mode, srn, startDate, accessType, version, index).url,
            returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
            schemeName = schemeName
          )

          val addressesAsJson = transformAddressesForTemplate(addresses)

          val json = Json.obj(
            "form" -> form,
            "viewModel" -> viewModel,
            "sponsorName" -> sponsorName,
            "employerType" -> Messages(s"chargeC.employerType.${employerType.toString}"),
            "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url,
            "addresses" -> addressesAsJson
          )

          renderer.render("chargeC/sponsoringEmployerAddressResults.njk", json).map(status(_))
      }
    }
  }
}
