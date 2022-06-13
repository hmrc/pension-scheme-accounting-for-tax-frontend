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

package audit

import models.AdministratorOrPractitioner.{Administrator, Practitioner}
import models.JourneyType.AFT_COMPILE_RETURN
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AFTReturnEmailAuditEventSpec extends AnyFlatSpec with Matchers {

  "AFTReturnEmailAuditEvent" should "output the correct map of data" in {

    val event = AFTReturnEmailAuditEvent(
      psaOrPspId = "A2500001",
      AFT_COMPILE_RETURN,
      Administrator,
      emailAddress = "test@test.com"
    )

    val expected: Map[String, String] = Map(
      "emailAddress" -> "test@test.com",
      "submittedBy" -> Administrator.toString,
      "psaId" -> "A2500001"
    )

    event.auditType shouldBe "AFTReturnCompiledEmail"
    event.details shouldBe expected
  }

  "AFTReturnEmailAuditEvent" should "output the correct map of data for  Practitioner" in {

    val event = AFTReturnEmailAuditEvent(
      psaOrPspId = "2500001",
      AFT_COMPILE_RETURN,
      Practitioner,
      emailAddress = "test@test.com"
    )

    val expected: Map[String, String] = Map(
      "emailAddress" -> "test@test.com",
      "submittedBy" -> Practitioner.toString,
      "pspId" -> "2500001"
    )

    event.auditType shouldBe "AFTReturnCompiledEmail"
    event.details shouldBe expected
  }
}
