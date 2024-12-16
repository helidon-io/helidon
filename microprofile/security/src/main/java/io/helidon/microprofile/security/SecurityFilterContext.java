/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.microprofile.security;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.helidon.common.uri.UriQuery;

import org.glassfish.jersey.server.ContainerRequest;

/**
 * Security filter context.
 */
public class SecurityFilterContext {
    private String resourceName;
    private String fullResourceName;
    private String resourceMethod;
    private String fullResourceMethod;
    private String resourcePath;
    private String method;
    private Map<String, List<String>> headers;
    private URI targetUri;
    private ContainerRequest jerseyRequest;
    private boolean shouldFinish;
    private SecurityDefinition methodSecurity;
    private boolean explicitAtz;

    // tracing support
    private boolean traceSuccess = true;
    private String traceDescription;
    private Throwable traceThrowable;
    private UriQuery queryParams;

    @Override
    public String toString() {
        return "SecurityFilterContext{"
                + "resourceName='" + resourceName + '\''
                + ", resourcePath='" + resourcePath + '\''
                + ", method='" + method + '\''
                + ", headers=" + headers
                + ", targetUri=" + targetUri
                + ", shouldFinish=" + shouldFinish
                + ", methodSecurity=" + methodSecurity
                + ", explicitAtz=" + explicitAtz
                + ", traceSuccess=" + traceSuccess
                + ", traceDescription='" + traceDescription + '\''
                + ", queryParams=" + queryParams
                + '}';
    }

    String resourceName() {
        return resourceName;
    }

    void resourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    String fullResourceName() {
        return fullResourceName;
    }

    void fullResourceName(String fullResourceName) {
        this.fullResourceName = fullResourceName;
    }

    String resourceMethod() {
        return resourceMethod;
    }

    void resourceMethod(String resourceMethod) {
        this.resourceMethod = resourceMethod;
    }

    String fullResourceMethod() {
        return fullResourceMethod;
    }

    void fullResourceMethod(String fullResourceMethod) {
        this.fullResourceMethod = fullResourceMethod;
    }

    String resourcePath() {
        return resourcePath;
    }

    void resourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    String method() {
        return method;
    }

    void method(String method) {
        this.method = method;
    }

    Map<String, List<String>> headers() {
        return headers;
    }

    void headers(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    URI targetUri() {
        return targetUri;
    }

    void targetUri(URI targetUri) {
        this.targetUri = targetUri;
    }

    ContainerRequest jerseyRequest() {
        return jerseyRequest;
    }

    void jerseyRequest(ContainerRequest jerseyRequest) {
        this.jerseyRequest = jerseyRequest;
    }

    boolean shouldFinish() {
        return shouldFinish;
    }

    void shouldFinish(boolean shouldFinish) {
        this.shouldFinish = shouldFinish;
    }

    SecurityDefinition methodSecurity() {
        return methodSecurity;
    }

    void methodSecurity(SecurityDefinition methodSecurity) {
        this.methodSecurity = methodSecurity;
    }

    boolean explicitAtz() {
        return explicitAtz;
    }

    void explicitAtz(boolean explicitAtz) {
        this.explicitAtz = explicitAtz;
    }

    boolean traceSuccess() {
        return traceSuccess;
    }

    void traceSuccess(boolean traceSuccess) {
        this.traceSuccess = traceSuccess;
    }

    String traceDescription() {
        return traceDescription;
    }

    void traceDescription(String traceDescription) {
        this.traceDescription = traceDescription;
    }

    Throwable traceThrowable() {
        return traceThrowable;
    }

    void traceThrowable(Throwable traceThrowable) {
        this.traceThrowable = traceThrowable;
    }

    UriQuery queryParams() {
        return queryParams;
    }

    void queryParams(UriQuery queryParams) {
        this.queryParams = queryParams;
    }

    void clearTrace() {
        traceSuccess(true);
        traceDescription(null);
        traceThrowable(null);
    }
}
