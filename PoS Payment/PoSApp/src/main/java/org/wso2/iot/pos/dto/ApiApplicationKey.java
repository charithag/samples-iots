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

package org.wso2.iot.pos.dto;

/**
 * This holds api application consumer key and secret.
 */
public class ApiApplicationKey {
    private String clientId;
    private String clientSecret;

    public String getConsumerKey() {
        return this.clientId;
    }

    public void setClientId(String consumerKey) {
        this.clientId = consumerKey;
    }

    public String getConsumerSecret() {
        return this.clientSecret;
    }

    public void setClientSecret(String consumerSecret) {
        this.clientSecret = consumerSecret;
    }
}
