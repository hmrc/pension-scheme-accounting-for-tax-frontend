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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import models.LocalDateBinder._
import forms.chargeC.AddressListFormProvider
import javax.inject.Inject
import models.GenericViewModel
import models.Index
import models.Mode
import models.TolerantAddress
import models.chargeC.SponsoringEmployerAddress
import models.requests.DataRequest
import navigators.CompoundNavigator
import pages.chargeC.AddressListPage
import pages.chargeC.SponsoringEmployerAddressSearchPage
import pages.chargeC.SponsoringEmployerAddressPage
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.MessagesApi
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.MessagesControllerComponents
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AddressListController @Inject()(override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      allowAccess: AllowAccessActionProvider,
                                      requireData: DataRequiredAction,
                                      formProvider: AddressListFormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async { implicit request =>
      presentPage(mode, srn, startDate, index, form, Ok)
    }

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen allowAccess(srn, startDate) andThen requireData).async { implicit request =>
      form
        .bindFromRequest()
        .fold(
          formWithErrors => {
            presentPage(mode, srn, startDate, index, formWithErrors, BadRequest)
          },
          value => {
            val selectedSponsoringEmployerAddress = request.userAnswers.get(SponsoringEmployerAddressSearchPage) match {
              case None =>
                SponsoringEmployerAddress("", "", None, None, "", None)
              case Some(addresses) =>
                SponsoringEmployerAddress.fromTolerantAddress(addresses(value))
            }

            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set(SponsoringEmployerAddressPage(index), selectedSponsoringEmployerAddress))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage(AddressListPage, mode, updatedAnswers, srn, startDate))
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

  private def presentPage(mode: Mode, srn: String, startDate: LocalDate, index: Index, form:Form[Int], status:Status)(implicit request: DataRequest[AnyContent]) = {
    DataRetrievals.retrieveSchemeAndSponsoringEmployer(index) { (schemeName, sponsorName) =>
      request.userAnswers.get(SponsoringEmployerAddressSearchPage) match {
        case None => throw new RuntimeException("??")
        case Some(addresses) =>
          val viewModel = GenericViewModel(
            submitUrl = routes.AddressListController.onSubmit(mode, srn, startDate, index).url,
            returnUrl = config.managePensionsSchemeSummaryUrl.format(srn),
            schemeName = schemeName
          )

          val addressesAsJson = transformAddressesForTemplate(addresses)

          val json = Json.obj(
            "form" -> form,
            "viewModel" -> viewModel,
            "sponsorName" -> sponsorName,
            "enterManuallyUrl" -> routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, index).url,
            "addresses" -> addressesAsJson
          )

          renderer.render("chargeC/addressList.njk", json).map(status(_))
      }
    }
  }
}
