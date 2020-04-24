#!/bin/bash

echo ""
echo "Applying migration EnterPostcode"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /:srn/new-return/employerAddressSearch                        controllers.chargeC.SponsoringEmployerAddressSearchController.onPageLoad(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/employerAddressSearch                        controllers.chargeC.SponsoringEmployerAddressSearchController.onSubmit(mode: Mode = NormalMode, srn: String)" >> ../conf/app.routes

echo "GET        /:srn/new-return/changeEnterPostcode                  controllers.chargeC.SponsoringEmployerAddressSearchController.onPageLoad(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes
echo "POST       /:srn/new-return/changeEnterPostcode                  controllers.chargeC.SponsoringEmployerAddressSearchController.onSubmit(mode: Mode = CheckMode, srn: String)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "employerAddressSearch.title = employerAddressSearch" >> ../conf/messages.en
echo "employerAddressSearch.heading = employerAddressSearch" >> ../conf/messages.en
echo "employerAddressSearch.checkYourAnswersLabel = employerAddressSearch" >> ../conf/messages.en
echo "employerAddressSearch.error.required = Enter employerAddressSearch" >> ../conf/messages.en
echo "employerAddressSearch.error.length = EnterPostcode must be 8 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryEnterPostcodeUserAnswersEntry: Arbitrary[(SponsoringEmployerAddressSearchPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[SponsoringEmployerAddressSearchPage.type]";\
    print "        value <- arbitrary[String].suchThat(_.nonEmpty).map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryEnterPostcodePage: Arbitrary[SponsoringEmployerAddressSearchPage.type] =";\
    print "    Arbitrary(SponsoringEmployerAddressSearchPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(SponsoringEmployerAddressSearchPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def employerAddressSearch: Option[Row] = userAnswers.get(SponsoringEmployerAddressSearchPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"employerAddressSearch.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"$answer\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = controllers.routes.SponsoringEmployerAddressSearchController.onPageLoad(CheckMode, srn).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"employerAddressSearch.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration EnterPostcode completed"
