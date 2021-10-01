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
    val membersExcludingDeletedJson = (ua.data \ chargeRootNode \ listNode(membersOrEmployers)).as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}
    val (start, end) = ChargePaginationService.pageStartAndEnd(pageNo, membersExcludingDeletedJson.size, pageSize)
    val membersForPageJson = membersExcludingDeletedJson.slice(start, end)

    val paginatedMembers = if (membersOrEmployers == MEMBERS) {
        membersForPageJson.flatMap { case (m, index) =>
          (m \ chargeDetailsNode).asOpt[A].map(createMember(m, index, amount, _, viewUrl, removeUrl)).toSeq
        }
      } else {
        Nil
      }

    val paginatedEmployers = if (membersOrEmployers == EMPLOYERS) {
      membersForPageJson.flatMap { case (m, index) =>
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
          totalMembers = membersExcludingDeletedJson.size,
          totalPages = ChargePaginationService.totalPages(membersExcludingDeletedJson.size, pageSize)
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

  def pagerSeq(ps:PaginationStats): Seq[Int] = {
    // Current page and total pages
    /*
      1 2 3 4 5 6 7 8 9 10
      x
        x
          x
            x
              x
     */
    (ps.currentPage, ps.totalPages) match {
      case (_, tp) if tp <= maxSize => (1 to tp)
      case (x, _) if x < 3 => (1 to x) ++ ((x + 1) to (x + (3 - x)))
      case (x, tp) if x > (tp - 2) =>
      case (x, tp) =>
    }

    //
    //if (currentPage == 1) {
    //  Seq(1)
    //} else {
    //  Seq(1, 2)
    //}
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

  private def pageStart(pageNo:Int, totalPages: Int, pageSize: Int, totalMembers: Int):Int =
    if (pageNo == totalPages) {
      0
    } else {
      (totalMembers - 1) - (pageNo * pageSize) + 1
    }

  def pageStartAndEnd(pageNo:Int, totalMembers: Int, pageSize: Int):(Int, Int) = {
    val pages = totalPages(totalMembers, pageSize)
    val start = pageStart(pageNo, pages, pageSize, totalMembers)
    val end = if (pageNo == 1) {
      totalMembers
    } else {
      pageStart(pageNo - 1, pages, pageSize, totalMembers)
    }
    (start,end)
  }
}

object MembersOrEmployers extends Enumeration {
  type MembersOrEmployers = Value
  val MEMBERS, EMPLOYERS = Value
}
