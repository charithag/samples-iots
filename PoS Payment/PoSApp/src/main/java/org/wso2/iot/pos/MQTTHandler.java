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

import com.openbravo.basic.BasicException;
import com.openbravo.data.loader.Session;
import com.openbravo.pos.forms.DataLogicSales;
import com.openbravo.pos.sales.DataLogicReceipts;
import com.openbravo.pos.ticket.ProductInfoExt;
import com.openbravo.pos.ticket.TaxInfo;
import com.openbravo.pos.ticket.TicketInfo;
import com.openbravo.pos.ticket.TicketLineInfo;
import com.openbravo.pos.ticket.UserInfo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.wso2.iot.pos.dto.AccessTokenInfo;
import org.wso2.iot.pos.dto.Operation;
import org.wso2.iot.pos.util.PoSUtils;

import java.util.Properties;

public class MQTTHandler {

    private static final Log log = LogFactory.getLog(MQTTHandler.class);

    private static final int MQTT_QOS = 0;

    private MqttClient mqttClient = null;
    private boolean isClientConnected = false;
    private String tenant;
    private String type;
    private String deviceId;

    MQTTHandler(String brokerUrl, String tenant, String type, String deviceId, TokenHandler tokenHandler) {
        this.tenant = tenant;
        this.type = type;
        this.deviceId = deviceId;

        try {
            mqttClient = new MqttClient(brokerUrl, deviceId, null);
        } catch (MqttException e) {
            log.error("Error occurred when creating MQTT Client.", e);
        }

        connectWithBroker(tokenHandler);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) { //Called when the client lost the connection to the broker
                log.warn("Connection lost. Reattempting to connect.");
                isClientConnected = false;
                connectWithBroker(tokenHandler);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                log.info("Message arrived from : " + topic);

                String payload = new String(message.getPayload());
                log.info("Message: " + payload);
                handleIncomingMessage(topic, payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {//Called when a outgoing publish is complete
                log.info("Delivery completed. " + token);
            }
        });
    }

    private void connectWithBroker(TokenHandler tokenHandler) {
        Runnable connectionThread = () -> {
            MqttConnectOptions connOpts = new MqttConnectOptions();
            try {
                AccessTokenInfo accessTokenInfo = tokenHandler.renewTokens();
                connOpts.setUserName(accessTokenInfo.getAccessToken());
            } catch (TokenRenewalException e) {
                log.error("Error occurred while renewing tokens.", e);
                return;
            }
            connOpts.setPassword("".toCharArray());
            connOpts.setKeepAliveInterval(120);
            connOpts.setCleanSession(true);
            while (!isClientConnected) {
                try {
                    mqttClient.connect(connOpts);
                } catch (MqttException e) {
                    log.error("Error occurred when connecting with MQTT Server.", e);
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    break;
                }
                isClientConnected = mqttClient.isConnected();
            }
            String operationTopic = tenant + "/" + type + "/" + deviceId + "/operation/#";
            try {
                mqttClient.subscribe(operationTopic, MQTT_QOS);
            } catch (Exception e) {
                log.error("Error occurred when subscribing with Operation topic '" + operationTopic + "'.", e);
            }
            log.info("MQTT client connected and subscribed for operation topic: " + operationTopic);
        };
        new Thread(connectionThread).start();
    }

    private void handleIncomingMessage(String topic, String message){
        Runnable messageHandler = () -> {
            String operationSpecifier = topic.replace(tenant + "/" + type + "/" + deviceId + "/operation/", "");
            String[] operationParams = operationSpecifier.split("/");

            Operation operation = new Operation();
            String operationResponse = "";
            try {
                operation.setId(Integer.parseInt(operationParams[2]));
                operation.setType(Operation.Type.valueOf(operationParams[0].toUpperCase()));
                operation.setCode(operationParams[1]);
                operation.setPayload(message);

                JSONObject checkoutRequest = new JSONObject(operation.getPayload());
                String mobileId = checkoutRequest.getString("mobile_id");
                TicketInfo ticket = addTicket(mobileId, checkoutRequest.getString("items").split(","));
                operation.setStatus(Operation.Status.COMPLETED);
                JSONObject ticketObj = new JSONObject();
                ticketObj.put("mobile_id", mobileId);
                ticketObj.put("bill_id", ticket.getId());
                ticketObj.put("sub_total", PoSUtils.round(ticket.getSubTotal(), 2));
                ticketObj.put("tax", PoSUtils.round(ticket.getTax(), 2));
                ticketObj.put("bill_total", PoSUtils.round(ticket.getTotal(), 2));
                operationResponse = ticketObj.toString();
                publishMessage(tenant + "/" + type + "/" + deviceId + "/events", operationResponse);
            } catch (Exception e) {
                operation.setStatus(Operation.Status.ERROR);
                operationResponse = "Exception: " + e.getMessage();
                log.error(operationResponse, e);
            } finally {
                JSONObject responseObj = new JSONObject();
                responseObj.put("id", operation.getId());
                responseObj.put("status", operation.getStatus());
                responseObj.put("operationResponse", operationResponse);
                publishMessage(tenant + "/" + type + "/" + deviceId + "/update/operation", responseObj.toString());
            }
        };
        new Thread(messageHandler).start();
    }

    private TicketInfo addTicket(String mobileId, String[] items) throws BasicException {
        String response;
        Session session = Application.getInstance().getjRootApp().getSession();

        DataLogicSales dataLogicSales = new DataLogicSales();
        dataLogicSales.init(session);

        TaxInfo taxInfo = new TaxInfo("001", "Tax Standard", "001", null, null,
                                      0.1, false, null);
        TicketInfo ticketInfo = new TicketInfo();
        ticketInfo.setUser(new UserInfo("3", "Guest"));
        ticketInfo.setProperty("mobile_id", mobileId);
        ticketInfo.setProperty("initial_id", ticketInfo.getId());

        for (String reference : items) {
            try {
                ProductInfoExt productInfoExt = dataLogicSales.getProductInfoByReference(reference);
                if (productInfoExt == null) {
                    log.warn("Product not exists for reference: " + reference);
                    continue;
                }
                TicketLineInfo ticketLineInfo = new TicketLineInfo(productInfoExt, 1.0,
                                                                   productInfoExt.getPriceSell(), taxInfo,
                                                                   (Properties) productInfoExt.getProperties().clone());
                ticketInfo.addLine(ticketLineInfo);
            } catch (BasicException e) {
                log.error("Error occurred while getting product details of " + reference, e);
            }
        }
        if (ticketInfo.getLinesCount() > 0) {
            DataLogicReceipts dataLogicReceipts = new DataLogicReceipts();
            dataLogicReceipts.init(session);
            try {
                dataLogicReceipts.insertSharedTicket(ticketInfo.getId(), ticketInfo, 0);
                return ticketInfo;
            } catch (BasicException e) {
                response = "Error occurred while adding shared ticket.";
                log.error(response, e);
                throw new BasicException(response, e);
            }
        } else {
            response = "Ignoring. Nothing to add to ticket.";
            log.warn(response);
            throw new BasicException(response);
        }
    }

    public void publishMessage(String topic, String payload) {
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setPayload(payload.getBytes());
        mqttMessage.setQos(MQTT_QOS);
        mqttMessage.setRetained(false);
        try {
            mqttClient.publish(topic, mqttMessage);
        } catch (MqttException e) {
            log.error("Error occurred when publishing message.", e);
        }
        log.info("Published to topic: " + topic + "\nPayload: " + payload);
    }
}
