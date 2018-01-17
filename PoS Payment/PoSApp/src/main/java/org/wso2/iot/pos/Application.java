//    WSO2 Agent Integration for uniCenta oPOS
//    Copyright (c) 2018 WSO2 Inc.
//    http://wso2.org
//
//    This file is part of WSO2 Agent Integration for uniCenta oPOS
//
//    WSO2 Integration for uniCenta oPOS is free software: you can
//    redistribute it and/or modify it under the terms of the GNU General
//    Public License as published by the Free Software Foundation, either
//    version 3 of the License, or (at your option) any later version.
//
//    WSO2 Integration for uniCenta oPOS is distributed in the hope that
//    it will be useful, but WITHOUT ANY WARRANTY; without even the implied
//    warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//    See the GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with uniCenta oPOS.  If not, see <http://www.gnu.org/licenses/>

package org.wso2.iot.pos;

import com.openbravo.pos.forms.JRootApp;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.iot.pos.dto.AccessTokenInfo;
import org.wso2.iot.pos.dto.ApiApplicationKey;
import org.wso2.iot.pos.ui.QRJFrame;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class Application {

    private static final Log log = LogFactory.getLog(Application.class);
    private static final String CONFIG_FILE = "config.json";

    private static Application application;
    private MQTTHandler mqttHandler;
    private TokenHandler tokenHandler;
    private JRootApp jRootApp;
    private JSONObject configData;
    private String mqttEndpoint;
    private String httpEndpoint;
    private String deviceId;
    private String deviceType;

    private Application() {
        jRootApp = JRootApp.getjRootApp();

        ApiApplicationKey apiApplicationKey = new ApiApplicationKey();
        AccessTokenInfo accessTokenInfo = new AccessTokenInfo();
        try {
            JSONParser parser = new JSONParser();
            configData = (JSONObject) parser.parse(new FileReader(CONFIG_FILE));
            apiApplicationKey.setClientId(configData.get("clientId").toString());
            apiApplicationKey.setClientSecret(configData.get("clientSecret").toString());
            accessTokenInfo.setAccessToken(configData.get("accessToken").toString());
            accessTokenInfo.setRefreshToken(configData.get("refreshToken").toString());
            mqttEndpoint = configData.get("mqttGateway").toString();
            httpEndpoint = configData.get("httpGateway").toString();
            deviceId = configData.get("deviceId").toString();
            deviceType = configData.get("type").toString();
        } catch (IOException | ParseException e) {
            log.error("Error occurred when reading device details from json file.", e);
        }

        tokenHandler = new TokenHandler(httpEndpoint + "/token", accessTokenInfo, apiApplicationKey, updatedTokenInfo -> {
            configData.put("accessToken", updatedTokenInfo.getAccessToken());
            configData.put("refreshToken", updatedTokenInfo.getRefreshToken());
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(CONFIG_FILE), "utf-8"))) {
                writer.write(configData.toJSONString());
                writer.close();
            } catch (IOException e) {
                log.error("Error occurred when writing device details to config json file.", e);
            }
        });
        mqttHandler = new MQTTHandler(mqttEndpoint, "carbon.super", deviceType, deviceId, tokenHandler);
        new QRJFrame(deviceId).setVisible(true);
    }

    public static void init() {
        application = new Application();
    }

    public static Application getInstance() {
        return application;
    }

    public MQTTHandler getMqttHandler() {
        return mqttHandler;
    }

    public JRootApp getjRootApp() {
        return jRootApp;
    }

    public TokenHandler getTokenHandler() {
        return tokenHandler;
    }
}
