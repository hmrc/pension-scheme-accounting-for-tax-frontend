/*
 * Copyright 2022 HM Revenue & Customs
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

package controllers.fileUpload

import controllers.actions.MutableFakeDataRetrievalAction
import controllers.base.ControllerSpecBase
import data.SampleData._
import forms.fileUpload.UploadCheckSelectionFormProvider
import matchers.JsonMatchers
import models.LocalDateBinder._
import models.fileUpload.UploadCheckSelection
import models.fileUpload.UploadCheckSelection.Yes
import models.{ChargeType, FileUploadDataCache, FileUploadStatus, GenericViewModel, UploadId, UserAnswers}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import pages.fileUpload.UploadCheckPage
import play.api.data.Form
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsObject, Json}
import play.api.test.Helpers.{route, status, _}
import play.api.{Application, inject}
import play.twirl.api.Html
import services.fileUpload.UploadProgressTracker
import uk.gov.hmrc.viewmodels.NunjucksSupport

import java.time.LocalDateTime
import scala.concurrent.Future

class FileUploadCheckControllerSpec extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  private val mutableFakeDataRetrievalAction: MutableFakeDataRetrievalAction = new MutableFakeDataRetrievalAction()
  private val fakeUploadProgressTracker: MutableFakeUploadProgressTracker = new MutableFakeUploadProgressTracker()

  val extraModules: Seq[GuiceableModule] = Seq(
    inject.bind[UploadProgressTracker].to(fakeUploadProgressTracker)
  )
  private val application: Application = applicationBuilderMutableRetrievalAction(mutableFakeDataRetrievalAction,extraModules).build()
  private val templateToBeRendered = "fileUpload/fileUploadResult.njk"
  private val chargeType = ChargeType.ChargeTypeAnnualAllowance
  private val form = new UploadCheckSelectionFormProvider

  private def httpPathGET: String = controllers.fileUpload.routes.FileUploadCheckController.
    onPageLoad(srn, startDate, accessType, versionInt,chargeType, UploadId("")).url
  private def httpPathPOST: String = controllers.fileUpload.routes.FileUploadCheckController.
    onSubmit( srn, startDate, accessType, versionInt,chargeType, UploadId("")).url


  private val jsonToPassToTemplate:Form[UploadCheckSelection]=>JsObject = form => Json.obj(
    "form" -> form,
    "viewModel" -> GenericViewModel(
      submitUrl = routes.FileUploadCheckController.onSubmit(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      schemeName = schemeName),
    "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
    "radios" -> UploadCheckSelection.radios(form),
    "fileName" -> "name"
  )
  private val jsonToPassToTemplateFill:Form[UploadCheckSelection]=>JsObject = form => Json.obj(
    "form" -> form.fill(Yes),
    "viewModel" -> GenericViewModel(
      submitUrl = routes.FileUploadCheckController.onSubmit(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url,
      returnUrl = controllers.routes.ReturnToSchemeDetailsController.returnToSchemeDetails(srn, startDate, accessType, versionInt).url,
      schemeName = schemeName),
    "chargeTypeText" -> ChargeType.fileUploadText(chargeType),
    "radios" -> UploadCheckSelection.radios(form.fill(Yes)),
    "fileName" -> "name"
  )

  private val dateTimeNow = LocalDateTime.now

  private val fileUploadDataCache: FileUploadDataCache =
    FileUploadDataCache(
      uploadId = "uploadId",
      reference ="reference",
      status=  FileUploadStatus("UploadedSuccessfully", name = Some("name")),
      created= dateTimeNow,
      lastUpdated= dateTimeNow,
      expireAt= dateTimeNow
    )

  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.savePartial(any(), any(), any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }

  val validData: UserAnswers = userAnswersWithSchemeName
  val expectedJson: JsObject = userAnswersWithSchemeName.data
  private val valuesValid: Map[String, Seq[String]] = Map(
    "value" -> Seq("yes")
  )
  private val valuesValid2: Map[String, Seq[String]] = Map(
    "value" -> Seq("no")
  )
  private val valuesInvalid: Map[String, Seq[String]] = Map(
    "value" -> Seq("No")
  )
  "ChargeDetails Controller" must {
    "return OK and the correct view for a GET" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered
      jsonCaptor.getValue must containJson(jsonToPassToTemplate(form.apply()))
    }

    "return OK and the correct view for a GET when the question has previously been answered" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      val ua = validData.set(UploadCheckPage(chargeType), Yes).get

      mutableFakeDataRetrievalAction.setDataToReturn(Some(ua))
      val templateCaptor = ArgumentCaptor.forClass(classOf[String])
      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpGETRequest(httpPathGET)).value

      status(result) mustEqual OK

      verify(mockRenderer, times(1)).render(templateCaptor.capture(), jsonCaptor.capture())(any())

      templateCaptor.getValue mustEqual templateToBeRendered

      jsonCaptor.getValue must containJson(jsonToPassToTemplateFill(form.apply()))
    }


    "Save data to user answers and redirect to Validation Page when valid Yes value submitted" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(routes.ValidationController.onPageLoad(srn, startDate, accessType, versionInt, chargeType, UploadId("")).url)
    }

    "Save data to user answers and redirect to File upload Page when valid No value submitted" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val jsonCaptor = ArgumentCaptor.forClass(classOf[JsObject])

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesValid2)).value

      status(result) mustEqual SEE_OTHER

      verify(mockUserAnswersCacheConnector, times(1)).savePartial(any(), jsonCaptor.capture, any(), any())(any(), any())
      jsonCaptor.getValue must containJson(expectedJson)

      redirectLocation(result) mustBe Some(routes.FileUploadController.onPageLoad(srn, startDate, accessType, versionInt, chargeType).url)
    }

    "return a BAD REQUEST when invalid data is submitted" in {
      fakeUploadProgressTracker.setDataToReturn(fileUploadDataCache)
      mutableFakeDataRetrievalAction.setDataToReturn(Some(validData))

      val result = route(application, httpPOSTRequest(httpPathPOST, valuesInvalid)).value

      status(result) mustEqual BAD_REQUEST

      verify(mockUserAnswersCacheConnector, times(0)).savePartial(any(), any(), any(), any())(any(), any())
    }
  }
}
