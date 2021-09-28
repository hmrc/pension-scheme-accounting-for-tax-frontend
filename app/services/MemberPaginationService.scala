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

import java.time.LocalDate
import play.api.libs.json.{JsArray, Reads}
import models.{UserAnswers, MemberDetails, Member, AccessType}

class MemberPaginationService @Inject()(config: FrontendAppConfig) {

  // scalastyle:off parameter.number
  def getMembersPaginated[A]
  (uaChargeDetailsNode:String,
    amount:A=>BigDecimal,
    viewUrl: (Int, String, LocalDate, AccessType, Int) => Call,
    removeUrl: (Int, String, LocalDate, UserAnswers, AccessType, Int) => Call,
    pageNo:Int
  )(
    ua: UserAnswers, srn: String, startDate: LocalDate, accessType: AccessType, version: Int
  )(implicit reads: Reads[A]): Option[PaginatedMembersInfo] = {
    val pageSize = config.membersPageSize
    val start = (pageNo - 1) * pageSize
    val end = pageNo * pageSize

    val filteredMembers = (ua.data \ uaChargeDetailsNode \ "members").as[JsArray].value.zipWithIndex
      .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}

    val paginatedMembers = filteredMembers
      .slice(start, end)
      .flatMap { case (m, index) =>
        val member = (m \ "memberDetails").as[MemberDetails]
        (m \ "chargeDetails").asOpt[A].map { chargeDetails =>
          Member(
            index,
            member.fullName,
            member.nino,
            amount(chargeDetails),
            viewUrl(index, srn, startDate, accessType, version).url,
            removeUrl(index, srn, startDate, ua, accessType, version).url
          )
        }.toSeq
      }
    if (paginatedMembers.isEmpty) {
      None
    } else {
      Some(PaginatedMembersInfo(
        members = paginatedMembers,
        startMember = start + 1,
        lastMember = start + paginatedMembers.size,
        totalMembers = filteredMembers.size))
    }
  }
}

// TODO: Add totalPages:Int
case class PaginatedMembersInfo(members:Seq[Member], startMember:Int, lastMember:Int, totalMembers:Int)
