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

package controllers

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.YesNoFormProvider
import models.ChargeType.ChargeTypeLifetimeAllowance
import models.LocalDateBinder._
import models.{AccessType, ChargeType, GenericViewModel, Index, Mode, NormalMode, UserAnswers}
import navigators.CompoundNavigator
import pages.{IsPublicServicePensionsRemedyPage, MemberFormCompleted, QuarterPage}
import play.api.data.Form
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import services.UserAnswersService
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import uk.gov.hmrc.viewmodels.{NunjucksSupport, Radios}

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class IsPublicServicePensionsRemedyController @Inject()(override val messagesApi: MessagesApi,
                                                        userAnswersCacheConnector: UserAnswersCacheConnector,
                                                        userAnswersService: UserAnswersService,
                                                        navigator: CompoundNavigator,
                                                        identify: IdentifierAction,
                                                        getData: DataRetrievalAction,
                                                        allowAccess: AllowAccessActionProvider,
                                                        requireData: DataRequiredAction,
                                                        config: FrontendAppConfig,
                                                        formProvider: YesNoFormProvider,
                                                        val controllerComponents: MessagesControllerComponents,
                                                        renderer: Renderer)(implicit ec: ExecutionContext)
  extends FrontendBaseController
    with I18nSupport
    with NunjucksSupport {

  private def form(memberName: String, optIndex: Option[Index])(implicit messages: Messages): Form[Boolean] = {
    optIndex match {
      case Some(_) => formProvider(messages("isPublicServicePensionsRemedy.error.required", memberName))
      case None => formProvider(messages("isPublicServicePensionsRemedyBulk.error.required", memberName))
    }
  }

  def onPageLoad(chargeType: ChargeType,
                 mode: Mode,
                 srn: String,
                 startDate: LocalDate,
                 accessType: AccessType,
                 version: Int,
                 index: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async {
      implicit request =>
        DataRetrievals.retrieveSchemeName { schemeName =>
          if (isPsrAlwaysTrueForLtaAbolition(chargeType, request.userAnswers)) {
            for {
              updatedAnswers <- Future.fromTry(userAnswersService.set(IsPublicServicePensionsRemedyPage(chargeType, index), true, mode))
              _ <- userAnswersCacheConnector
                .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = index.map(_.id))
            } yield {
              Redirect(navigator.nextPage(IsPublicServicePensionsRemedyPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
            }
          } else {
            val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")

            val viewModel = GenericViewModel(
              submitUrl = routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName
            )

            val (heading, title) = index match {
              case Some(_) => Tuple2("isPublicServicePensionsRemedy.heading", "isPublicServicePensionsRemedy.title")
              case None => Tuple2("isPublicServicePensionsRemedyBulk.heading", "isPublicServicePensionsRemedyBulk.title")
            }

            val preparedForm = request.userAnswers.get(IsPublicServicePensionsRemedyPage(chargeType, index)) match {
              case None => form(chargeTypeDescription, index)
              case Some(value) => form(chargeTypeDescription, index).fill(value)
            }

            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> Some(localDateToString(startDate)),
              "form" -> preparedForm,
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(preparedForm("value")),
              "chargeTypeDescription" -> chargeTypeDescription,
              "manOrBulkHeading" -> heading,
              "manOrBulkTitle" -> title
            )
            renderer.render("isPublicServicePensionsRemedy.njk", json).map(Ok(_))
          }
        }
    }

  // scalastyle:off method.length
  def onSubmit(chargeType: ChargeType,
               mode: Mode,
               srn: String,
               startDate: LocalDate,
               accessType: AccessType,
               version: Int,
               index: Option[Index]): Action[AnyContent] =
    (identify andThen getData(srn, startDate) andThen requireData andThen allowAccess(srn, startDate, None, version, accessType)).async { implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val chargeTypeDescription = Messages(s"chargeType.description.${chargeType.toString}")
        form(chargeTypeDescription, index)
          .bindFromRequest()
          .fold(formWithErrors => {
            val viewModel = GenericViewModel(
              submitUrl = routes.IsPublicServicePensionsRemedyController.onSubmit(chargeType, mode, srn, startDate, accessType, version, index).url,
              returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, version).url,
              schemeName = schemeName
            )
            val (heading, title) = index match {
              case Some(_) => Tuple2("isPublicServicePensionsRemedy.heading", "isPublicServicePensionsRemedy.title")
              case None => Tuple2("isPublicServicePensionsRemedyBulk.heading", "isPublicServicePensionsRemedyBulk.title")
            }
            val json = Json.obj(
              "srn" -> srn,
              "startDate" -> Some(localDateToString(startDate)),
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "radios" -> Radios.yesNo(formWithErrors("value")),
              "chargeTypeDescription" -> chargeTypeDescription,
              "manOrBulkHeading" -> heading,
              "manOrBulkTitle" -> title
            )
            renderer.render("isPublicServicePensionsRemedy.njk", json).map(BadRequest(_))
          },
            value => {
              for {
                updatedAnswersInProgress <- Future.fromTry(userAnswersService.set(IsPublicServicePensionsRemedyPage(chargeType, index), value, mode))
                updatedAnswers <- Future.fromTry(mode match{
                  case NormalMode => updatedAnswersInProgress.set(
                    MemberFormCompleted(ChargeType.chargeTypeNode(chargeType), index.getOrElse(Index(0)).toInt), false)
                  case _ => Success(updatedAnswersInProgress)
                })
                _ <- userAnswersCacheConnector
                  .savePartial(request.internalId, updatedAnswers.data, chargeType = Some(chargeType), memberNo = index.map(_.id))
              } yield {
                Redirect(navigator.nextPage(IsPublicServicePensionsRemedyPage(chargeType, index), mode, updatedAnswers, srn, startDate, accessType, version))
              }
            }
          )
      }
    }

  private def isPsrAlwaysTrueForLtaAbolition(chargeType: ChargeType, ua: UserAnswers): Boolean = {
    ua.get(QuarterPage) match {
      case Some(aftQuarter) if chargeType == ChargeTypeLifetimeAllowance =>
        val mccloudPsrAlwaysTrueStartDate = config.mccloudPsrAlwaysTrueStartDate
        aftQuarter.startDate.isAfter(mccloudPsrAlwaysTrueStartDate) || aftQuarter.startDate.isEqual(mccloudPsrAlwaysTrueStartDate)
      case _ => false
    }
  }
}
