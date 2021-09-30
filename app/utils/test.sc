import play.api.libs.json.{JsArray, JsBoolean, JsObject, JsPath, JsSuccess, JsValue, Json, Reads, __}

import scala.collection.mutable.ArrayBuffer
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads.JsObjectReducer

val ua: JsValue = Json.parse(
  """
    |{
    |        "chargeCDetails" : {
    |            "employers" : [
    |                {
    |                    "sponsoringIndividualDetails" : {
    |                        "firstName" : "Ray",
    |                        "lastName" : "Golding",
    |                        "nino" : "AA000020A"
    |                    },
    |                    "memberStatus" : "New",
    |                    "sponsoringEmployerAddress" : {
    |                        "country" : "GB",
    |                        "line4" : "Shropshire",
    |                        "postcode" : "TF3 4NT",
    |                        "line3" : "Telford",
    |                        "line2" : "Ironmasters Way",
    |                        "line1" : "Plaza 2 "
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "amountTaxDue" : 80.02,
    |                        "paymentDate" : "2020-04-28"
    |                    },
    |                    "whichTypeOfSponsoringEmployer" : "individual"
    |                },
    |                {
    |                    "sponsoringIndividualDetails" : {
    |                        "firstName" : "Aaa",
    |                        "lastName" : "Golding",
    |                        "nino" : "AA000620A"
    |                    },
    |                    "memberStatus" : "New",
    |                    "sponsoringEmployerAddress" : {
    |                        "country" : "GB",
    |                        "line4" : "Shropshire",
    |                        "postcode" : "TF3 4NT",
    |                        "line3" : "Telford",
    |                        "line2" : "Ironmasters Way",
    |                        "line1" : "Plaza 2 "
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "amountTaxDue" : 80.02,
    |                        "paymentDate" : "2020-04-28"
    |                    },
    |                    "whichTypeOfSponsoringEmployer" : "company"
    |                },
    |                {
    |                    "sponsoringIndividualDetails" : {
    |                        "firstName" : "Bay",
    |                        "lastName" : "Golding",
    |                        "nino" : "AA000620A"
    |                    },
    |                    "memberStatus" : "New",
    |                    "sponsoringEmployerAddress" : {
    |                        "country" : "GB",
    |                        "line4" : "Shropshire",
    |                        "postcode" : "TF3 4NT",
    |                        "line3" : "Telford",
    |                        "line2" : "Ironmasters Way",
    |                        "line1" : "Plaza 2 "
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "amountTaxDue" : 80.02,
    |                        "paymentDate" : "2020-04-28"
    |                    },
    |                    "whichTypeOfSponsoringEmployer" : "individual"
    |                },
    |                {
    |                    "sponsoringIndividualDetails" : {
    |                        "firstName" : "Cay",
    |                        "lastName" : "McMillan",
    |                        "nino" : "AA000620A"
    |                    },
    |                    "memberStatus" : "New",
    |                    "sponsoringEmployerAddress" : {
    |                        "country" : "GB",
    |                        "line4" : "Warwickshire",
    |                        "postcode" : "B1 1LA",
    |                        "line3" : "Birmingham",
    |                        "line2" : "Post Box APTS",
    |                        "line1" : "45 UpperMarshall Street"
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "amountTaxDue" : 10,
    |                        "paymentDate" : "2020-04-28"
    |                    },
    |                    "whichTypeOfSponsoringEmployer" : "individual"
    |                }
    |            ],
    |            "totalChargeAmount" : 90.02,
    |            "amendedVersion" : 1
    |        },
    |        "submitterDetails" : {
    |            "submitterType" : "PSP",
    |            "submitterID" : "10000240",
    |            "authorisingPsaId" : "A2100005",
    |            "receiptDate" : "2016-12-17",
    |            "submitterName" : "Nigel"
    |        },
    |        "minimalFlags" : {
    |            "deceasedFlag" : false,
    |            "rlsFlag" : false
    |        },
    |        "chargeADetails" : {
    |            "amendedVersion" : 1,
    |            "chargeDetails" : {
    |                "totalAmtOfTaxDueAtHigherRate" : 2500.02,
    |                "totalAmount" : 4500.04,
    |                "numberOfMembers" : 2,
    |                "totalAmtOfTaxDueAtLowerRate" : 2000.02
    |            }
    |        },
    |        "chargeBDetails" : {
    |            "amendedVersion" : 1,
    |            "chargeDetails" : {
    |                "totalAmount" : 1064.92,
    |                "numberOfDeceased" : 2
    |            }
    |        },
    |        "chargeDDetails" : {
    |            "totalChargeAmount" : 2345.02,
    |            "members" : [
    |                {
    |                    "memberStatus" : "New",
    |                    "memberDetails" : {
    |                        "firstName" : "Joy",
    |                        "lastName" : "Kenneth",
    |                        "nino" : "AA089000A"
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "dateOfEvent" : "2020-04-28",
    |                        "taxAt55Percent" : 200.01,
    |                        "taxAt25Percent" : 1800
    |                    }
    |                },
    |                {
    |                    "memberStatus" : "New",
    |                    "memberDetails" : {
    |                        "firstName" : "Brian",
    |                        "lastName" : "Lara",
    |                        "nino" : "AA100000A"
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeDetails" : {
    |                        "dateOfEvent" : "2020-04-28",
    |                        "taxAt55Percent" : 45,
    |                        "taxAt25Percent" : 300.01
    |                    }
    |                }
    |            ],
    |            "amendedVersion" : 1
    |        },
    |        "schemeName" : "Open Scheme Overview API Test",
    |        "loggedInPersonEmail" : "nigel@test.com",
    |        "aftStatus" : "Submitted",
    |        "schemeStatus" : "Open",
    |        "loggedInPersonName" : "Nigel Robert Smith",
    |        "pstr" : "24000040IN",
    |        "chargeGDetails" : {
    |            "totalChargeAmount" : 10000,
    |            "members" : [
    |                {
    |                    "memberStatus" : "New",
    |                    "memberDetails" : {
    |                        "firstName" : "Alex",
    |                        "lastName" : "Grath",
    |                        "dob" : "1950-08-29",
    |                        "nino" : "AA000000C"
    |                    },
    |                    "chargeDetails" : {
    |                        "qropsReferenceNumber" : "000000",
    |                        "qropsTransferDate" : "2016-02-29"
    |                    },
    |                    "memberAFTVersion" : 1,
    |                    "chargeAmounts" : {
    |                        "amountTaxDue" : 10000,
    |                        "amountTransferred" : 10000
    |                    }
    |                }
    |            ],
    |            "amendedVersion" : 1
    |        },
    |        "quarter" : {
    |            "endDate" : "2020-06-30",
    |            "startDate" : "2020-04-01"
    |        }
    |    }
    |""".stripMargin)

val searchNino = "AA000620A"

val chargeCFilterNino = (__ \ 'chargeCDetails \ 'employers).json.update(__.read[JsArray].map { jsArray =>
  JsArray(jsArray.value.filter { jsValue =>
    (jsValue \ "whichTypeOfSponsoringEmployer").as[String] match {
      case "individual" if (jsValue \ "sponsoringIndividualDetails" \ "nino").as[String] == searchNino => true
      case _ => false
    }
  })
 // val x = if (p.value.isEmpty){(__ \ 'chargeCDetails).json.prune} else {__.json.put(Json.obj())}

})

val chargeDFilterNino = (__ \ 'chargeCDetails \ 'employers).json.update(__.read[JsArray].map { jsArray =>
  JsArray(jsArray.value.filter { jsValue =>
    (jsValue \ "whichTypeOfSponsoringEmployer").as[String] match {
      case "individual" if (jsValue \ "sponsoringIndividualDetails" \ "nino").as[String] == searchNino => true
      case _ => false
    }
  })
})

val xx2: Reads[JsObject] =
  (__ \ "chargeCDetails" \ "employers").readNullable[JsArray] flatMap {
    case Some(array) if array.value.nonEmpty =>
      __.json.pickBranch
    case _ => ((__ \ "chargeCDetails").json.prune  and
      (__ \ "chargeCNoMatch").json.put(JsBoolean(true))).reduce
  }

val z = ua.transform(chargeCFilterNino).get

println("val z equals "+Json.prettyPrint(z))


val l = (z \ "chargeCDetails" \ "employers").as[Array[JsValue]]
val xx: Reads[JsObject] = if(l.isEmpty) {
  println("IN IFFFFF")
  (__ \ "chargeCDetails").json.prune
} else {
  println("IN else")
  __.json.pickBranch
}


val p =   if(l.isEmpty) {
  println("IN IFFFFF")
  (__ \ "chargeCDetails").json.prune
} else {
  println("IN else")
  __.json.pickBranch
}

//val m = (ua \ "chargeCDetails" \ "employers").as[ArrayBuffer[JsValue]]
val z1 = z.transform(xx2).get

println("val z1 equals "+Json.prettyPrint(z1))

//l.zipWithIndex.map {case (_, i) =>
//  (l(i) \ "whichTypeOfSponsoringEmployer").as[String] match {
//    case "individual"  if (l(i) \ "sponsoringIndividualDetails" \ "nino").as[String] == searchNino => Unit
//    case _ => m.remove(i)
//  }
//  println(">>>>>>>>>>>>>>>> "+m)
//}