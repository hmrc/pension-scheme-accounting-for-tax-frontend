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
import forms.chargeC.SponsoringEmployerAddressFormProvider
import javax.inject.Inject
import models.LocalDateBinder._
import models.chargeC.SponsoringEmployerAddress
import models.{Mode, GenericViewModel, AccessType, Index}
import navigators.CompoundNavigator
import pages.chargeC.SponsoringEmployerAddressPage
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{AnyContent, MessagesControllerComponents, Action}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.{Future, ExecutionContext}

class SponsoringEmployerAddressController @Inject()(override val messagesApi: MessagesApi,
                                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                                    userAnswersService: UserAnswersService,
                                                    navigator: CompoundNavigator,
                                                    identify: IdentifierAction,
                                                    getData: DataRetrievalAction,
                                                    allowAccess: AllowAccessActionProvider,
                                                    requireData: DataRequiredAction,
                                                    formProvider: SponsoringEmployerAddressFormProvider,
                                                    val controllerComponents: MessagesControllerComponents,
                                                    config: FrontendAppConfig,
                                                    renderer: Renderer)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private val form = formProvider()

  private def countryJsonElement(tuple: (String, String), isSelected: Boolean): JsArray = Json.arr(
    if (isSelected) {
      Json.obj(
        "value" -> tuple._1,
        "text" -> tuple._2,
        "selected" -> true
      )
    } else {
      Json.obj(
        "value" -> tuple._1,
        "text" -> tuple._2
      )
    }
  )

  private def jsonCountries(countrySelected: Option[String])(implicit messages: Messages): JsArray =
    config.validCountryCodes
      .map(countryCode => (countryCode, messages(s"country.$countryCode")))
      .sortWith(_._2 < _._2)
      .foldLeft(JsArray(Seq(Json.obj("value" -> "", "text" -> "")))) { (acc, nextCountryTuple) =>
        acc ++ countryJsonElement(nextCountryTuple, countrySelected.contains(nextCountryTuple._1))
      }

  def onPageLoad(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
    implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, sponsorName, employerType) =>
        val preparedForm = request.userAnswers.get(SponsoringEmployerAddressPage(index)) match {
          case None        => form
          case Some(value) => form.fill(value)
        }
        val viewModel = GenericViewModel(
          submitUrl = routes.SponsoringEmployerAddressController.onSubmit(mode, srn, startDate, accessType, version, index).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val json = Json.obj(
          "srn" -> srn,
          "startDate" -> Some(startDate),
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "sponsorName" -> sponsorName,
          "employerType" -> Messages(s"chargeC.employerType.${employerType.toString}"),
          "countries" -> jsonCountries(preparedForm.data.get("country"))
        )

        renderer.render("chargeC/sponsoringEmployerAddress.njk", json).map(Ok(_))
      }
    }

  private def addArgsToErrors(form: Form[SponsoringEmployerAddress], args: String*): Form[SponsoringEmployerAddress] =
    form copy (errors = form.errors.map(_ copy (args = args)))

  def onSubmit(mode: Mode, srn: String, startDate: LocalDate, accessType: AccessType, version: Int, index: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeEmployerTypeAndSponsoringEmployer(index) { (schemeName, sponsorName, employerType) =>
        form
          .bindFromRequest()
          .fold(
            formWithErrors => {
              val viewModel = GenericViewModel(
                submitUrl = routes.SponsoringEmployerAddressController.onSubmit(mode, srn, startDate, accessType, version, index).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              val json = Json.obj(
                "srn" -> srn,
                "startDate" -> Some(startDate),
                "form" -> addArgsToErrors(formWithErrors, sponsorName),
                "viewModel" -> viewModel,
                "sponsorName" -> sponsorName,
                "employerType" -> Messages(s"chargeC.employerType.${employerType.toString}"),
                "countries" -> jsonCountries(formWithErrors.data.get("country"))
              )

              renderer.render("chargeC/sponsoringEmployerAddress.njk", json).map(BadRequest(_))
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(SponsoringEmployerAddressPage(index), value, mode))
                _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
              } yield Redirect(navigator.nextPage(SponsoringEmployerAddressPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
          )
      }
    }
}
