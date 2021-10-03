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
import models.ChargeType.{ChargeTypeAuthSurplus, ChargeTypeLifetimeAllowance, ChargeTypeOverseasTransfer, ChargeTypeAnnualAllowance}
import models.SponsoringEmployerType.{SponsoringEmployerTypeIndividual, SponsoringEmployerTypeOrganisation}
import models.chargeC.{ChargeCDetails, SponsoringOrganisationDetails}
import models.chargeD.ChargeDDetails
import models.chargeE.ChargeEDetails
import models.chargeG.ChargeAmounts
import play.api.libs.json._
import models.{Member, SponsoringEmployerType, UserAnswers, Employer, MemberDetails, ChargeType}
import pages.chargeC.{WhichTypeOfSponsoringEmployerPage, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeG.ChargeAmountsPage
import services.MembersOrEmployers.{MEMBERS, MembersOrEmployers, EMPLOYERS}
import uk.gov.hmrc.viewmodels.Text.{Message, Literal}
import viewmodels.Link

class ChargePaginationService @Inject()(config: FrontendAppConfig) {
  private def nodeInfo(chargeType:ChargeType):Option[(String, String, String, MembersOrEmployers)] = {
    chargeType match {
      case ChargeTypeAnnualAllowance =>
       Some(Tuple4("chargeEDetails", pages.chargeE.ChargeDetailsPage.toString, "members", MEMBERS))
      case ChargeTypeAuthSurplus =>
        Some(Tuple4("chargeCDetails", pages.chargeC.ChargeCDetailsPage.toString, "employers", EMPLOYERS))
      case ChargeTypeLifetimeAllowance =>
        Some(Tuple4("chargeDDetails", pages.chargeD.ChargeDetailsPage.toString, "members", MEMBERS))
      case ChargeTypeOverseasTransfer =>
        Some(Tuple4("chargeGDetails", ChargeAmountsPage.toString, "members", MEMBERS))
      case _ => None
    }
  }

  def getItemsPaginated(
    pageNo:Int,
    ua: UserAnswers,
    viewUrl: Int => Call,
    removeUrl: Int => Call,
    chargeType: ChargeType
  ): Option[PaginatedMembersInfo] = {
    chargeType match {
      case ChargeTypeAnnualAllowance =>
        getItemsPaginatedWithAmount[ChargeEDetails](pageNo, ua: UserAnswers, _.chargeAmount, viewUrl, removeUrl, chargeType: ChargeType)
      case ChargeTypeAuthSurplus =>
        getItemsPaginatedWithAmount[ChargeCDetails](pageNo, ua: UserAnswers, _.amountTaxDue, viewUrl, removeUrl, chargeType: ChargeType)
      case ChargeTypeLifetimeAllowance =>
        getItemsPaginatedWithAmount[ChargeDDetails](pageNo, ua: UserAnswers, _.total, viewUrl, removeUrl, chargeType: ChargeType)
      case ChargeTypeOverseasTransfer =>
        getItemsPaginatedWithAmount[ChargeAmounts](pageNo, ua: UserAnswers, _.amountTaxDue, viewUrl, removeUrl, chargeType: ChargeType)
      case _ => None
    }
  }

  private def getItemsPaginatedWithAmount[A](
    pageNo:Int,
    ua: UserAnswers,
    amount: A=>BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call,
    chargeType: ChargeType
  )(implicit reads: Reads[A]): Option[PaginatedMembersInfo] = {
    nodeInfo(chargeType).flatMap{ case (chargeRootNode, chargeDetailsNode, listNode, membersOrEmployers) =>
      val pageSize = config.membersPageSize
      val allItemsAsJsArray = (ua.data \ chargeRootNode \ listNode).as[JsArray].value.zipWithIndex
        .filter{ case (m, _) => (m \ "memberStatus").as[String] != "Deleted"}
      val (start, end) = ChargePaginationService.pageStartAndEnd(pageNo, allItemsAsJsArray.size, pageSize)
      val pageItemsAsJsArray = allItemsAsJsArray.slice(start, end)

      if (pageItemsAsJsArray.isEmpty) {
        None
      } else {
        val startMember = (pageNo - 1) * pageSize + 1
        val items: Either[Seq[Member], Seq[Employer]] =
          if (membersOrEmployers == MEMBERS) {
            Left(createMembers(membersOrEmployers, pageItemsAsJsArray, chargeDetailsNode, amount, viewUrl, removeUrl).reverse)
          } else {
            Right(createEmployers(membersOrEmployers, pageItemsAsJsArray, chargeDetailsNode, amount, viewUrl, removeUrl).reverse)
          }
        Some(PaginatedMembersInfo(
          itemsForCurrentPage = items,
          paginationStats = PaginationStats(
            currentPage = pageNo,
            startMember = startMember,
            lastMember = startMember + pageItemsAsJsArray.size - 1,
            totalMembers = allItemsAsJsArray.size,
            totalPages = ChargePaginationService.totalPages(allItemsAsJsArray.size, pageSize)
          )
        ))
      }
    }
  }

  private def createEmployers[A](
    membersOrEmployers: MembersOrEmployers,
    membersForPageJson: IndexedSeq[(JsValue, Int)],
    chargeDetailsNode: String,
    amount: A => BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call)(implicit reads: Reads[A]): Seq[Employer] = {
    if (membersOrEmployers == EMPLOYERS) {
      membersForPageJson.map { case (m, index) => val chargeAmount = (m \ chargeDetailsNode).asOpt[A] match {
        case Some(chargeDetails) => amount(chargeDetails)
        case None => BigDecimal(0)
      }
        createEmployer(m, index, chargeAmount, viewUrl, removeUrl)
      }
    } else {
      Nil
    }
  }

  private def createMembers[A](
    membersOrEmployers: MembersOrEmployers,
    membersForPageJson: IndexedSeq[(JsValue, Int)],
    chargeDetailsNode: String,
    amount: A => BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call)(implicit reads: Reads[A]): Seq[Member] = {
    if (membersOrEmployers == MEMBERS) {
      membersForPageJson.map { case (m, index) =>
        val chargeAmount = (m \ chargeDetailsNode).asOpt[A] match {
          case Some(chargeDetails) => amount(chargeDetails)
          case None => BigDecimal(0)
        }
        createMember(m, index, chargeAmount, viewUrl, removeUrl)
      }
    } else {
      Nil
    }
  }

  private def createMember(
    jsValueMemberRootNode:JsValue,
    index: Int,
    amount: BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  ):Member = {
    val member = (jsValueMemberRootNode \ "memberDetails").as[MemberDetails]
    Member(
      index,
      member.fullName,
      member.nino,
      amount,
      viewUrl(index).url,
      removeUrl(index).url
    )
  }

  private def createEmployer(
    jsValueEmployerRootNode:JsValue,
    index: Int,
    amount: BigDecimal,
    viewUrl: Int => Call,
    removeUrl: Int => Call
  ):Employer = {
    (jsValueEmployerRootNode \ WhichTypeOfSponsoringEmployerPage.toString).as[SponsoringEmployerType] match {
      case SponsoringEmployerTypeIndividual =>
        val member = (jsValueEmployerRootNode \ SponsoringIndividualDetailsPage.toString).as[MemberDetails]
        Employer(
          index,
          member.fullName,
          amount,
          viewUrl(index).url,
          removeUrl(index).url
        )
      case SponsoringEmployerTypeOrganisation =>
        val member = (jsValueEmployerRootNode \ SponsoringOrganisationDetailsPage.toString).as[SponsoringOrganisationDetails]
        Employer(
          index,
          member.name,
          amount,
          viewUrl(index).url,
          removeUrl(index).url
        )
    }
  }

  private[services] def pagerSeq(ps:PaginationStats): Seq[Int] = {
    (ps.currentPage, ps.totalPages) match {
      case (_, tp) if tp <= 5 => 1 to tp
      case (c, _) if c < 3 => 1 to 5
      case (c, tp) if c > (tp - 3) => (tp - 4) to tp
      case (c, _) => (c - 2) to (c + 2)
    }
  }

  def pagerNavSeq(ps:PaginationStats, url:Int => Call): Seq[Link] = {
    if (ps.totalPages == 1) {
      Nil
    } else {
      val items = pagerSeq(ps).map { c =>
        val target = if (ps.currentPage == c) {
          ""
        } else {
          url(c).url
        }
        Link(id = s"nav-$c", url = target, linkText = Literal(s"$c"), hiddenText = None)
      }
      val prevLink = if (ps.currentPage == 1) {
        Nil
      } else {
        Seq(Link(id = "nav-prev", url = url(ps.currentPage - 1).url, linkText = Message("paginationPreviousPage"),
          hiddenText = None))
      }
      val nextLink = if (ps.currentPage == ps.totalPages) {
        Nil
      } else {
        Seq(Link(id = "nav-next", url = url(ps.currentPage + 1).url, linkText = Message("paginationNextPage"),
          hiddenText = None))
      }
      prevLink ++ items ++ nextLink
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

private object MembersOrEmployers extends Enumeration {
  type MembersOrEmployers = Value
  val MEMBERS, EMPLOYERS = Value
}
