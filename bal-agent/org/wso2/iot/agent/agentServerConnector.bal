package org.wso2.iot.agent;
import ballerina.net.http;
import ballerina.file;
import ballerina.runtime;

json configJson;
string configFilePath = "./config.json";

function (string, string) onTokenRenewed =
            function (string accessToken, string refreshToken) {
                configJson["accessToken"] = accessToken;
                configJson["refreshToken"] = refreshToken;
                file:File configFile = {path:configFilePath};
                configFile.delete();
                _,_,_ = configFile.createNewFile();
                io:CharacterChannel configFileChannel = getFileCharacterChannel(configFilePath,"w","UTF-8");
                _ = configFileChannel.writeCharacters(configJson.toString(), 0);
                configFileChannel.closeCharacterChannel();
                io:println("Token renewed: " + configJson.toString());
            };

@Description {
    value:"IoT Server connector for Agent"
}
public connector AgentServerConnector (function (json) onOperationReceived, function () returns (json) onReadEvent) {

    boolean isInitialized = false;

    @Description {
        value:"Initialize the Agent server connector"
    }
    @Param {
        value:"pollingInterval: Interval to poll operations from IoT Server in milliseconds"
    }
    action initialize (int pollingInterval, int publishingInterval) {
        worker initializer {
            io:CharacterChannel configFileChannel = getFileCharacterChannel(configFilePath,"r","UTF-8");
            configJson,_ = <json>configFileChannel.readAllCharacters();
            configFileChannel.closeCharacterChannel();
            io:println("Agent configuration loaded.");
            isInitialized = true;
        }
        worker localNotifier {
            while (!isInitialized) {
                runtime:sleepCurrentWorker(1000);
            }
            io:println("starting local notifier");
            while (true) {
                runtime:sleepCurrentWorker(pollingInterval);
                _ = getOperations(onOperationReceived);
            }
        }
        worker eventPublisher {
            while (!isInitialized) {
                runtime:sleepCurrentWorker(1000);
            }
            io:println("starting event publisher");
            while (true) {
                _ = publishEvent(onReadEvent());
                runtime:sleepCurrentWorker(publishingInterval);
            }
        }
    }

    @Description {
        value:"Send operation response to IoT Server"
    }
    @Param {
        value:"operation: Updated operation"
    }
    action updateOperation(json operation) {
        _ = updateOperation(operation);
    }
}

function getFileCharacterChannel(string filePath,string permission,string encoding) (io:CharacterChannel) {
    io:ByteChannel channel = io:openFile(filePath,permission);
    io:CharacterChannel characterChannel = io:createCharacterChannel(channel,encoding);
    return characterChannel;
}

function publishEvent (json payload) (error publishingError) {
    endpoint<Oauth2ClientConnector> eventPublisher{
        create Oauth2ClientConnector(configJson["httpGateway"].toString(), configJson["accessToken"].toString(), 
            configJson["clientId"].toString(), configJson["clientSecret"].toString(), configJson["refreshToken"].toString(), 
            configJson["httpGateway"].toString() + "/token", onTokenRenewed);
    }

    http:OutRequest req = {};
    req.setJsonPayload(payload);
    http:InResponse response;

    try {
        response = eventPublisher.post("/api/device-mgt/v1.0/device/agent/events/publish/" + 
            configJson["type"].toString() + "/" + configJson["deviceId"].toString(), req);
        publishingError = null;   
    } catch (error err) {
        io:println("error occured: " + err.message);
        publishingError = err;
    }
    return;
}

function getOperations(function (json) onOperationReceived)(error operationError){
    endpoint<Oauth2ClientConnector> localNotifierClient{
        create Oauth2ClientConnector(configJson["httpGateway"].toString(), configJson["accessToken"].toString(), 
        configJson["clientId"].toString(), configJson["clientSecret"].toString(), configJson["refreshToken"].toString(), 
        configJson["httpGateway"].toString() + "/token", onTokenRenewed);
    }
    
    http:OutRequest operationRequest = {};
    http:InResponse operationResponse;

    try {
        operationResponse = localNotifierClient.get("/api/device-mgt/v1.0/device/agent/next-pending/operation/" + 
        configJson["type"].toString() + "/" + configJson["deviceId"].toString(), operationRequest);
        operationError = null;
        json operation = null;
        try {
            operation = operationResponse.getJsonPayload();
        } catch (error ignore) {
        }
        if (operation != null) {
            operation["status"] = "IN_PROGRESS";
            operationError = updateOperation(operation);
            onOperationReceived(operation);   
        }
    } catch (error err) {
        io:println("error occured: " + err.message);
        operationError = err;
    }
    return;
}   

function updateOperation(json operation)(error operationError){ 
    endpoint<Oauth2ClientConnector> operationClient{
        create Oauth2ClientConnector(configJson["httpGateway"].toString(), configJson["accessToken"].toString(), 
        configJson["clientId"].toString(), configJson["clientSecret"].toString(), configJson["refreshToken"].toString(), 
        configJson["httpGateway"].toString() + "/token", onTokenRenewed);
    }
    
    http:OutRequest operationUpdateRequest = {};
    operationUpdateRequest.setJsonPayload(operation);
    http:InResponse operationUpdateResponse;

    try {
        operationUpdateResponse = operationClient.put("/api/device-mgt/v1.0/device/agent/operations/" + 
        configJson["type"].toString() + "/" + configJson["deviceId"].toString(), operationUpdateRequest);
        operationError = null;
        io:print("Operation update response: ");
        io:println(operationUpdateResponse);
    } catch (error err) {
        io:println("error occured: " + err.message);
        operationError = err;
    }
    return;
}    