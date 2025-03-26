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

package models

import models.chargeC.SponsoringEmployerAddress
import play.api.libs.functional.syntax._
import play.api.libs.json._

import scala.language.implicitConversions

case class TolerantAddress(addressLine1: Option[String],
                           addressLine2: Option[String],
                           townOrCity: Option[String],
                           county: Option[String],
                           postcode: Option[String],
                           country: Option[String]) {

  def lines: Seq[String] =
    Seq(
      this.addressLine1,
      this.addressLine2,
      this.townOrCity,
      this.county,
      this.country,
      this.postcode
    ).flatten(s => s)

  def print: String = lines.mkString(", ")

  private def prepopAddress: SponsoringEmployerAddress = {
    SponsoringEmployerAddress(
      addressLine1.getOrElse(""),
      addressLine2,
      townOrCity.getOrElse(""),
      county,
      country.getOrElse(""),
      postcode
    )
  }

  def toPrepopAddress: SponsoringEmployerAddress = toAddress.getOrElse(prepopAddress)

  def toAddress: Option[SponsoringEmployerAddress] = (addressLine1, townOrCity, country) match {
    case (Some(line1), Some(townOrCity), Some(country)) => Some(SponsoringEmployerAddress(line1, addressLine2, townOrCity, county, country, postcode))
    case _ => shuffle
  }

  private def shuffle: Option[SponsoringEmployerAddress] = {
    val values = Seq(addressLine1, addressLine2, townOrCity, county, postcode, country).flatten

    values.length match {
      case 5 => Some(SponsoringEmployerAddress(
        values(0),
        Some(values(1)),
        values(2),
        None,
        values(4),
        Some(values(3))
      ))
      case 4 if (values(3) == "GB") => Some(SponsoringEmployerAddress(
        values(0),
        None,
        values(1),
        None,
        values(3),
        Some(values(2))
      ))
      case 4 => Some(SponsoringEmployerAddress(
        values(0),
        Some(values(1)),
        values(2),
        None,
        values(3),
        None
      ))
      case 3 => Some(SponsoringEmployerAddress(
        values(0),
        None,
        values(1),
        None,
        values(2),
        None
      ))
      case _ => None
    }
  }



  def equalsAddress(address: SponsoringEmployerAddress): Boolean =
    address.line1 == addressLine1.getOrElse("") &&
      address.line2 == addressLine2 &&
      address.townOrCity == townOrCity.getOrElse("") &&
      address.county == county &&
      address.country == country.getOrElse("") &&
      address.postcode == postcode
}

object TolerantAddress {
  val postcodeLookupAddressReads: Reads[TolerantAddress] = (
    (JsPath \ "address" \ "lines").read[List[String]] and
      (JsPath \ "address" \ "postcode").read[String] and
      (JsPath \ "address" \ "country" \ "code").read[String] and
      (JsPath \ "address" \ "town").readNullable[String] and
      (JsPath \ "address" \ "county").readNullable[String]
    ) ((lines, postcode, countryCode, town, county) => {
    val addressLines: (Option[String], Option[String], Option[String], Option[String]) = {
      lines.size match {
        case 0 =>
          (None, None, None, None)
        case 1 =>
          val townOrCounty = getTownOrCounty(town, county, lines)
          (Some(lines.head), townOrCounty._1, townOrCounty._2, None)
        case 2 =>
          val townOrCounty = getTownOrCounty(town, county, lines)
          (Some(lines.head), Some(lines(1)), townOrCounty._1, townOrCounty._2)
        case 3 =>
          val townOrCounty = getTownOrCounty(town, county, lines)
          val townOrCountyValue = if (townOrCounty._2.isDefined) townOrCounty._2 else townOrCounty._1
          (Some(lines.head), Some(lines(1)), Some(lines(2)), townOrCountyValue)
        case numberOfLines if numberOfLines >= 4 => (Some(lines.head), Some(lines(1)), Some(lines(2)), Some(lines(3)))
      }
    }
    TolerantAddress(addressLines._1, addressLines._2, addressLines._3, addressLines._4, Some(postcode), Some(countryCode))
  })


  private def checkIfElementAlreadyExistsInLines(addressLines: List[String], elementToCheck: String) =
    addressLines.mkString("").toLowerCase().contains(elementToCheck.trim().toLowerCase())

  private def getTownOrCounty(town: Option[String], county: Option[String], addressLines: List[String]): (Option[String], Option[String]) = {
    (town, county) match {
      case (Some(formattedTown), None) =>
        (if (checkIfElementAlreadyExistsInLines(addressLines, formattedTown)) None else Some(formattedTown), None)
      case (None, Some(formattedCounty)) =>
        (if (checkIfElementAlreadyExistsInLines(addressLines, formattedCounty)) None else Some(formattedCounty), None)
      case (Some(formattedTown), Some(formattedCounty)) =>
        val townAlreadyExists = checkIfElementAlreadyExistsInLines(addressLines, formattedTown)
        val countyAlreadyExists = checkIfElementAlreadyExistsInLines(addressLines, formattedCounty)
        (townAlreadyExists, countyAlreadyExists) match {
          case (true, false) => (Some(formattedCounty), None)
          case (false, true) => (Some(formattedTown), None)
          case (true, true) => (None, None)
          case _ => (Some(formattedTown), Some(formattedCounty))
        }
      case _ => (None, None)
    }
  }

  val postcodeLookupReads: Reads[Seq[TolerantAddress]] = Reads {
    json =>
      json.validate[Seq[JsValue]].flatMap(addresses => {
        addresses.foldLeft[JsResult[List[TolerantAddress]]](JsSuccess(List.empty)) {
          (addresses, currentAddress) => {
            for {
              sequenceOfAddressess <- addresses
              address <- currentAddress.validate[TolerantAddress](postcodeLookupAddressReads)
            } yield sequenceOfAddressess :+ address
          }
        }
      })
  }

  implicit lazy val formatsTolerantAddress: Format[TolerantAddress] = (
    (JsPath \ "addressLine1").formatNullable[String] and
      (JsPath \ "addressLine2").formatNullable[String] and
      (JsPath \ "townOrCity").formatNullable[String] and
      (JsPath \ "county").formatNullable[String] and
      (JsPath \ "postalCode").formatNullable[String] and
      (JsPath \ "countryCode").formatNullable[String]
    ) (TolerantAddress.apply, unlift(TolerantAddress.unapply))

  implicit def convert(tolerant: TolerantAddress): Option[SponsoringEmployerAddress] =
    for {
      addressLine1 <- tolerant.addressLine1
      townOrCity <- tolerant.townOrCity
      country <- tolerant.country
    } yield {
      SponsoringEmployerAddress(
        addressLine1,
        tolerant.addressLine2,
        townOrCity,
        tolerant.county,
        country,
        tolerant.postcode
      )
    }
}

