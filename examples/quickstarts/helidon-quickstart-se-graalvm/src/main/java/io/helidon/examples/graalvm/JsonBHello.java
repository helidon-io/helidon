/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.examples.graalvm;

/**
 * JSON-B compatible POJO.
 */
public class JsonBHello {
    private String parameterValue;
    private long time = System.currentTimeMillis();
    private String key = "value";

    public JsonBHello(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    public JsonBHello() {
    }

    public long getTime() {
        return time;
    }

    public String getKey() {
        return key;
    }

    public String getParameter() {
        return parameterValue;
    }

    public void setParameter(String parameterValue) {
        this.parameterValue = parameterValue;
    }

    public void setTime(Long time) {
        this.time = time;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
