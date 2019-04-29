/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.integration.grpc;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.server.test.EchoServiceGrpc;
import io.helidon.security.EndpointConfig;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.SecurityEnvironment;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServiceDescriptor;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@SuppressWarnings("unchecked")
public class GrpcSecurityTest {
    @Test
    public void shouldRegisterSecurityContext() {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();


        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build());
        Context context = security.registerContext(call, headers);
        assertThat(context, is(notNullValue()));


        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(notNullValue()));

        SecurityEnvironment environment = securityContext.env();
        assertThat(environment, is(notNullValue()));
    }

    @Test
    public void shouldAddAttributesToSecurityContext() {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();


        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build());
        Context context = security.registerContext(call, headers);
        assertThat(context, is(notNullValue()));

        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(notNullValue()));

        SecurityEnvironment environment = securityContext.env();
        assertThat(environment, is(notNullValue()));
        assertThat(environment.method(), is(descriptor.getFullMethodName()));
        assertThat(environment.path().get(), is(descriptor.getFullMethodName()));
        assertThat(environment.transport(), is("grpc"));
        assertThat(environment.abacAttribute(GrpcSecurity.ABAC_ATTRIBUTE_REMOTE_ADDRESS).get(), is("helidon.io"));
        assertThat(environment.abacAttribute(GrpcSecurity.ABAC_ATTRIBUTE_REMOTE_PORT).get(), is(8080));
        assertThat(environment.abacAttribute(GrpcSecurity.ABAC_ATTRIBUTE_HEADERS).get(), is(sameInstance(headers)));
        assertThat(environment.abacAttribute(GrpcSecurity.ABAC_ATTRIBUTE_METHOD).get(), is(sameInstance(descriptor)));

    }

    @Test
    public void shouldAddHeadersToSecurityContext() {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();

        headers.put(Metadata.Key.of("key-1", Metadata.ASCII_STRING_MARSHALLER), "value-1.1");
        headers.put(Metadata.Key.of("key-1", Metadata.ASCII_STRING_MARSHALLER), "value-1.2");
        headers.put(Metadata.Key.of("key-2", Metadata.ASCII_STRING_MARSHALLER), "value-2");

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build());
        Context context = security.registerContext(call, headers);
        assertThat(context, is(notNullValue()));

        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(notNullValue()));

        SecurityEnvironment environment = securityContext.env();
        assertThat(environment, is(notNullValue()));

        Map<String, List<String>> expectedHeaders = new HashMap<>();
        Map<String, List<String>> securityHeaders = environment.headers();

        expectedHeaders.put("key-1", Arrays.asList("value-1.1", "value-1.2"));
        expectedHeaders.put("key-2", Collections.singletonList("value-2"));
        
        assertThat(securityHeaders, is(notNullValue()));
        assertThat(securityHeaders, is(expectedHeaders));
    }

    @Test
    public void shouldAddExtraHeadersToSecurityContext() throws Exception {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();

        Map extraHeaders = new HashMap();
        extraHeaders.put("key-1", Collections.singletonList("value-1"));
        extraHeaders.put("key-2", Collections.singletonList("value-2"));

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build());
        Context contextCurrent = Context.current().withValue(GrpcSecurity.CONTEXT_ADD_HEADERS, extraHeaders);
        Context context = contextCurrent.call(() -> security.registerContext(call, headers));
        assertThat(context, is(notNullValue()));

        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(notNullValue()));

        SecurityEnvironment environment = securityContext.env();
        assertThat(environment, is(notNullValue()));

        Map<String, List<String>> expectedHeaders = new HashMap<>();
        Map<String, List<String>> securityHeaders = environment.headers();

        expectedHeaders.put("key-1", Collections.singletonList("value-1"));
        expectedHeaders.put("key-2", Collections.singletonList("value-2"));

        assertThat(securityHeaders, is(notNullValue()));
        assertThat(securityHeaders, is(expectedHeaders));
    }

    @Test
    public void shouldAddConfigToSecurityContext() {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();

        headers.put(Metadata.Key.of("key-1", Metadata.ASCII_STRING_MARSHALLER), "value-1.1");
        headers.put(Metadata.Key.of("key-1", Metadata.ASCII_STRING_MARSHALLER), "value-1.2");
        headers.put(Metadata.Key.of("key-2", Metadata.ASCII_STRING_MARSHALLER), "value-2");

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        Config config = Config.builder()
                .sources(ConfigSources.classpath("secure-services.conf"))
                .build()
                .get("security");

        GrpcSecurity security = GrpcSecurity.create(Security.builder(config).build(), config);
        Context context = security.registerContext(call, headers);
        assertThat(context, is(notNullValue()));

        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(notNullValue()));

        EndpointConfig endpointConfig = securityContext.endpointConfig();
        assertThat(endpointConfig, is(notNullValue()));

        endpointConfig.config("foo");
    }

    @Test
    public void shouldUseExistingSecurityContext() throws Exception {
        MethodDescriptor<String, String> descriptor = getEchoMethod();
        ServerCall<String, String> call = mock(ServerCall.class);
        Metadata headers = new Metadata();
        SocketAddress address = new InetSocketAddress("helidon.io", 8080);
        Attributes attributes = Attributes.newBuilder()
                                          .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, address)
                                          .build();

        Map extraHeaders = new HashMap();
        extraHeaders.put("key-1", Collections.singletonList("value-1"));
        extraHeaders.put("key-2", Collections.singletonList("value-2"));

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);

        Security security = Security.builder().build();
        SecurityContext securityContextCurrent = security.createContext("foo");
        GrpcSecurity grpcSecurity = GrpcSecurity.create(security);
        Context contextCurrent = Context.current().withValue(GrpcSecurity.SECURITY_CONTEXT, securityContextCurrent);
        Context context = contextCurrent.call(() -> grpcSecurity.registerContext(call, headers));

        assertThat(context, is(notNullValue()));

        SecurityContext securityContext = GrpcSecurity.SECURITY_CONTEXT.get(context);
        assertThat(securityContext, is(sameInstance(securityContextCurrent)));
    }

    @Test
    public void shouldCallDefaultHandler() {
        MethodDescriptor<String, String> descriptor= getEchoMethod();
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ServerCall<String, String> call = mock(ServerCall.class);
        ListenerStub listener = new ListenerStub();
        Attributes attributes = Attributes.EMPTY;
        Metadata headers = new Metadata();

        GrpcSecurityHandler defaultHandler = mock(GrpcSecurityHandler.class);

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(defaultHandler.handleSecurity(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class))).thenReturn(listener);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build()).securityDefaults(defaultHandler);

        ServerCall.Listener<String> result = security.interceptCall(call, headers, next);

        verify(defaultHandler).handleSecurity(call, headers, next);
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void shouldCallSpecificHandler() throws Exception {
        Metadata headers = new Metadata();
        ServerCall<String, String> call = mock(ServerCall.class);
        ServerCallHandler<String, String> next = mock(ServerCallHandler.class);
        ListenerStub listener = new ListenerStub();
        Attributes attributes = Attributes.EMPTY;
        ServiceDescriptor serviceDescriptor = EchoServiceGrpc.getServiceDescriptor();
        MethodDescriptor<String, String> descriptor
                = (MethodDescriptor<String, String>) serviceDescriptor.getMethods().stream().findAny().get();

        GrpcSecurityHandler defaultHandler = mock(GrpcSecurityHandler.class);
        GrpcSecurityHandler handler = mock(GrpcSecurityHandler.class);

        when(call.getAttributes()).thenReturn(attributes);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(handler.handleSecurity(any(ServerCall.class), any(Metadata.class), any(ServerCallHandler.class))).thenReturn(listener);

        GrpcSecurity security = GrpcSecurity.create(Security.builder().build()).securityDefaults(defaultHandler);

        Context context = Context.current().withValue(GrpcSecurity.GRPC_SECURITY_HANDLER, handler);

        ServerCall.Listener<String> result = context.call(() -> security.interceptCall(call, headers, next));

        verify(handler).handleSecurity(call, headers, next);
        verifyNoMoreInteractions(defaultHandler);
        assertThat(result, is(notNullValue()));
    }

    @Test
    public void shouldBuildDefaultHandler()  {
        GrpcSecurityHandler handler = GrpcSecurity.enforce();

        assertThat(handler.isAuthenticate().orElse(null), is(nullValue()));
        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAuthorize().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldBuildSecureHandler()  {
        GrpcSecurityHandler handler = GrpcSecurity.secure();

        assertThat(handler.isAuthenticate().orElse(null), is(true));
        assertThat(handler.isAuthorize().orElse(null), is(true));

        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerAllowAnonymous() {
        GrpcSecurityHandler handler = GrpcSecurity.allowAnonymous();

        assertThat(handler.isAuthenticate().orElse(null), is(true));
        assertThat(handler.isAuthenticationOptional().orElse(null), is(true));

        assertThat(handler.isAuthorize().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithAudit() {
        GrpcSecurityHandler handler = GrpcSecurity.audit();

        assertThat(handler.isAudited().orElse(null), is(true));

        assertThat(handler.isAuthenticate().orElse(null), is(nullValue()));
        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAuthorize().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithAuthenticate() {
        GrpcSecurityHandler handler = GrpcSecurity.authenticate();

        assertThat(handler.isAuthenticate().orElse(null), is(true));

        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAuthorize().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithNamedAuthenticator() {
        GrpcSecurityHandler handler = GrpcSecurity.authenticator("foo");

        assertThat(handler.isAuthenticate().orElse(null), is(true));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is("foo"));

        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAuthorize().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithAuthorize() {
        GrpcSecurityHandler handler = GrpcSecurity.authorize();

        assertThat(handler.isAuthorize().orElse(null), is(true));

        assertThat(handler.isAuthenticate().orElse(null), is(nullValue()));
        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithNamedAuthorizer() {
        GrpcSecurityHandler handler = GrpcSecurity.authorizer("foo");

        assertThat(handler.isAuthenticate().orElse(null), is(true));
        assertThat(handler.isAuthorize().orElse(null), is(true));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is("foo"));

        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getRolesAllowed().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityHandlerWithRoles()  {
        GrpcSecurityHandler handler = GrpcSecurity.rolesAllowed("foo", "bar");

        assertThat(handler.getRolesAllowed().orElse(null), containsInAnyOrder("foo", "bar"));
        assertThat(handler.isAuthenticate().orElse(null), is(true));
        assertThat(handler.isAuthorize().orElse(null), is(true));

        assertThat(handler.isAuthenticationOptional().orElse(null), is(nullValue()));
        assertThat(handler.isAudited().orElse(null), is(nullValue()));
        assertThat(handler.getAuditEventType().orElse(null), is(nullValue()));
        assertThat(handler.getAuditMessageFormat().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthenticator().orElse(null), is(nullValue()));
        assertThat(handler.getExplicitAuthorizer().orElse(null), is(nullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurity() {
        Security security = Security.builder().build();
        GrpcSecurity grpcSecurity = GrpcSecurity.create(security);

        assertThat(grpcSecurity, is(notNullValue()));
        assertThat(grpcSecurity.getSecurity(), is(sameInstance(security)));
        assertThat(grpcSecurity.getDefaultHandler(), is(notNullValue()));
    }

    @Test
    public void shouldCreateGrpcSecurityWithDefaultHandler() {
        Security security = Security.builder().build();
        GrpcSecurityHandler defaultHandler = GrpcSecurityHandler.create();
        GrpcSecurity grpcSecurity = GrpcSecurity.create(security)
                .securityDefaults(defaultHandler);

        assertThat(grpcSecurity, is(notNullValue()));
        assertThat(grpcSecurity.getSecurity(), is(sameInstance(security)));
        assertThat(grpcSecurity.getDefaultHandler(), is(sameInstance(defaultHandler)));
    }


    private MethodDescriptor<String, String> getEchoMethod() {
        ServiceDescriptor serviceDescriptor = EchoServiceGrpc.getServiceDescriptor();
        return (MethodDescriptor<String, String>) serviceDescriptor.getMethods().stream().findAny().get();
    }

    private class ListenerStub
            extends ServerCall.Listener<String> {

        private Context context;

        @Override
        public void onMessage(String message) {
            context = Context.current();
        }

        public Context getContext() {
            return context;
        }
    }
}
