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
import pages.chargeC.{WhichTypeOfSponsoringEmployerPage, SponsoringEmployersQuery, SponsoringIndividualDetailsPage, SponsoringOrganisationDetailsPage}
import pages.chargeD.LifetimeAllowanceMembersQuery
import pages.chargeE.AnnualAllowanceMembersQuery
import pages.chargeG.{ChargeAmountsPage, OverseasTransferMembersQuery}
import uk.gov.hmrc.viewmodels.Text.{Message, Literal}
import viewmodels.Link

class ChargePaginationService @Inject()(config: FrontendAppConfig) {
  private case class NodeInfo(chargeRootNode:String,
    chargeDetailsNode:String,
    createItem: (JsValue, Int, BigDecimal, String, String) => Either[Member, Employer],
    listNode:String
  )

  private val createMember: (JsValue, Int, BigDecimal, String, String) => Either[Member, Employer] =
    (jsValueMemberRootNode, index, amount, viewUrl, removeUrl) => {
    val member = (jsValueMemberRootNode \ "memberDetails").as[MemberDetails]
    Left(Member(
      index,
      member.fullName,
      member.nino,
      amount,
      viewUrl,
      removeUrl
    ))
  }

  private val createEmployer:(JsValue, Int, BigDecimal, String, String) => Either[Member, Employer] =
    (jsValueEmployerRootNode, index, amount, viewUrl, removeUrl) =>
    (jsValueEmployerRootNode \ WhichTypeOfSponsoringEmployerPage.toString).as[SponsoringEmployerType] match {
      case SponsoringEmployerTypeIndividual =>
        val member = (jsValueEmployerRootNode \ SponsoringIndividualDetailsPage.toString).as[MemberDetails]
        Right(Employer(
          index,
          member.fullName,
          amount,
          viewUrl,
          removeUrl
        ))
      case SponsoringEmployerTypeOrganisation =>
        val member = (jsValueEmployerRootNode \ SponsoringOrganisationDetailsPage.toString).as[SponsoringOrganisationDetails]
        Right(Employer(
          index,
          member.name,
          amount,
          viewUrl,
          removeUrl
        ))
    }


  private def nodeInfo(chargeType:ChargeType):Option[NodeInfo] = {
    chargeType match {
      case ChargeTypeAnnualAllowance =>
       Some(NodeInfo(
         chargeRootNode = "chargeEDetails",
         chargeDetailsNode = pages.chargeE.ChargeDetailsPage.toString,
         createItem = createMember,
         listNode = AnnualAllowanceMembersQuery.toString))
      case ChargeTypeAuthSurplus =>
        Some(NodeInfo(
          chargeRootNode = "chargeCDetails",
          chargeDetailsNode = pages.chargeC.ChargeCDetailsPage.toString,
          createItem = createEmployer,
          listNode = SponsoringEmployersQuery.toString))
      case ChargeTypeLifetimeAllowance =>
        Some(NodeInfo(
          chargeRootNode = "chargeDDetails",
          chargeDetailsNode = pages.chargeD.ChargeDetailsPage.toString,
          createItem = createMember,
          listNode = LifetimeAllowanceMembersQuery.toString))
      case ChargeTypeOverseasTransfer =>
        Some(NodeInfo(
          chargeRootNode = "chargeGDetails",
          chargeDetailsNode = ChargeAmountsPage.toString,
          createItem = createMember,
          listNode = OverseasTransferMembersQuery.toString))
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
    nodeInfo(chargeType).flatMap { nodeInfo =>
      chargeType match {
        case ChargeTypeAnnualAllowance =>
          getItemsPaginatedWithAmount[ChargeEDetails](pageNo, ua: UserAnswers, viewUrl, removeUrl, nodeInfo, _.chargeAmount)
        case ChargeTypeAuthSurplus =>
          getItemsPaginatedWithAmount[ChargeCDetails](pageNo, ua: UserAnswers, viewUrl, removeUrl, nodeInfo, _.amountTaxDue)
        case ChargeTypeLifetimeAllowance =>
          getItemsPaginatedWithAmount[ChargeDDetails](pageNo, ua: UserAnswers, viewUrl, removeUrl, nodeInfo, _.total)
        case ChargeTypeOverseasTransfer =>
          getItemsPaginatedWithAmount[ChargeAmounts](pageNo, ua: UserAnswers, viewUrl, removeUrl, nodeInfo, _.amountTaxDue)
        case _ => None
      }
    }

  }

  private def getItemsPaginatedWithAmount[A](
    pageNo:Int,
    ua: UserAnswers,
    viewUrl: Int => Call,
    removeUrl: Int => Call,
    nodeInfo: NodeInfo,
    amount: A=>BigDecimal
  )(implicit reads: Reads[A]): Option[PaginatedMembersInfo] = {
    def extractAmount(m:JsValue):BigDecimal = (m \ nodeInfo.chargeDetailsNode).asOpt[A] match {
        case Some(chargeDetails) => amount(chargeDetails)
        case None => BigDecimal(0)
      }
    val pageSize = config.membersPageSize
    val allItemsAsJsArray = (ua.data \ nodeInfo.chargeRootNode \ nodeInfo.listNode).asOpt[JsArray].map(_.value).getOrElse(Nil).zipWithIndex
      .filter{ case (m, _) => !(m \ "memberStatus").asOpt[String].contains("Deleted")}
    val (start, end) = ChargePaginationService.pageStartAndEnd(pageNo, allItemsAsJsArray.size, pageSize)
    val pageItemsAsJsArray = allItemsAsJsArray.slice(start, end).reverse

    if (pageItemsAsJsArray.isEmpty) {
      None
    } else {
      val startMember = (pageNo - 1) * pageSize + 1

      val items: Either[Seq[Member], Seq[Employer]] =
        ChargePaginationService.toEitherSeq(
          pageItemsAsJsArray.map{ case (item, index) =>
            nodeInfo.createItem(item, index, extractAmount(item), viewUrl(index).url, removeUrl(index).url)
          }
        )

      Some(PaginatedMembersInfo(
        itemsForCurrentPage = items,
        paginationStats = PaginationStats(
          currentPage = pageNo,
          startMember = startMember,
          lastMember = startMember + pageItemsAsJsArray.size - 1,
          totalMembers = allItemsAsJsArray.size,
          totalPages = ChargePaginationService.totalPages(allItemsAsJsArray.size, pageSize),
          totalAmount = allItemsAsJsArray.map { case (item, _) => extractAmount(item)}.sum
        )
      ))
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

case class PaginationStats(currentPage: Int, startMember:Int, lastMember:Int, totalMembers:Int, totalPages: Int, totalAmount:BigDecimal)

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
      totalMembers - pageNo * pageSize
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


  private[services] def toEitherSeq[A,B](seq:Seq[Either[A, B]]):Either[Seq[A], Seq[B]] = {
    if (seq.exists(_.isLeft)) {
      Left(seq.flatMap(_.fold[Seq[A]](Seq(_), _=>Nil)))
    } else {
      Right(seq.flatMap(_.fold[Seq[B]](_=>Nil, Seq(_))))
    }
  }
}
