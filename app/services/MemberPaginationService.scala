/*
 * Copyright 2021 HM Revenue & Customs
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

import config.FrontendAppConfig
import play.api.mvc.Call
import com.google.inject.Inject
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.SponsoringOrganisationDetails
import play.api.libs.json._
import models.{Member, SponsoringEmployerType, UserAnswers, Employer, MemberDetails}

class MemberPaginationService @Inject()(config: FrontendAppConfig) {

  private def createMember[A](
    jsValueChargeRootNode:JsValue,
    index: Int,
    amount: A=>BigDecimal,
    chargeDetails: A,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  ):Member = {
    val member = (jsValueChargeRootNode \ "memberDetails").as[MemberDetails]
    Member(
      index,
      member.fullName,
      member.nino,
      amount(chargeDetails),
      viewUrl(index).url,
      removeUrl(index).url
    )
  }

  def getMembersPaginated[A](
    pageNo:Int,
    ua: UserAnswers,
    chargeRootNode:String,
    chargeDetailsNode: String = "chargeDetails",
    amount: A=>BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  )(implicit reads: Reads[A]): Option[PaginatedMembersInfo] = {
    val pageSize = config.membersPageSize
    val start = (pageNo - 1) * pageSize
    val end = pageNo * pageSize

    val filteredMembers = (ua.data \ chargeRootNode \ "members").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}

    // TODO: Make this more efficient by calculating the stats and then only reversing the slice chosen
    val paginatedMembers = filteredMembers.reverse
      .slice(start, end)
      .flatMap { case (m, index) =>
        (m \ chargeDetailsNode).asOpt[A].map(createMember(m, index, amount, _, viewUrl, removeUrl)).toSeq
      }
    if (paginatedMembers.isEmpty) {
      None
    } else {
      Some(PaginatedMembersInfo(
        membersForCurrentPage = paginatedMembers,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = start + 1,
          lastMember = start + paginatedMembers.size,
          totalMembers = filteredMembers.size,
          totalPages = MemberPaginationService.totalPages(filteredMembers.size, pageSize)
        )
      ))
    }
  }

  private def createEmployer[A](
    jsValueChargeRootNode:JsValue,
    index: Int,
    amount: A=>BigDecimal,
    chargeDetails: A,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  ):Employer = {
    (jsValueChargeRootNode \ "whichTypeOfSponsoringEmployer").as[SponsoringEmployerType] match {
      case SponsoringEmployerTypeIndividual =>
        val member = (jsValueChargeRootNode \ "sponsoringIndividualDetails").as[MemberDetails]
        Employer(
          index,
          member.fullName,
          amount(chargeDetails),
          viewUrl(index).url,
          removeUrl(index).url
        )
      case SponsoringEmployerTypeOrganisation =>
        val member = (jsValueChargeRootNode \ "sponsoringOrganisationDetails").as[SponsoringOrganisationDetails]
        Employer(
          index,
          member.name,
          amount(chargeDetails),
          viewUrl(index).url,
          removeUrl(index).url
        )
    }
  }

  def getEmployersPaginated[A](
    pageNo:Int,
    ua: UserAnswers,
    chargeRootNode:String,
    amount: A=>BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  )(implicit reads: Reads[A]): Option[PaginatedEmployersInfo] = {
    val pageSize = config.membersPageSize
    val start = (pageNo - 1) * pageSize
    val end = pageNo * pageSize

    val filteredEmployers = (ua.data \ chargeRootNode \ "employers").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}

    val paginatedEmployers = filteredEmployers.reverse
      .slice(start, end)
      .flatMap { case (m, index) =>
        (m \ "chargeDetails").asOpt[A].map(createEmployer(m, index, amount, _, viewUrl, removeUrl)).toSeq
      }
    if (paginatedEmployers.isEmpty) {
      None
    } else {
      Some(PaginatedEmployersInfo(
        membersForCurrentPage = paginatedEmployers,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = start + 1,
          lastMember = start + paginatedEmployers.size,
          totalMembers = filteredEmployers.size,
          totalPages = MemberPaginationService.totalPages(filteredEmployers.size, pageSize)
        )
      ))
    }
  }

}

case class PaginationStats(currentPage: Int, startMember:Int, lastMember:Int, totalMembers:Int, totalPages: Int)

object PaginationStats {
  implicit val formats: Format[PaginationStats] = Json.format[PaginationStats]
}

case class PaginatedMembersInfo(membersForCurrentPage:Seq[Member], paginationStats: PaginationStats)

case class PaginatedEmployersInfo(membersForCurrentPage:Seq[Employer], paginationStats: PaginationStats)

object MemberPaginationService {
  def totalPages(totalMembers:Int, pageSize: Int):Int = (totalMembers.toFloat / pageSize).ceil.toInt
}
