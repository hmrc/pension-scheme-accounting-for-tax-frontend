#!/bin/bash

echo ""
echo "Applying migration MemberDetails"

echo "Adding routes to conf/app.routes"

echo "" >> ../conf/app.routes
echo "GET        /memberDetails                        controllers.chargeE.MemberDetailsController.onPageLoad(mode: Mode = NormalMode)" >> ../conf/app.routes
echo "POST       /memberDetails                        controllers.chargeE.MemberDetailsController.onSubmit(mode: Mode = NormalMode)" >> ../conf/app.routes

echo "GET        /changeMemberDetails                  controllers.chargeE.MemberDetailsController.onPageLoad(mode: Mode = CheckMode)" >> ../conf/app.routes
echo "POST       /changeMemberDetails                  controllers.chargeE.MemberDetailsController.onSubmit(mode: Mode = CheckMode)" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "memberDetails.title = memberDetails" >> ../conf/messages.en
echo "memberDetails.heading = memberDetails" >> ../conf/messages.en
echo "memberDetails.firstName = firstName" >> ../conf/messages.en
echo "memberDetails.lastName = lastName" >> ../conf/messages.en
echo "memberDetails.checkYourAnswersLabel = memberDetails" >> ../conf/messages.en
echo "memberDetails.error.firstName.required = Enter firstName" >> ../conf/messages.en
echo "memberDetails.error.lastName.required = Enter lastName" >> ../conf/messages.en
echo "memberDetails.error.firstName.length = firstName must be 35 characters or less" >> ../conf/messages.en
echo "memberDetails.error.lastName.length = lastName must be 35 characters or less" >> ../conf/messages.en

echo "Adding to UserAnswersEntryGenerators"
awk '/trait UserAnswersEntryGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryMemberDetailsUserAnswersEntry: Arbitrary[(MemberDetailsPage.type, JsValue)] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        page  <- arbitrary[MemberDetailsPage.type]";\
    print "        value <- arbitrary[MemberDetails].map(Json.toJson(_))";\
    print "      } yield (page, value)";\
    print "    }";\
    next }1' ../test/generators/UserAnswersEntryGenerators.scala > tmp && mv tmp ../test/generators/UserAnswersEntryGenerators.scala

echo "Adding to PageGenerators"
awk '/trait PageGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryMemberDetailsPage: Arbitrary[MemberDetailsPage.type] =";\
    print "    Arbitrary(MemberDetailsPage)";\
    next }1' ../test/generators/PageGenerators.scala > tmp && mv tmp ../test/generators/PageGenerators.scala

echo "Adding to ModelGenerators"
awk '/trait ModelGenerators/ {\
    print;\
    print "";\
    print "  implicit lazy val arbitraryMemberDetails: Arbitrary[MemberDetails] =";\
    print "    Arbitrary {";\
    print "      for {";\
    print "        firstName <- arbitrary[String]";\
    print "        lastName <- arbitrary[String]";\
    print "      } yield MemberDetails(firstName, lastName)";\
    print "    }";\
    next }1' ../test/generators/ModelGenerators.scala > tmp && mv tmp ../test/generators/ModelGenerators.scala

echo "Adding to UserAnswersGenerator"
awk '/val generators/ {\
    print;\
    print "    arbitrary[(MemberDetailsPage.type, JsValue)] ::";\
    next }1' ../test/generators/UserAnswersGenerator.scala > tmp && mv tmp ../test/generators/UserAnswersGenerator.scala

echo "Adding helper method to CheckYourAnswersHelper"
awk '/class CheckYourAnswersHelper/ {\
     print;\
     print "";\
     print "  def memberDetails: Option[Row] = addRequiredDetailsToUserAnswers.get(MemberDetailsPage) map {";\
     print "    answer =>";\
     print "      Row(";\
     print "        key     = Key(msg\"memberDetails.checkYourAnswersLabel\", classes = Seq(\"govuk-!-width-one-half\")),";\
     print "        value   = Value(lit\"${answer.firstName} ${answer.lastName}\"),";\
     print "        actions = List(";\
     print "          Action(";\
     print "            content            = msg\"site.edit\",";\
     print "            href               = routes.MemberDetailsController.onPageLoad(CheckMode).url,";\
     print "            visuallyHiddenText = Some(msg\"site.edit.hidden\".withArgs(msg\"memberDetails.checkYourAnswersLabel\"))";\
     print "          )";\
     print "        )";\
     print "      )";\
     print "  }";\
     next }1' ../app/utils/CheckYourAnswersHelper.scala > tmp && mv tmp ../app/utils/CheckYourAnswersHelper.scala

echo "Migration MemberDetails completed"
