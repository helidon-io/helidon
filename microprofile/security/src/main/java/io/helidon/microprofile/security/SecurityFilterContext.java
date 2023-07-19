/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

    String getResourceName() {
        return resourceName;
    }

    void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    String getResourcePath() {
        return resourcePath;
    }

    void setResourcePath(String resourcePath) {
        this.resourcePath = resourcePath;
    }

    String getMethod() {
        return method;
    }

    void setMethod(String method) {
        this.method = method;
    }

    Map<String, List<String>> getHeaders() {
        return headers;
    }

    void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    URI getTargetUri() {
        return targetUri;
    }

    void setTargetUri(URI targetUri) {
        this.targetUri = targetUri;
    }

    ContainerRequest getJerseyRequest() {
        return jerseyRequest;
    }

    void setJerseyRequest(ContainerRequest jerseyRequest) {
        this.jerseyRequest = jerseyRequest;
    }

    boolean isShouldFinish() {
        return shouldFinish;
    }

    void setShouldFinish(boolean shouldFinish) {
        this.shouldFinish = shouldFinish;
    }

    SecurityDefinition getMethodSecurity() {
        return methodSecurity;
    }

    void setMethodSecurity(SecurityDefinition methodSecurity) {
        this.methodSecurity = methodSecurity;
    }

    boolean isExplicitAtz() {
        return explicitAtz;
    }

    void setExplicitAtz(boolean explicitAtz) {
        this.explicitAtz = explicitAtz;
    }

    boolean isTraceSuccess() {
        return traceSuccess;
    }

    void setTraceSuccess(boolean traceSuccess) {
        this.traceSuccess = traceSuccess;
    }

    String getTraceDescription() {
        return traceDescription;
    }

    void setTraceDescription(String traceDescription) {
        this.traceDescription = traceDescription;
    }

    Throwable getTraceThrowable() {
        return traceThrowable;
    }

    void setTraceThrowable(Throwable traceThrowable) {
        this.traceThrowable = traceThrowable;
    }

    UriQuery getQueryParams() {
        return queryParams;
    }

    void setQueryParams(UriQuery queryParams) {
        this.queryParams = queryParams;
    }

    void clearTrace() {
        setTraceSuccess(true);
        setTraceDescription(null);
        setTraceThrowable(null);
    }
}
