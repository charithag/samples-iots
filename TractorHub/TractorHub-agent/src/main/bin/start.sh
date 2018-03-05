#!/bin/bash
echo "" >> agent.log
echo "------------------------------" >> agent.log
upgradeFile="upgrade.zip"
if [ -f "$upgradeFile" ]
then
    echo "Upgrading firmware..." >> agent.log
    sleep 5
    timestamp=$(date +%s)
    backupLocation="backup/$timestamp/"
    mkdir -p "$backupLocation"
    mv agent.jar "$backupLocation"
    mv libs/ "$backupLocation"
    mv start.sh "$backupLocation"
    unzip "$upgradeFile"
    mv agent/* .
    rm "$upgradeFile"
    rm -rf agent/
    echo "Firmware upgraded..." >> agent.log
    sleep 2
    ./start.sh &
else
    java -cp agent.jar:libs/* org.wso2.iot.agent.AgentApplicationImpl
fi