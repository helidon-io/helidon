/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.Set;

import io.helidon.http.Status;

import jdk.jfr.Category;
import jdk.jfr.Contextual;
import jdk.jfr.Description;
import jdk.jfr.Enabled;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Threshold;

@Name("io.helidon.HttpServerRequest")
@Label("HTTP Server Request")
@Category({"Helidon", "WebServer"})
@Description("Synchronous routing and filters on the request thread; excludes transport delivery and child tasks")
@Enabled(true)
@Threshold("0 ms")
@StackTrace(false)
final class HttpServerRequestEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(HttpServerRequestEvent.class);
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "CONNECT",
                                                       "OPTIONS", "TRACE", "PATCH", "QUERY");

    @Label("Listener Socket ID")
    @Contextual
    private String serverSocketId;
    @Label("Connection Socket ID")
    @Contextual
    private String socketId;
    @Label("Request ID")
    @Contextual
    private int requestId;
    @Label("Request Name")
    @Description("Method and bounded configured route template, with <unknown> for unproven routes")
    @Contextual
    private String requestName;
    @Label("Method")
    private String method;
    @Label("Route Template")
    @Description("Bounded configured template of the last selected route, or absent when provenance is unknown")
    private String routeTemplate;
    @Label("Status Code")
    private int statusCode;
    @Label("Outcome")
    @Description("HTTP status class, or ESCAPED_FAILURE if routing did not return normally")
    private String outcome;

    static HttpServerRequestEvent start(ServerRequest request) {
        if (!TYPE.isEnabled()) {
            return null;
        }
        var event = new HttpServerRequestEvent();
        event.serverSocketId = request.serverSocketId();
        event.socketId = request.socketId();
        event.requestId = request.id();
        String method = request.prologue().method().text();
        event.method = METHODS.contains(method) ? method : "OTHER";
        event.begin();
        return event;
    }

    void routingStarted() {
        routeTemplate = null;
    }

    void routeSelected(RouteCrawler.CrawlerItem route) {
        routeTemplate = route.safeTemplate();
    }

    void handshakeSent(Status status) {
        statusCode = status.code();
    }

    void finish(ServerResponse response, boolean completed) {
        end();
        if (shouldCommit()) {
            requestName = method + " " + (routeTemplate == null ? "<unknown>" : routeTemplate);
            if (statusCode == 0) {
                statusCode = response.status().code();
            }
            outcome = completed ? switch (statusCode / 100) {
                case 1 -> "INFORMATIONAL";
                case 2 -> "SUCCESS";
                case 3 -> "REDIRECTION";
                case 4 -> "CLIENT_ERROR";
                case 5 -> "SERVER_ERROR";
                default -> "UNKNOWN";
            } : "ESCAPED_FAILURE";
            commit();
        }
    }
}
