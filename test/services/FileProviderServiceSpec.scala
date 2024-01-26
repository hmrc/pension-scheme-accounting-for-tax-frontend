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

package services

import base.SpecBase
import models.ChargeType.{ChargeTypeAnnualAllowance, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Environment

import java.io.File

class FileProviderServiceSpec extends SpecBase with MockitoSugar {

  private val environment: Environment = injector.instanceOf[Environment]
  private val fileProviderService = new FileProviderService(environment)

  "getInstructionsFile" must {
    "for the charge type Annual Allowance return the correct file name if PSR answer is Yes" in {

      val instructionsFileToCheck = new File("./conf/resources/fileDownload/instructions/instructions_aft-annual-allowance-charge-upload-format.ods")

      fileProviderService.getInstructionsFile(ChargeTypeAnnualAllowance, Some(true)) mustBe instructionsFileToCheck

    }
    "for the charge type Lifetime Allowance return the correct file name if PSR answer is No" in {

      val instructionsFileToCheck =
        new File("./conf/resources/fileDownload/instructions/instructions_aft-lifetime-allowance-charge-upload-non-public-service-pensions-remedy.ods")

      fileProviderService.getInstructionsFile(ChargeTypeLifetimeAllowance, Some(false)) mustBe instructionsFileToCheck

    }
    "for the charge type Lifetime Allowance return the correct file name if PSR is None" in {

      val instructionsFileToCheck =
        new File("./conf/resources/fileDownload/instructions/instructions_aft-lifetime-allowance-charge-upload-non-public-service-pensions-remedy.ods")

      fileProviderService.getInstructionsFile(ChargeTypeLifetimeAllowance, None) mustBe instructionsFileToCheck

    }

    "for the charge type Overseas Transfer return the correct file name" in {

      val instructionsFileToCheck = new File("./conf/resources/fileDownload/instructions/aft-overseas-transfer-charge-upload-format-instructions.ods")

      fileProviderService.getInstructionsFile(ChargeTypeOverseasTransfer, None) mustBe instructionsFileToCheck

    }
  }

  "getTemplateFile" must {
    "for the charge type Annual Allowance return the correct file name if PSR answer is No" in {

      val templateFileToCheck =
        new File("./conf/resources/fileDownload/template/upload-template_aft-annual-allowance-charge_non-public-service-pensions-remedy.csv")

      fileProviderService.getTemplateFile(ChargeTypeAnnualAllowance, Some(false)) mustBe templateFileToCheck

    }
    "for the charge type Annual Allowance return the correct file name if PSR is None" in {

      val templateFileToCheck =
        new File("./conf/resources/fileDownload/template/upload-template_aft-annual-allowance-charge_non-public-service-pensions-remedy.csv")

      fileProviderService.getTemplateFile(ChargeTypeAnnualAllowance, None) mustBe templateFileToCheck

    }
    "for the charge type Lifetime Allowance return the correct file name if PSR answer is Yes" in {

      val templateFileToCheck = new File("./conf/resources/fileDownload/template/upload-template_aft-lifetime-allowance-charge.csv")

      fileProviderService.getTemplateFile(ChargeTypeLifetimeAllowance, Some(true)) mustBe templateFileToCheck

    }

    "for the charge type Overseas Transfer return the correct file name" in {

      val templateFileToCheck = new File("./conf/resources/fileDownload/template/aft-overseas-transfer-charge-upload-template.csv")

      fileProviderService.getTemplateFile(ChargeTypeOverseasTransfer, None) mustBe templateFileToCheck

    }
  }

}