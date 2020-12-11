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

import com.google.inject.Inject
import connectors.SchemeDetailsConnector
import models.SchemeDetails
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SchemeService @Inject()(schemeDetailsConnector: SchemeDetailsConnector)  {

  private val psaIdRegex = "^A[0-9]{7}$".r

  private def isPsaId(s:String) = psaIdRegex.findFirstIn(s).isDefined

  def retrieveSchemeDetails(psaId: String, srn: String, schemeIdType: String)
                           (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[SchemeDetails] = {
    val futureSchemeDetails = if (isPsaId(psaId)) {
      schemeDetailsConnector.getSchemeDetails(
        psaId = psaId,
        idNumber = srn,
        schemeIdType = schemeIdType
      )
    } else {
      schemeDetailsConnector.getPspSchemeDetails(
        pspId = psaId,
        srn = srn
      )
    }

    futureSchemeDetails map { schemeDetails =>
      SchemeDetails(
        schemeName = schemeDetails.schemeName,
        pstr = schemeDetails.pstr,
        schemeStatus = schemeDetails.schemeStatus,
        authorisingPSAID = schemeDetails.authorisingPSAID
      )
    }
  }
}
