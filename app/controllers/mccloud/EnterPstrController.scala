/*
 * Copyright 2023 HM Revenue & Customs
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

package controllers.mccloud

import connectors.cache.UserAnswersCacheConnector
import controllers.DataRetrievals
import controllers.actions._
import forms.mccloud.EnterPstrFormProvider
import models.LocalDateBinder._
import models.{AccessType, ChargeType, CheckMode, GenericViewModel, Index, Mode, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.mccloud.{ChargeAmountReportedPage, EnterPstrPage, SchemePathHelper}
import play.api.data.Form
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsArray, Json}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class EnterPstrController @Inject()(override val messagesApi: MessagesApi,
                                    userAnswersCacheConnector: UserAnswersCacheConnector,
                                    userAnswersService: UserAnswersService,
                                    navigator: CompoundNavigator,
                                    identify: IdentifierAction,
                                    getData: DataRetrievalAction,
                                    allowAccess: AllowAccessActionProvider,
                                    requireData: DataRequiredAction,
                                    formProvider: EnterPstrFormProvider,
                                    val controllerComponents: MessagesControllerComponents,
                                    renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport
    with CommonMcCloud {

  private def form(): Form[String] = formProvider()

  //scalastyle:off method.length
  //scalastyle:off cyclomatic.complexity
  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Index,
                 schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        val viewModel = GenericViewModel(
          submitUrl = routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
          returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
          schemeName = schemeName
        )

        val preparedForm = request.userAnswers.get(EnterPstrPage(chargeType, index, schemeIndex)) match {
          case Some(value) => form().fill(value)
          case None => form()
        }

        val schemeSize = countSchemeSize(request.userAnswers, index, chargeType)

        (schemeSize == 0, mode) match {

          case (false, CheckMode) =>
            (ordinal(Some(schemeIndex)).map(_.resolve).getOrElse(""), lifetimeOrAnnual(chargeType)) match {
              case (ordinalValue, Some(chargeTypeDesc)) =>
                val json = Json.obj(
                  "srn" -> srn,
                  "startDate" -> Some(localDateToString(startDate)),
                  "form" -> preparedForm,
                  "viewModel" -> viewModel,
                  "ordinal" -> ordinalValue,
                  "chargeTypeDesc" -> chargeTypeDesc
                )
                renderer.render("mccloud/enterPstr.njk", json).map(Ok(_))
              case _ =>
                renderer.render("badRequest.njk").map(BadRequest(_))
            }
          case (_, NormalMode) =>
            (ordinal(Some(schemeIndex)).map(_.resolve).getOrElse(""), lifetimeOrAnnual(chargeType)) match {
              case (ordinalValue, Some(chargeTypeDesc)) =>
                val json = Json.obj(
                  "srn" -> srn,
                  "startDate" -> Some(localDateToString(startDate)),
                  "form" -> preparedForm,
                  "viewModel" -> viewModel,
                  "ordinal" -> ordinalValue,
                  "chargeTypeDesc" -> chargeTypeDesc
                )
                renderer.render("mccloud/enterPstr.njk", json).map(Ok(_))
              case _ =>
                renderer.render("badRequest.njk").map(BadRequest(_))
            }


          case (true, CheckMode) =>
            Future(Redirect(navigator.nextPage(
              ChargeAmountReportedPage(chargeType, index, Some(schemeIndex)), mode, request.userAnswers, srn, startDate, accessType, version)))
        }
      }
    }

  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Index,
               schemeIndex: Index): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>

        form()
          .bindFromRequest()
          .fold(
            formWithErrors => {

              val viewModel = GenericViewModel(
                submitUrl = routes.EnterPstrController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index, schemeIndex).url,
                returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
                schemeName = schemeName
              )

              (ordinal(Some(schemeIndex)).map(_.resolve).getOrElse(""), lifetimeOrAnnual(chargeType)) match {
                case (ordinalValue, Some(chargeTypeDesc)) =>
                  val json = Json.obj(
                    "srn" -> srn,
                    "startDate" -> Some(localDateToString(startDate)),
                    "form" -> formWithErrors,
                    "viewModel" -> viewModel,
                    "ordinal" -> ordinalValue,
                    "chargeTypeDesc" -> chargeTypeDesc
                  )
                  renderer.render("mccloud/enterPstr.njk", json).map(BadRequest(_))
                case _ =>
                  renderer.render("badRequest.njk").map(BadRequest(_))
              }
            },
            value =>
              for {
                updatedAnswers <- Future.fromTry(userAnswersService.set(EnterPstrPage(chargeType, index, schemeIndex), value, mode))
                _ <- userAnswersCacheConnector.savePartial(request.internalId, updatedAnswers.data,
                  chargeType = Some(chargeType), memberNo = Some(index.id))
              } yield Redirect(navigator.nextPage(EnterPstrPage(chargeType, index, schemeIndex), mode, updatedAnswers, srn, startDate, accessType, version))
          )

      }
    }

  private def countSchemeSize(userAnswers: UserAnswers, index: Int, chargeType: ChargeType): Int = {
    SchemePathHelper.path(chargeType, index).readNullable[JsArray].reads(userAnswers.data).asOpt.flatten.map(_.value.size).getOrElse(0)
  }
}
