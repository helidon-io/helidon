/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.soap.ws;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.webserver.HttpException;
import io.helidon.webserver.RequestHeaders;
import io.helidon.webserver.ServerRequest;

import com.sun.xml.ws.api.server.BoundEndpoint;
import com.sun.xml.ws.resources.WsservletMessages;

class WSUtils {

    private WSUtils() {
    }

    /*
     * Generates the listing of all services.
     */
    static CharSequence writeWebServicesHtmlPage(URI baseUri, List<BoundEndpoint> endpoints) {
        // standard browsable page
        StringBuilder out = new StringBuilder();
        out.append("<html>");
        out.append("<head><title>");
        // out.println("Web Services");
        out.append(WsservletMessages.SERVLET_HTML_TITLE());
        out.append("</title></head>");
        out.append("<body>");
        // out.println("<h1>Web Services</h1>");
        out.append(WsservletMessages.SERVLET_HTML_TITLE_2());

        if (endpoints.isEmpty()) {
            // out.println("<p>No JAX-WS context information available.</p>");
            out.append(WsservletMessages.SERVLET_HTML_NO_INFO_AVAILABLE());
        } else {
            out.append("<table width='100%' border='1'>");
            out.append("<tr>");
            out.append("<td>");
            // out.println("Endpoint");
            out.append(WsservletMessages.SERVLET_HTML_COLUMN_HEADER_PORT_NAME());
            out.append("</td>");

            out.append("<td>");
            // out.println("Information");
            out.append(WsservletMessages.SERVLET_HTML_COLUMN_HEADER_INFORMATION());
            out.append("</td>");
            out.append("</tr>");

            for (BoundEndpoint a : endpoints) {
                String endpointAddress = a.getAddress(baseUri.toString()).toString();
                out.append("<tr>");

                out.append("<td>");
                out.append(WsservletMessages.SERVLET_HTML_ENDPOINT_TABLE(
                        a.getEndpoint().getServiceName(),
                        a.getEndpoint().getPortName()
                ));
                out.append("</td>");

                out.append("<td>");
                out.append(WsservletMessages.SERVLET_HTML_INFORMATION_TABLE(
                        endpointAddress,
                        a.getEndpoint().getImplementationClass().getName()
                ));
                out.append("</td>");

                out.append("</tr>");
            }
            out.append("</table>");
        }
        out.append("</body>");
        out.append("</html>");
        return out;
    }

    static URI getBaseUri(ServerRequest req) {
        try {
            return new URI(getBaseAddress(req));
        } catch (URISyntaxException e) {
            throw new HttpException("Unable to parse request URL", Http.Status.BAD_REQUEST_400, e);
        }
    }

    static String getBaseAddress(ServerRequest req) {
        /* Computes the Endpoint's address from the request.
         * Uses "X-Forwarded-Proto", "X-Forwarded-Host" and "Host" headers
         * so that it has correct address(IP address or someother hostname)
         * through which the application reached the endpoint.
         */
        StringBuilder buf = new StringBuilder();
        RequestHeaders headers = req.headers();
        String protocol = headers.first("X-Forwarded-Proto")
                .orElse(req.isSecure() ? "https" : "http");

        // An X-Forwarded-Host header would mean we are behind a reverse
        // proxy. Use it as host address if found, or the Host header
        // otherwise.
        String host = headers.first("X-Forwarded-Host")
                .orElse(headers.first("Host")
                        // Fallback
                        .orElse(req.localAddress() + ":" + req.localPort()));

        buf.append(protocol);
        buf.append("://");
        buf.append(host);
        buf.append(basePath(req.path()));

        return buf.toString();
    }

    private static String basePath(HttpRequest.Path path) {
        String reqPath = path.toString();
        String absPath = path.absolute().toString();
        String basePath = absPath.substring(0, absPath.length() - reqPath.length() + 1);
        return basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
    }

}
