package org.wso2.iot.agent;

import ballerina.net.http;

@Description { value:"OAuth2 client connector"}
@Param { value:"baseUrl: The endpoint base url"}
@Param { value:"accessToken: The access token of the account"}
@Param { value:"clientId: The client Id of the account"}
@Param { value:"clientSecret: The client secret of the account"}
@Param { value:"refreshToken: The refresh token of the account"}
@Param { value:"refreshTokenEP: The refresh token endpoint url"}
public connector Oauth2ClientConnector (string baseUrl, string accessToken, string clientId, string clientSecret,
                           string refreshToken, string refreshTokenEP, function (string, string) onTokenRenewed) {

    endpoint<http:HttpClient> httpConnectorEP{
        create http:HttpClient(baseUrl,{});
    }

    string accessTokenValue;

    @Description { value:"Get with OAuth2 authentication"}
    @Param { value:"path: The endpoint path"}
    @Param { value:"request: The request of the method"}
    @Return { value:"response object"}
    action get (string path, http:OutRequest request) (http:InResponse) {
        http:InResponse response;

        accessTokenValue = constructAuthHeader (request, accessTokenValue, accessToken);
        response,_ = httpConnectorEP.get(path, request);

        if ((response.statusCode == 401) && (refreshToken != "" || refreshToken != "null")) {
            accessTokenValue = getAccessTokenFromRefreshToken(request, accessToken, clientId, clientSecret, refreshToken,
                                                              refreshTokenEP, onTokenRenewed);                                                 
            response,_ = httpConnectorEP.get(path, request);
        }

        return response;
    }

    @Description { value:"Post with OAuth2 authentication"}
    @Param { value:"path: The endpoint path"}
    @Param { value:"request: The request of the method"}
    @Return { value:"response object"}
    action post (string path, http:OutRequest request) (http:InResponse) {

        http:InResponse response;

        accessTokenValue = constructAuthHeader (request, accessTokenValue, accessToken);
        response,_ = httpConnectorEP.post(path, request);

        if ((response.statusCode == 401) && (refreshToken != "" || refreshToken != "null")) {
            accessTokenValue = getAccessTokenFromRefreshToken(request, accessToken, clientId, clientSecret, refreshToken,
                                                              refreshTokenEP, onTokenRenewed);
            response,_ = httpConnectorEP.post (path, request);
        }

        return response;
    }

    @Description { value:"Put with OAuth2 authentication"}
    @Param { value:"path: The endpoint path"}
    @Param { value:"request: The request of the method"}
    @Return { value:"response object"}
    action put (string path, http:OutRequest request) (http:InResponse) {

        http:InResponse response;

        accessTokenValue = constructAuthHeader (request, accessTokenValue, accessToken);
        response,_ = httpConnectorEP.put (path, request);

        if ((response.statusCode == 401) && (refreshToken != "" || refreshToken != "null")) {
            accessTokenValue = getAccessTokenFromRefreshToken(request, accessToken, clientId, clientSecret, refreshToken,
                                                              refreshTokenEP, onTokenRenewed);
            response,_ = httpConnectorEP.put (path, request);
        }

        return response;
    }

    @Description { value:"Delete with OAuth2 authentication"}
    @Param { value:"path: The endpoint path"}
    @Param { value:"request: The request of the method"}
    @Return { value:"response object"}
    action delete (string path, http:OutRequest request) (http:InResponse) {

        http:InResponse response;

        accessTokenValue = constructAuthHeader (request, accessTokenValue, accessToken);
        response,_ = httpConnectorEP.delete (path, request);

        if ((response.statusCode == 401) && (refreshToken != "" || refreshToken != "null")) {
            accessTokenValue = getAccessTokenFromRefreshToken(request, accessToken, clientId, clientSecret, refreshToken,
                                                              refreshTokenEP, onTokenRenewed);
            response,_ = httpConnectorEP.delete (path, request);
        }

        return response;
    }

    @Description { value:"Patch with OAuth2 authentication"}
    @Param { value:"path: The endpoint path"}
    @Param { value:"request: The request of the method"}
    @Return { value:"response object"}
    action patch (string path, http:OutRequest request) (http:InResponse) {

        http:InResponse response;

        accessTokenValue = constructAuthHeader (request, accessTokenValue, accessToken);
        response,_ = httpConnectorEP.patch (path, request);

        if ((response.statusCode == 401) && (refreshToken != "" || refreshToken != "null")) {
            accessTokenValue = getAccessTokenFromRefreshToken(request, accessToken, clientId, clientSecret, refreshToken,
                                                              refreshTokenEP, onTokenRenewed);
            response,_ = httpConnectorEP.patch (path, request);
        }

        return response;
    }
}

function constructAuthHeader (http:OutRequest request, string accessTokenValue, string accessToken) (string) {

    if (accessTokenValue == "") {
        accessTokenValue = accessToken;
    }

    request.setHeader("Authorization", "Bearer " + accessTokenValue);

    return accessTokenValue;
}

function getAccessTokenFromRefreshToken (http:OutRequest request, string accessToken, string clientId, string clientSecret,
                                         string refreshToken, string refreshTokenEP, function (string, string) onTokenRenewed) (string) {
    endpoint<http:HttpClient> refreshTokenHTTPEP{
        create http:HttpClient(refreshTokenEP,{});
    }
    http:OutRequest refreshTokenRequest = {};
    http:InResponse refreshTokenResponse;
    string accessTokenFromRefreshTokenReq;
    json accessTokenFromRefreshTokenJSONResponse;

    accessTokenFromRefreshTokenReq = "?refresh_token=" + refreshToken
                                     + "&grant_type=refresh_token&client_secret="
                                     + clientSecret + "&client_id=" + clientId;
    refreshTokenResponse,_ = refreshTokenHTTPEP.post(accessTokenFromRefreshTokenReq, refreshTokenRequest);

    accessTokenFromRefreshTokenJSONResponse = refreshTokenResponse.getJsonPayload();
    accessToken = accessTokenFromRefreshTokenJSONResponse.access_token.toString();
    refreshToken = accessTokenFromRefreshTokenJSONResponse.refresh_token.toString();
    request.setHeader("Authorization", "Bearer " + accessToken);
    onTokenRenewed(accessToken, refreshToken);
    return accessToken;
}