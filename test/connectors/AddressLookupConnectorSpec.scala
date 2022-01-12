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

package connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import models.TolerantAddress
import org.scalatest.RecoverMethods
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http.{HeaderCarrier, HttpException}
import utils.WireMockHelper

class AddressLookupConnectorSpec extends AsyncWordSpec
  with Matchers
  with WireMockHelper
  with RecoverMethods {

  private def url = "/lookup"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  override protected def portConfigKey: String = "microservice.services.address-lookup.port"

  private lazy val connector = injector.instanceOf[AddressLookupConnector]

  ".addressLookupByPostCode" must {
    "returns an Ok and empty list" which {
      "means the AddressLookup has found no data for postcode" in {
        server.stubFor(
          post(urlEqualTo(url))
            .withRequestBody(equalTo(Json.obj("postcode"->"ZZ1 1ZZ").toString()))
            .willReturn
          (
            aResponse().withStatus(OK)
              .withBody("[]")
          )
        )
        connector.addressLookupByPostCode("ZZ1 1ZZ") map {
          result =>
            result mustEqual Nil
        }
      }

    }

    "returns an ok and Seq of Addresses" which {
      "means the AddressLookup has found data for the postcode" in {

        val payload =
          """[{"uprn":990091234524,
            |"localCustodian":{"code":121,"name":"North Somerset","J":""},
            |"id":"GB990091234524",
            |"language":"en",
            |"B":"",
            |"address":{"postcode":"ZZ1 1ZZ","F":"",
            |"country":{"code":"UK","name":"United Kingdom","R":""},
            |"county":"Somerset",
            |"subdivision":{"code":"GB-ENG","name":"England","B":""},
            |"town":"Anytown",
            |"lines":["10 Other Place","Some District"]}
            |},
            |{"Y":"",
            |"uprn":990091234514,
            |"localCustodian":{"code":121,"name":"North Somerset","H":""},
            |"id":"GB990091234514",
            |"language":"en",
            |"address":{"postcode":"ZZ1 1ZZ",
            |"country":{"code":"UK","name":"United Kingdom","U":""},
            |"county":"Somerset",
            |"subdivision":{"code":"GB-ENG","name":"England","R":""},
            |"town":"Anytown",
            |"lines":["2 Other Place","Some District"],"D":""}
            |}
            |]
            |""".stripMargin


        val tolerantAddressSample = Seq(
          TolerantAddress(Some("10 Other Place"), Some("Some District"), Some("Anytown"), Some("Somerset"), Some("ZZ1 1ZZ"), Some("UK")),
          TolerantAddress(Some("2 Other Place"), Some("Some District"), Some("Anytown"), Some("Somerset"), Some("ZZ1 1ZZ"), Some("UK"))
        )


        server.stubFor(
          post(urlEqualTo(url))
            .withHeader("user-agent", matching(".+"))
            .withRequestBody(equalTo(Json.obj("postcode"->"ZZ1 1ZZ").toString()))
            .willReturn
            (
              aResponse().withStatus(OK)
                .withBody(payload)
            )
        )
        connector.addressLookupByPostCode("ZZ1 1ZZ") map {
          result =>
            result mustEqual tolerantAddressSample
        }
      }
    }



    "returns an ok and Seq of Addresses" which {
      "means the AddressLookup has found data for the postcode when one of addresses returned has no address lines" in {

        val payload =
          """[{"uprn":990091234524,
            |"localCustodian":{"code":121,"name":"North Somerset","J":""},
            |"id":"GB990091234524",
            |"language":"en",
            |"B":"",
            |"address":{"postcode":"ZZ1 1ZZ","F":"",
            |"country":{"code":"UK","name":"United Kingdom","R":""},
            |"county":"Somerset",
            |"subdivision":{"code":"GB-ENG","name":"England","B":""},
            |"town":"Anytown",
            |"lines":[]}
            |},
            |{"Y":"",
            |"uprn":990091234514,
            |"localCustodian":{"code":121,"name":"North Somerset","H":""},
            |"id":"GB990091234514",
            |"language":"en",
            |"address":{"postcode":"ZZ1 1ZZ",
            |"country":{"code":"UK","name":"United Kingdom","U":""},
            |"county":"Somerset",
            |"subdivision":{"code":"GB-ENG","name":"England","R":""},
            |"town":"Anytown",
            |"lines":["2 Other Place","Some District"],"D":""}
            |}
            |]
            |""".stripMargin


        val tolerantAddressSample = Seq(
          TolerantAddress(Some("2 Other Place"), Some("Some District"), Some("Anytown"), Some("Somerset"), Some("ZZ1 1ZZ"), Some("UK"))
        )


        server.stubFor(
          post(urlEqualTo(url))
            .withHeader("user-agent", matching(".+"))
            .withRequestBody(equalTo(Json.obj("postcode"->"ZZ1 1ZZ").toString()))
            .willReturn
            (
              aResponse().withStatus(OK)
                .withBody(payload)
            )
        )
        connector.addressLookupByPostCode("ZZ1 1ZZ") map {
          result =>
            result mustEqual tolerantAddressSample
        }
      }
    }





    "returns an exception" which {
      "means the Address Lookup has returned a non 200 response " in {

        server.stubFor(
          post(urlEqualTo(url))
            .withRequestBody(equalTo(Json.obj("postcode"->"ZZ1 1ZZ").toString()))
            .willReturn
          (
            aResponse().withStatus(NOT_FOUND).withBody("Something is wrong")
          )
        )

        recoverToSucceededIf[HttpException] {

          connector.addressLookupByPostCode("ZZ1 1ZZ")

        }
      }
      "means the Address Lookup has returned a response in 200 range but not an OK" in {

        server.stubFor(
          post(urlEqualTo(url))
            .withRequestBody(equalTo(Json.obj("postcode"->"ZZ1 1ZZ").toString()))
            .willReturn
          (
            aResponse().withStatus(NO_CONTENT)
          )
        )

        recoverToSucceededIf[HttpException] {

          connector.addressLookupByPostCode("ZZ1 1ZZ")

        }
      }

    }
  }
}
