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
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.common.context.Context;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.reactive.IoMulti;
import io.helidon.common.reactive.OutputStreamMulti;
import io.helidon.media.common.DataChunkInputStream;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

import com.sun.xml.ws.api.ha.HaInfo;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.server.PortAddressResolver;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.ws.resources.WsservletMessages;
import com.sun.xml.ws.transport.http.HttpAdapter;
import com.sun.xml.ws.transport.http.WSHTTPConnection;
import jakarta.xml.ws.WebServiceException;

class HelidonConnectionImpl extends WSHTTPConnection implements WebServiceContextDelegate {

    private final ServerRequest req;
    private final ServerResponse res;
    private final HttpAdapter adapter;
    private HaInfo haInfo;
    private static final PropertyMap MODEL;
    private static final Logger LOGGER = Logger.getLogger(HelidonConnectionImpl.class.getName());

    static {
        // 3.0.0 does not have this method
        MODEL = parse(HelidonConnectionImpl.class, MethodHandles.lookup());
    }

    HelidonConnectionImpl(HttpAdapter adapter, ServerRequest req, ServerResponse res) {
        this.adapter = adapter;
        this.req = req;
        this.res = res;
        // Must be set to 0 to set correct response code for one-way operations
        res.status(0);
    }

    @Override
    public WebServiceContextDelegate getWebServiceContextDelegate() {
        return this;
    }

    @Property(HelidonProperties.REQUEST)
    public ServerRequest getRequest() {
        return req;
    }

    @Override
    @Property(HelidonProperties.CONTEXT)
    public Context getContext() {
        return req.context();
    }

    @Property(HelidonProperties.RESPONSE)
    public ServerResponse getResponse() {
        return res;
    }

    @Override
    protected PropertyMap getPropertyMap() {
        return MODEL;
    }

    @Override
    public void setResponseHeaders(Map<String, List<String>> headers) {
        //we can't clear/removeAll response headers directly
        new ArrayList<>(res.headers().toMap().keySet()).forEach(res.headers()::remove);
        headers.entrySet().stream()
                .filter(e -> !(e.getKey().equalsIgnoreCase("Content-Type")
                || e.getKey().equalsIgnoreCase("Content-Length")))
                .forEach(e -> res.headers().add(e.getKey(), e.getValue()));
    }

    @Override
    public void setResponseHeader(String key, String value) {
        res.headers().put(key, value);
    }

    @Override
    public void setResponseHeader(String key, List<String> value) {
        res.headers().put(key, value);
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return res.headers().toMap();
    }

    @Override
    public void setContentTypeResponseHeader(String value) {
        res.headers().contentType(MediaType.parse(value));
    }

    @Override
    public void setContentLengthResponseHeader(int value) {
        res.headers().contentLength(value);
    }

    @Override
    public void setStatus(int status) {
        res.status(status);
    }

    @Override
    public int getStatus() {
        return res.status().code();
    }

    @Override
    public void setCookie(String name, String value) {
        res.headers().addCookie(name, value);
    }

    @Override
    public String getCookie(String name) {
        return req.headers().cookies().first(name).orElse(null);
    }

    @Override
    public InputStream getInput() throws IOException {
        return new DataChunkInputStream(req.content());
    }

    @Override
    public OutputStream getOutput() throws IOException {
        OutputStreamMulti os = IoMulti.outputStreamMulti();
        res.send(os.map(
                byteBuffer -> DataChunk.create(false, true, byteBuffer)));
        return os;
    }

    @Override
    public String getRequestMethod() {
        return req.method().name();
    }

    @Override
    public Map<String, List<String>> getRequestHeaders() {
        return req.headers().toMap();
    }

    @Override
    public Set<String> getRequestHeaderNames() {
        return getRequestHeaders().keySet();
    }

    @Override
    public String getRequestHeader(String name) {
        return req.headers().first(name).orElse(null);
    }

    @Override
    public List<String> getRequestHeaderValues(String header) {
        return header != null
                ? req.headers().values(header)
                : null;
    }

    @Override
    public String getQueryString() {
        return req.query();
    }

    @Override
    public String getPathInfo() {
        return req.path().param("pathinfo");
    }

    @Override
    public String getProtocol() {
        return req.version().value();
    }

    @Override
    @Property(HelidonProperties.REQUEST_URI)
    public String getRequestURI() {
        return req.uri().toString();
    }

    @Override
    public String getRequestScheme() {
        return isSecure()
                ? "https"
                : "http";
    }

    @Override
    public String getServerName() {
        return req.localAddress();
    }

    @Override
    public int getServerPort() {
        return req.localPort();
    }

    @Override
    public boolean isSecure() {
        return req.isSecure();
    }

    @Override
    public String getBaseAddress() {
        return WSUtils.getBaseAddress(req);
    }

    @Override
    public Principal getUserPrincipal() {
        return getUserPrincipal(null);
    }

    @Override
    public Principal getUserPrincipal(Packet request) {
        // Not supported
        LOGGER.warning("Calling unsupported method: getUserPrincipal");
        return null;
    }

    @Override
    public boolean isUserInRole(String role) {
        return isUserInRole(null, role);
    }

    @Override
    public boolean isUserInRole(Packet request, String role) {
        // Not supported
        LOGGER.warning("Calling unsupported method: isUserInRole");
        return false;
    }

    @Property(Packet.HA_INFO)
    public HaInfo getHaInfo() {
        if (haInfo == null) {
            Parameters cookies = req.headers().cookies();
            String replicaInstance = cookies.first("JREPLICA").orElse(null);
            String key = cookies.first("METRO_KEY").orElse(null);
            String jrouteId = cookies.first("JROUTE").orElse(null);
            if (replicaInstance != null && key != null) {
                String proxyJroute = getRequestHeader("proxy-jroute");
                boolean failOver = jrouteId != null && proxyJroute != null && !jrouteId.equals(proxyJroute);
                haInfo = new HaInfo(key, replicaInstance, failOver);
            }
        }
        return haInfo;
    }

    public void setHaInfo(HaInfo replicaInfo) {
        this.haInfo = replicaInfo;
    }

    @Override
    public String getEPRAddress(Packet request, WSEndpoint endpoint) {
        PortAddressResolver resolver = adapter.owner
                .createPortAddressResolver(getBaseAddress(), endpoint.getImplementationClass());
        String address = resolver.getAddressFor(endpoint.getServiceName(), endpoint.getPortName().getLocalPart());
        if (address == null) {
            throw new WebServiceException(WsservletMessages.SERVLET_NO_ADDRESS_AVAILABLE(endpoint.getPortName()));
        }
        return address;
    }

    @Override
    public String getWSDLAddress(Packet request, WSEndpoint endpoint) {
        String eprAddress = getEPRAddress(request, endpoint);
        return adapter.getEndpoint().getPort() != null
                ? eprAddress + "?wsdl"
                : null;
    }
}
