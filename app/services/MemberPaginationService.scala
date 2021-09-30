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
    val filteredMembers = (ua.data \ chargeRootNode \ "members").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}
    val (start, end) = MemberPaginationService.pageStartAndEnd(pageNo, filteredMembers.size, pageSize)
    val paginatedMembers = filteredMembers
      .slice(start, end)
      .flatMap { case (m, index) =>
        (m \ chargeDetailsNode).asOpt[A].map(createMember(m, index, amount, _, viewUrl, removeUrl)).toSeq
      }
    if (paginatedMembers.isEmpty) {
      None
    } else {
      val startMember = (pageNo - 1) * pageSize + 1
      Some(PaginatedMembersInfo(
        membersForCurrentPage = paginatedMembers.reverse,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = startMember,
          lastMember = startMember + paginatedMembers.size - 1,
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
    val filteredMembers = (ua.data \ chargeRootNode \ "employers").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}
    val (start, end) = MemberPaginationService.pageStartAndEnd(pageNo, filteredMembers.size, pageSize)

    val filteredEmployers = (ua.data \ chargeRootNode \ "employers").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}

    val paginatedEmployers = filteredEmployers
      .slice(start, end)
      .flatMap { case (m, index) =>
        (m \ "chargeDetails").asOpt[A].map(createEmployer(m, index, amount, _, viewUrl, removeUrl)).toSeq
      }
    if (paginatedEmployers.isEmpty) {
      None
    } else {
      val startMember = (pageNo - 1) * pageSize + 1
      Some(PaginatedEmployersInfo(
        membersForCurrentPage = paginatedEmployers.reverse,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = startMember,
          lastMember = startMember + paginatedEmployers.size - 1,
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

  private def pageStart(pageNo:Int, totalPages: Int, pageSize: Int):Int = {
    (totalPages - pageNo) * pageSize - 1 match {case x if x < 0 => 0 case x => x}
  }

  def pageStartAndEnd(pageNo:Int, totalMembers: Int, pageSize: Int):Tuple2[Int, Int] = {
    val pages = totalPages(totalMembers, pageSize)
    val start = pageStart(pageNo, pages, pageSize)
    val end = if (pageNo == 1) {
      totalMembers
    } else {
      pageStart(pageNo - 1, pages, pageSize)
    }
    (start,end)
  }

}
