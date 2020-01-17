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

package behaviours

import controllers.base.ControllerSpecBase
import matchers.JsonMatchers
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import play.api.libs.json.Json
import play.twirl.api.Html
import uk.gov.hmrc.viewmodels.NunjucksSupport

import scala.concurrent.Future

trait ControllerBehaviours extends ControllerSpecBase with NunjucksSupport with JsonMatchers {
  override def beforeEach: Unit = {
    super.beforeEach
    when(mockUserAnswersCacheConnector.save(any(), any())(any(), any())).thenReturn(Future.successful(Json.obj()))
    when(mockRenderer.render(any(), any())(any())).thenReturn(Future.successful(Html("")))
  }
}
