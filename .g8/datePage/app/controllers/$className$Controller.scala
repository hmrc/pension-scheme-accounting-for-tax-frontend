package controllers

import config.FrontendAppConfig
import connectors.cache.UserAnswersCacheConnector
import controllers.actions._
import forms.$className$FormProvider
import javax.inject.Inject
import models.{GenericViewModel, Mode}
import navigators.CompoundNavigator
import pages.$className$Page
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import renderer.Renderer
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendBaseController
import views.html.$className$View
import scala.concurrent.{ExecutionContext, Future}

class $className$Controller @Inject()(override val messagesApi: MessagesApi,
                                      userAnswersCacheConnector: UserAnswersCacheConnector,
                                      navigator: CompoundNavigator,
                                      identify: IdentifierAction,
                                      getData: DataRetrievalAction,
                                      requireData: DataRequiredAction,
                                      formProvider: $className$FormProvider,
                                      val controllerComponents: MessagesControllerComponents,
                                      config: FrontendAppConfig,
                                      view: $className$View
                                     )(implicit ec: ExecutionContext) extends FrontendBaseController with I18nSupport {

  val form = formProvider()

  def onPageLoad(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData) {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        val preparedForm = request.userAnswers.get($className$Page) match {
          case Some(value) => form.fill(value)
          case None => form
        }

        val viewModel = GenericViewModel(
          submitUrl = routes.$className$Controller.onSubmit(mode, srn).url,
          returnUrl = config.schemeDashboardUrl(any(): IdentifierRequest[_]).format(srn),
          schemeName = schemeName)

        val date = DateInput.localDate(preparedForm("value"))

        val json = Json.obj(
          "form" -> preparedForm,
          "viewModel" -> viewModel,
          "date" -> date
        )

        Future.successful(Ok(view(preparedForm, schemeName, submitUrl, returnUrl)))
      }
  }

  def onSubmit(mode: Mode, srn: String): Action[AnyContent] = (identify andThen getData(srn) andThen requireData).async {
    implicit request =>
      DataRetrievals.retrieveSchemeName { schemeName =>
        form.bindFromRequest().fold(
          formWithErrors => {

            val viewModel = GenericViewModel(
              submitUrl = routes.$className$Controller.onSubmit(mode, srn).url,
              returnUrl = config.schemeDashboardUrl(request).format(srn),
              schemeName = schemeName)

            val date = DateInput.localDate(formWithErrors("value"))

            val json = Json.obj(
              "form" -> formWithErrors,
              "viewModel" -> viewModel,
              "date" -> date
            )

            renderer.render("$className;format="decap"$.njk", json).map(BadRequest(_))
          },
          value =>
            for {
              updatedAnswers <- Future.fromTry(request.userAnswers.set($className$Page, value))
              _ <- userAnswersCacheConnector.save(request.internalId, updatedAnswers.data)
            } yield Redirect(navigator.nextPage($className$Page, mode, updatedAnswers, srn))
        )
      }
  }
}
