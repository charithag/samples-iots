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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.wso2.iot.pos.dto.AccessTokenInfo;
import org.wso2.iot.pos.dto.ApiApplicationKey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;

public class TokenHandler {

    private static final String APPLICATION_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final Log log = LogFactory.getLog(TokenHandler.class);

    private String tokenEndpoint;
    private AccessTokenInfo accessTokenInfo;
    private ApiApplicationKey apiApplicationKey;
    private TokenRenewCallback tokenRenewCallback;

    public TokenHandler(String tokenEndpoint, AccessTokenInfo accessTokenInfo, ApiApplicationKey apiApplicationKey,
                        TokenRenewCallback tokenRenewCallback) {
        this.tokenEndpoint = tokenEndpoint;
        this.accessTokenInfo = accessTokenInfo;
        this.apiApplicationKey = apiApplicationKey;
        this.tokenRenewCallback = tokenRenewCallback;
    }

    public AccessTokenInfo renewTokens() throws TokenRenewalException {
        String encodedClientApp = new String(Base64.encodeBase64((apiApplicationKey.getConsumerKey() + ":" +
                                                                  apiApplicationKey.getConsumerSecret()).getBytes()));
        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(this.tokenEndpoint);
            httpPost.setHeader("Authorization", "Basic " + encodedClientApp);
            httpPost.setHeader("Content-Type", APPLICATION_FORM_URLENCODED);

            StringEntity tokenEPPayload = new StringEntity(
                    "grant_type=refresh_token&refresh_token=8e957016-8493-3a22-8f98-e9e8571c00ae",
                    "UTF-8");
            httpPost.setEntity(tokenEPPayload);
            String tokenResult;

            HttpResponse response = client.execute(httpPost);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            tokenResult = result.toString();
            JSONParser jsonParser = new JSONParser();
            JSONObject jTokenResult = (JSONObject) jsonParser.parse(tokenResult);
            accessTokenInfo.setAccessToken(jTokenResult.get("access_token").toString());
            accessTokenInfo.setRefreshToken(jTokenResult.get("refresh_token").toString());
            tokenRenewCallback.onTokenRenewed(accessTokenInfo);
            log.info("Token renewed.");
            return accessTokenInfo;
        } catch (IOException | ParseException | URISyntaxException | HttpException e) {
            log.error("Cannot renew tokens due to " + e.getMessage(), e);
            throw new TokenRenewalException(e);
        }
    }


}
