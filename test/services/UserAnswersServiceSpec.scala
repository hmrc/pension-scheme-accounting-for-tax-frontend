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

package services

import base.SpecBase
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar

class UserAnswersServiceSpec extends SpecBase with MockitoSugar with ScalaFutures {

  ".set" must {
    "set amended version to null and the page value for a scheme level charge being added if version is 2" in {

    }

    "set amended version to null and the page value for a scheme level charge being deleted if version is 2" in {

    }

    "set only the page value for a scheme level charge being added if version is 1" in {

    }

    "set only the page value for a scheme level charge being deleted if version is 1" in {

    }

    "set only the page value for a member level charge being added if version is 1" in {

    }

    "set only the page value for a member level charge being changed if version is 1" in {

    }

    "set only the page value for a member level charge being deleted if version is 1" in {

    }

    "set amended version, member version to null, status to New and the page value" +
      " for a scheme level charge if version is 2 for a new member being added" in {

    }

    "set amended version, member version to null, status to Changed and the page value" +
      " for a scheme level charge if version is 2 for a member being changed" in {

    }

    "set amended version, member version to null, status to Deleted and the page value" +
      " for a scheme level charge if version is 2 for a member being deleted" in {

    }
  }
}
