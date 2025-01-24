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
import models.requests.DataRequest
import models.{AccessType, ChargeType, Index, Mode, TolerantAddress}
import navigators.CompoundNavigator
import pages.chargeC.{SponsoringEmployerAddressPage, SponsoringEmployerAddressResultsPage, SponsoringEmployerAddressSearchPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents, Result}
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import utils.TwirlMigration
import views.html.chargeC.SponsoringEmployerAddressResultsView

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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
                                                           view: SponsoringEmployerAddressResultsView)(implicit ec: ExecutionContext)
    extends FrontendBaseController
    with I18nSupport {

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
          val submitCall = routes.SponsoringEmployerAddressResultsController.onSubmit(mode, srn, startDate, accessType, version, index)
          val returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url

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

          val empType = Messages(s"chargeC.employerType.${employerType.toString}")
          val enterManuallyUrl = routes.SponsoringEmployerAddressController.onPageLoad(mode, srn, startDate, accessType, version, index).url

      Future.successful(status(view(form, schemeName, submitCall, returnUrl, sponsorName, empType, enterManuallyUrl,
        TwirlMigration.convertToRadioItems(addressesSorted))))
      }
    }
  }
}
