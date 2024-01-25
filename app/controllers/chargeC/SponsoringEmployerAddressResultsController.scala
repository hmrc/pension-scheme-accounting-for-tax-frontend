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

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.chargeC.SponsoringEmployerAddressResultsFormProvider
import models.LocalDateBinder._

import javax.inject.Inject
import models.{TolerantAddress, GenericViewModel, AccessType, Mode, ChargeType, Index}
import models.requests.DataRequest
import navigators.CompoundNavigator
import pages.chargeC.{SponsoringEmployerAddressPage, SponsoringEmployerAddressSearchPage, SponsoringEmployerAddressResultsPage}
import play.api.data.Form
import play.api.i18n.{MessagesApi, Messages, I18nSupport}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import scala.util.Try
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
                  _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                    chargeType = Some(ChargeType.ChargeTypeAuthSurplus), memberNo = Some(index.id))
                } yield Redirect(navigator.nextPage(SponsoringEmployerAddressResultsPage(index), mode, updatedAnswers, srn, startDate, accessType, version))
              case Some(addresses) => for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(SponsoringEmployerAddressResultsPage(index), addresses(value), mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data)
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


  private def mkString(p: TolerantAddress) = p.lines.mkString(" ").toLowerCase()

  // Find numbers in proposed address in order of significance, from rightmost to leftmost.
  // Pad with None to ensure we never return an empty sequence
  private def numbersIn(p: TolerantAddress): Seq[Option[Int]] =
    "([0-9]+)".r.findAllIn(mkString(p)).map(n => Try(n.toInt).toOption).toSeq.reverse :+ None


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
          val addressesSorted = addresses.sortWith((a, b) => {

            def sort(zipped: Seq[(Option[Int], Option[Int])]): Boolean = zipped match {
              case (Some(nA), Some(nB)) :: tail =>
                if (nA == nB) sort(tail) else nA < nB
              case (Some(_), None) :: _ => true
              case (None, Some(_)) :: _ => false
              case _ => mkString(a) < mkString(b)
            }

            sort(numbersIn(a).zipAll(numbersIn(b), None, None).toList)
          })

          val addressesAsJson = transformAddressesForTemplate(addressesSorted)

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
