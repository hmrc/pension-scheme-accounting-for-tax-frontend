#!/bin/bash

echo ""
echo "Applying migration WhatYouWillNeed"

echo "Adding routes to conf/app.routes"
echo "" >> ../conf/app.routes
echo "GET        /whatYouWillNeed                       controllers.chargeC.WhatYouWillNeedController.onPageLoad()" >> ../conf/app.routes

echo "Adding messages to conf.messages"
echo "" >> ../conf/messages.en
echo "whatYouWillNeed.title = whatYouWillNeed" >> ../conf/messages.en
echo "whatYouWillNeed.heading = whatYouWillNeed" >> ../conf/messages.en

echo "Migration WhatYouWillNeed completed"
