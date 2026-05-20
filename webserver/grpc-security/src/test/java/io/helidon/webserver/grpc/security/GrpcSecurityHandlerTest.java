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

package io.helidon.webserver.grpc.security;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.AuditEvent;
import io.helidon.security.Security;
import io.helidon.security.spi.AuditProvider;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;

class GrpcSecurityHandlerTest {
    @Test
    void testCombinedHandlerIsCached() {
        GrpcSecurityHandler defaults = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();
        GrpcSecurityHandler handler = GrpcSecurityHandler.builder()
                .authorize(true)
                .build();

        GrpcSecurityHandler firstCombined = handler.combine(defaults);
        GrpcSecurityHandler secondCombined = handler.combine(defaults);

        assertThat(firstCombined, sameInstance(secondCombined));
    }

    @Test
    void testDefaultHandlerDoesNotCreateCombinedHandler() {
        GrpcSecurityHandler handler = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();

        assertThat(handler.combine(GrpcSecurityHandler.create()), sameInstance(handler));
    }

    @Test
    void testDifferentDefaultsDoNotReuseCachedHandler() {
        GrpcSecurityHandler firstDefaults = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();
        GrpcSecurityHandler secondDefaults = GrpcSecurityHandler.builder()
                .audit(true)
                .build();
        GrpcSecurityHandler handler = GrpcSecurityHandler.builder()
                .authorize(true)
                .build();

        GrpcSecurityHandler firstCombined = handler.combine(firstDefaults);
        GrpcSecurityHandler secondCombined = handler.combine(secondDefaults);

        assertThat(firstCombined, not(sameInstance(secondCombined)));
    }

    @Test
    void testConfigHandlerOverridesAttachedHandler() {
        GrpcSecurityHandler attachedHandler = GrpcSecurityHandler.builder()
                .authenticate(false)
                .audit(true)
                .build();
        GrpcSecurityHandler configHandler = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();

        GrpcSecurityHandler combined = configHandler.combine(attachedHandler);

        assertThat(combined.prototype().authenticate(), is(Optional.of(true)));
        assertThat(combined.prototype().audit(), is(Optional.of(true)));
    }

    @Test
    void testCombiningPreservesAttachedRolesWhenConfigDoesNotOverrideRoles() {
        GrpcSecurityHandler attachedHandler = GrpcSecurityHandler.builder()
                .rolesAllowed(Set.of("admin"))
                .build();
        GrpcSecurityHandler configHandler = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();

        GrpcSecurityHandler combined = configHandler.combine(attachedHandler);

        assertThat(combined.prototype().rolesAllowed(), contains("admin"));
        assertThat(combined.prototype().authenticate(), is(Optional.of(true)));
    }

    @Test
    void testProgrammaticServiceAndMethodHandlersAreLayered() {
        GrpcSecurity security = GrpcSecurity.create(Security.builder().build());
        GrpcSecurityHandler serviceHandler = GrpcSecurityHandler.builder()
                .authenticate(true)
                .build();
        GrpcSecurityHandler methodHandler = GrpcSecurityHandler.builder()
                .audit(true)
                .build();
        Context context = Context.current()
                .withValue(GrpcSecurity.GRPC_SERVICE_SECURITY_HANDLER, serviceHandler)
                .withValue(GrpcSecurity.GRPC_METHOD_SECURITY_HANDLER, methodHandler);

        GrpcSecurityHandler handler = security.securityHandler(context, methodDescriptor());

        assertThat(handler.prototype().authenticate(), is(Optional.of(true)));
        assertThat(handler.prototype().audit(), is(Optional.of(true)));
    }

    @Test
    void testConfiguredServiceRulesOverrideProgrammaticHandler() {
        GrpcSecurity security = GrpcSecurity.create(Security.builder().build(),
                                                    Config.just(ConfigSources.create(Map.of(
                                                            "grpc.grpc-services.security.services.0.name", "Test",
                                                            "grpc.grpc-services.security.services.0.defaults.authenticate",
                                                            "true"))));
        GrpcSecurityHandler methodHandler = GrpcSecurityHandler.builder()
                .authenticate(false)
                .audit(true)
                .build();
        Context context = Context.current()
                .withValue(GrpcSecurity.GRPC_METHOD_SECURITY_HANDLER, methodHandler);

        GrpcSecurityHandler handler = security.securityHandler(context, methodDescriptor());

        assertThat(handler.prototype().authenticate(), is(Optional.of(true)));
        assertThat(handler.prototype().audit(), is(Optional.of(true)));
    }

    @Test
    void testListConfiguredServiceRulesOverrideProgrammaticHandler() {
        GrpcSecurity security = GrpcSecurity.create(Security.builder().build(),
                                                    Config.just("""
                                                            grpc:
                                                              grpc-services:
                                                                - type: security
                                                                  services:
                                                                    - name: Test
                                                                      defaults:
                                                                        authenticate: true
                                                            """, MediaTypes.APPLICATION_YAML));
        GrpcSecurityHandler methodHandler = GrpcSecurityHandler.builder()
                .authenticate(false)
                .audit(true)
                .build();
        Context context = Context.current()
                .withValue(GrpcSecurity.GRPC_METHOD_SECURITY_HANDLER, methodHandler);

        GrpcSecurityHandler handler = security.securityHandler(context, methodDescriptor());

        assertThat(handler.prototype().authenticate(), is(Optional.of(true)));
        assertThat(handler.prototype().audit(), is(Optional.of(true)));
    }

    @Test
    void testDisabledRootConfigDoesNotRequireSecurityConfig() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "grpc.grpc-services.security.enabled", "false")));

        GrpcSecurity security = GrpcSecurity.create(config);

        assertThat(security.interceptors().isEmpty(), is(true));
    }

    @Test
    void testDisabledServiceConfigDoesNotRequireSecurityConfig() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "grpc.grpc-services.security.enabled", "false")));

        GrpcSecurity security = GrpcSecurity.create(config.get("grpc.grpc-services.security"));

        assertThat(security.interceptors().isEmpty(), is(true));
    }

    @Test
    void testSynchronousCloseDuringStartCallIsAuditedOnce() throws Exception {
        TestAuditProvider auditProvider = new TestAuditProvider();
        Security security = Security.builder()
                .addAuditProvider(auditProvider)
                .build();
        GrpcSecurity grpcSecurity = GrpcSecurity.create(security);
        GrpcSecurityHandler handler = GrpcSecurityHandler.builder()
                .audit(true)
                .build();
        TestServerCall call = new TestServerCall();
        Metadata headers = new Metadata();
        ServerCallHandler<String, String> next = (serverCall, metadata) -> {
            serverCall.close(Status.OK, new Metadata());
            return new ServerCall.Listener<>() {
            };
        };

        Context context = grpcSecurity.registerContext(call, headers);
        ServerCall.Listener<String> listener = context.call(() -> handler.handleSecurity(call, headers, next));

        assertThat(auditProvider.count(), is(1));
        assertThat(auditProvider.auditEvent().severity(), is(AuditEvent.AuditSeverity.SUCCESS));

        listener.onComplete();

        assertThat(auditProvider.count(), is(1));
    }

    private static MethodDescriptor<String, String> methodDescriptor() {
        MethodDescriptor.Marshaller<String> marshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                return "";
            }
        };
        return MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("Test/Call")
                .setRequestMarshaller(marshaller)
                .setResponseMarshaller(marshaller)
                .build();
    }

    private static final class TestServerCall extends ServerCall<String, String> {
        private static final Attributes ATTRIBUTES = Attributes.newBuilder()
                .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, new InetSocketAddress("127.0.0.1", 8080))
                .build();

        @Override
        public void request(int numMessages) {
        }

        @Override
        public void sendHeaders(Metadata headers) {
        }

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void close(Status status, Metadata trailers) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<String, String> getMethodDescriptor() {
            return methodDescriptor();
        }

        @Override
        public Attributes getAttributes() {
            return ATTRIBUTES;
        }
    }

    private static final class TestAuditProvider implements AuditProvider {
        private final AtomicInteger count = new AtomicInteger();
        private volatile AuditEvent auditEvent;

        @Override
        public Consumer<TracedAuditEvent> auditConsumer() {
            return event -> {
                if (GrpcSecurityHandler.DEFAULT_AUDIT_EVENT_TYPE.equals(event.eventType())) {
                    auditEvent = event;
                    count.incrementAndGet();
                }
            };
        }

        int count() {
            return count.get();
        }

        AuditEvent auditEvent() {
            return auditEvent;
        }
    }
}
