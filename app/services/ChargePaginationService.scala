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
import services.MembersOrEmployers.{MEMBERS, MembersOrEmployers, EMPLOYERS}

class ChargePaginationService @Inject()(config: FrontendAppConfig) {

  private def listNode(membersOrEmployers:MembersOrEmployers): String =
    membersOrEmployers match {
      case MEMBERS => "members"
      case EMPLOYERS => "employers"
    }

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

  // scalastyle:off parameter.number
  def getItemsPaginated[A](
    pageNo:Int,
    ua: UserAnswers,
    chargeRootNode:String,
    chargeDetailsNode: String = "chargeDetails",
    amount: A=>BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call,
    membersOrEmployers: MembersOrEmployers
  )(implicit reads: Reads[A]): Option[PaginatedMembersInfo] = {
    val pageSize = config.membersPageSize
    val filteredMembers = (ua.data \ chargeRootNode \ listNode(membersOrEmployers)).as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}
    val (start, end) = ChargePaginationService.pageStartAndEnd(pageNo, filteredMembers.size, pageSize)
    val paginatedMembersTmp = filteredMembers
      .slice(start, end)
    val paginatedMembers = if (membersOrEmployers == MEMBERS) {
        paginatedMembersTmp.flatMap { case (m, index) =>
          (m \ chargeDetailsNode).asOpt[A].map(createMember(m, index, amount, _, viewUrl, removeUrl)).toSeq
        }
      } else {
        Nil
      }

    val paginatedEmployers = if (membersOrEmployers == EMPLOYERS) {
      paginatedMembersTmp.flatMap { case (m, index) =>
        (m \ chargeDetailsNode).asOpt[A].map(createEmployer(m, index, amount, _, viewUrl, removeUrl)).toSeq
      }
    } else {
      Nil
    }

    if (paginatedMembers.isEmpty && paginatedEmployers.isEmpty) {
      None
    } else {
      val startMember = (pageNo - 1) * pageSize + 1
      val items: Either[Seq[Member], Seq[Employer]] =
        if (membersOrEmployers == MEMBERS) Left(paginatedMembers.reverse) else Right(paginatedEmployers.reverse)
      Some(PaginatedMembersInfo(
        itemsForCurrentPage = items,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = startMember,
          lastMember = startMember + paginatedMembers.size - 1,
          totalMembers = filteredMembers.size,
          totalPages = ChargePaginationService.totalPages(filteredMembers.size, pageSize)
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
}

case class PaginationStats(currentPage: Int, startMember:Int, lastMember:Int, totalMembers:Int, totalPages: Int)

object PaginationStats {
  implicit val formats: Format[PaginationStats] = Json.format[PaginationStats]
}

case class PaginatedMembersInfo(itemsForCurrentPage:Either[Seq[Member], Seq[Employer]], paginationStats: PaginationStats) {
  def membersForCurrentPage:Seq[Member] = itemsForCurrentPage match {
    case Left(value) => value
    case _ => Nil
  }

  def employersForCurrentPage:Seq[Employer] = itemsForCurrentPage match {
    case Right(value) => value
    case _ => Nil
  }
}

object ChargePaginationService {
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

object MembersOrEmployers extends Enumeration {
  type MembersOrEmployers = Value
  val MEMBERS, EMPLOYERS = Value
}
