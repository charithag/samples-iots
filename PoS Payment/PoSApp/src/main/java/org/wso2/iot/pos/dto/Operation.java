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

import java.io.Serializable;

public class Operation implements Serializable {

    private Type type;
    private Status status;
    private String code;
    private int id;
    private String payload;

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public enum Type {
        CONFIG, MESSAGE, INFO, COMMAND, PROFILE, POLICY
    }

    public enum Status {
        IN_PROGRESS, PENDING, COMPLETED, ERROR, REPEATED
    }

}
