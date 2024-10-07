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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.MultiFromOutputStream;
import io.helidon.webserver.PathMatcher;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.BoundEndpoint;
import com.sun.xml.ws.api.server.DocumentAddressResolver;
import com.sun.xml.ws.api.server.PortAddressResolver;
import com.sun.xml.ws.api.server.SDDocument;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WebModule;
import com.sun.xml.ws.transport.http.HttpAdapter;
import com.sun.xml.ws.transport.http.HttpAdapterList;
import com.sun.xml.ws.transport.http.WSHTTPConnection;
import jakarta.xml.ws.WebServiceException;

class HelidonAdapter extends HttpAdapter implements BoundEndpoint {

    private final String name;
    private static final Logger LOGGER = Logger.getLogger(HelidonAdapter.class.getName());
    private final PathMatcher serviceContext;

    HelidonAdapter(String name, WSEndpoint endpoint, HttpAdapterList<? extends HttpAdapter> owner, String urlPattern) {
        super(endpoint, owner, urlPattern);
        this.name = name;
        if (urlPattern.endsWith("/*")) {
            // ie Provider based endpoint with XML/HTTP binding (aka REST)
            serviceContext = PathMatcher.create(getValidPath() + "[{pathinfo:/.+}]");
        } else {
            serviceContext = PathMatcher.create(urlPattern);
        }
    }

    String getName() {
        return name;
    }

    PathMatcher getServiceContextPath() {
        return serviceContext;
    }

    void handle(ServerRequest req, ServerResponse res) {
        HelidonConnectionImpl con = new HelidonConnectionImpl(this, req, res);

        try {
            invokeAsync(con);
//            handle(con);
//            res.send(con.getOutput());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    protected void addSatellites(Packet packet) {
        ServerRequest req = (ServerRequest) packet.get(HelidonProperties.REQUEST);
        ServerResponse res = (ServerResponse) packet.get(HelidonProperties.RESPONSE);
        packet.addSatellite(new ConnectionProperties(req, res));
    }

    private boolean isMetadataQuery(String query) {
        // we intentionally return true even if documents don't exist,
        // so that they get 404.
        return query != null && (query.equals("WSDL") || query.startsWith("wsdl") || query.startsWith("xsd="));
    }

    boolean xhandleGet(WSHTTPConnection connection) throws IOException {
        if (connection.getRequestMethod().equals("GET")) {
            // metadata query. let the interceptor run
//            for (Component c : endpoint.getComponents()) {
//                HttpMetadataPublisher spi = c.getSPI(HttpMetadataPublisher.class);
//                if (spi != null && spi.handleMetadataRequest(this, connection)) {
//                    return true;
//                } // handled
//            }

            if (isMetadataQuery(connection.getQueryString())) {
                // Sends published WSDL and schema documents as the default action.
                publishWSDL(connection);
                return true;
            }

//            Binding binding = getEndpoint().getBinding();
//            if (!(binding instanceof HTTPBinding)) {
//                // Writes HTML page with all the endpoint descriptions
//                writeWebServicesHtmlPage(connection);
//                return true;
//            }
        } else if (connection.getRequestMethod().equals("HEAD")) {
//            connection.getInput().close();
//            Binding binding = getEndpoint().getBinding();
//            if (isMetadataQuery(connection.getQueryString())) {
//                SDDocument doc = wsdls.get(connection.getQueryString());
//                connection.setStatus(doc != null
//                        ? HttpURLConnection.HTTP_OK
//                        : HttpURLConnection.HTTP_NOT_FOUND);
//                connection.getOutput().close();
//                connection.close();
//                return true;
//            } else if (!(binding instanceof HTTPBinding)) {
//                connection.setStatus(HttpURLConnection.HTTP_NOT_FOUND);
//                connection.getOutput().close();
//                connection.close();
//                return true;
//            }
            // Let the endpoint handle for HTTPBinding
        }

        return false;

    }

    void publishWSDLX(WSHTTPConnection c) throws IOException {
//                                res.status(Http.Status.OK_200);
//                        res.headers().add("Content-Type", Collections.singletonList("text/xml;charset=utf-8"));
//                        res.send();

        HelidonConnectionImpl con = (HelidonConnectionImpl) c;
//        con.getInput().close();

        SDDocument doc = wsdls.get(con.getQueryString());
        if (doc == null) {
//            writeNotFoundErrorPage(con,"Invalid Request");
            return;
        }

//        con.req.
        ServerResponse res = con.getResponse();
        res.status(Http.Status.OK_200);
        res.headers().add("Content-Type", Collections.singletonList("text/xml;charset=utf-8"));
//        res.send(con.getOutput(), OutputStream.class);
//        res.send("<s>hello</s>");

//        con.setStatus(HttpURLConnection.HTTP_OK);
//        con.setContentTypeResponseHeader("text/xml;charset=utf-8");
//
////        OutputStream os = con.getProtocol().contains("1.1") ? con.getOutput() : new Http10OutputStream(con);
////con.res.
//        OutputStream os = con.getOutput();
//        OutputStream os = new ByteArrayOutputStream();
        MultiFromOutputStream os = IoMulti.outputStreamMulti();

        PortAddressResolver portAddressResolver = getPortAddressResolver(con.getBaseAddress());
        DocumentAddressResolver resolver = getDocumentAddressResolver(portAddressResolver);
        res.send(os
                .map(byteBuffer -> DataChunk.create(false, true, byteBuffer)));

        doc.writeTo(portAddressResolver, resolver, os);
//        con.res.send(() -> {
//        });
        os.close();

    }

    @Override
    public URI getAddress() {
        WebModule webModule = endpoint.getContainer().getSPI(WebModule.class);
        if (webModule == null) {
            throw new WebServiceException("Container " + endpoint.getContainer() + " doesn't support " + WebModule.class);
        }
        return getAddress(webModule.getContextPath());
    }

    @Override
    public URI getAddress(String base) {
        try {
            return new URI((base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + getValidPath());
        } catch (URISyntaxException ex) {
            // this is really a bug in the container implementation
            throw new WebServiceException("Unable to compute address for " + endpoint, ex);
        }
    }

}
