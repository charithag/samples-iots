package org.wso2.iot.agent;
import ballerina.io;
import ballerina.math;

function (json) onOperationReceived =
            function (json operation) {
                //TODO: Implement agent specific operation handling logic here.
                io:println("Operation received: " + operation.toString());
            };

function () returns (json) onReadEvent = 
            function () returns (json event) {
                //TODO: Implement agent specific event genrating logic here.
                event = {"temperature":math:random() * 100,"humidity":math:random() * 100};
                return;
            };            

function main(string[] args) {
    endpoint<AgentServerConnector> agentServerConnector{
        create AgentServerConnector(onOperationReceived, onReadEvent);
    }
    io:println("Starting agent...");
    agentServerConnector.initialize(5000, 30000);
}