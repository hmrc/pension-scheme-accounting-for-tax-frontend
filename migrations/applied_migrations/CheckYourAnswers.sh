#!/bin/bash

echo ""
echo "Applying migration CheckYourAnswers"

echo "Adding routes to conf/app.routes"
echo "" >> ../conf/app.routes
echo "GET        /checkYourAnswers                       controllers.chargeC.CheckYourAnswersController.onPageLoad()" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "checkYourAnswers.title = checkYourAnswers" >> ../conf/messages.en
echo "checkYourAnswers.heading = checkYourAnswers" >> ../conf/messages.en

echo "Migration CheckYourAnswers completed"
