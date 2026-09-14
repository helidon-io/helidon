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

package io.helidon.microprofile.grpc.security;

import java.lang.reflect.Proxy;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.spi.ConfigNode;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.webserver.grpc.GrpcRouting;
import io.helidon.webserver.grpc.security.GrpcSecurity;

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GrpcMpSecurityExtensionTest {

    @Test
    void shouldRecognizeDefaultGrpcSecurityServiceConfig() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.empty())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(true));
    }

    @Test
    void shouldRecognizeExplicitGrpcSecurityServiceConfig() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("type", "security")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(true));
    }

    @Test
    void shouldRecognizeListGrpcSecurityServiceConfig() {
        Config config = Config.just("""
                security:
                  providers:
                    - http-basic-auth:
                        realm: "helidon"
                grpc:
                  grpc-services:
                    - security:
                        services:
                          - name: "UnaryService"
                """, MediaTypes.APPLICATION_YAML);
        GrpcRouting.Builder routing = GrpcRouting.builder()
                .config(config);

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(true));
        new GrpcMpSecurityExtension().configure(new TestGrpcMpContext(config, routing, emptyBeanManager()));
        assertThat(countGrpcSecurityInterceptors(routing), is(1L));
    }

    @Test
    void shouldRecognizeDirectListGrpcSecurityServiceConfig() {
        Config config = Config.just("""
                security:
                  providers:
                    - http-basic-auth:
                        realm: "helidon"
                grpc:
                  grpc-services:
                    - type: security
                      services:
                        - name: "UnaryService"
                """, MediaTypes.APPLICATION_YAML);
        GrpcRouting.Builder routing = GrpcRouting.builder()
                .config(config);

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(true));
        new GrpcMpSecurityExtension().configure(new TestGrpcMpContext(config, routing, emptyBeanManager()));
        assertThat(countGrpcSecurityInterceptors(routing), is(1L));
    }

    @Test
    void shouldLeaveCustomNamedListSecurityToConfiguredService() {
        Config config = Config.just("""
                security:
                  providers:
                    - http-basic-auth:
                        realm: "helidon"
                grpc:
                  grpc-services:
                    - type: security
                      name: mp-security
                      services:
                        - name: "UnaryService"
                """, MediaTypes.APPLICATION_YAML);
        GrpcRouting.Builder routing = GrpcRouting.builder()
                .config(config);

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(false));
        new GrpcMpSecurityExtension().configure(new TestGrpcMpContext(config, routing, null));
        assertThat(countGrpcSecurityInterceptors(routing), is(1L));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BeanManager emptyBeanManager() {
        Instance<?> emptyInstance = (Instance<?>) Proxy.newProxyInstance(Instance.class.getClassLoader(),
                                                                         new Class<?>[] {Instance.class},
                                                                         (proxy, method, args) -> switch (method.getName()) {
                                                                         case "select" -> proxy;
                                                                         case "isUnsatisfied" -> true;
                                                                         case "toString" -> "empty";
                                                                         case "hashCode" -> 0;
                                                                         case "equals" -> proxy == args[0];
                                                                         default -> throw new AssertionError(method);
                                                                         });
        return (BeanManager) Proxy.newProxyInstance(BeanManager.class.getClassLoader(),
                                                    new Class<?>[] {BeanManager.class},
                                                    (proxy, method, args) -> switch (method.getName()) {
                                                    case "createInstance" -> emptyInstance;
                                                    case "toString" -> "beanManager";
                                                    case "hashCode" -> 0;
                                                    case "equals" -> proxy == args[0];
                                                    default -> throw new AssertionError(method);
                                                    });
    }

    @Test
    void shouldIgnoreTypedProviderNamedSecurity() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("type", "custom-security")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(false));
    }

    @Test
    void shouldIgnoreDisabledGrpcSecurityServiceConfig() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("enabled", "false")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(false));
    }

    @Test
    void shouldIgnoreServerProtocolSecurityServiceConfig() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("services.0.name", "UnaryService")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(false));
        assertThat(containsGrpcSecurityInterceptor(config), is(false));
    }

    @Test
    void shouldIgnoreServerProtocolSecurityWhenTopLevelSecurityIsDisabled() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("services.0.name", "UnaryService")
                        .build())
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("enabled", "false")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(false));
        assertThat(containsGrpcSecurityInterceptor(config), is(false));
    }

    @Test
    void shouldUseTopLevelConfigWhenServerProtocolSecurityIsDisabled() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("enabled", "false")
                        .build())
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("services.0.name", "UnaryService")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.isGrpcSecurityServiceConfigured(config), is(true));
        assertThat(GrpcMpSecurityExtension.grpcSecurityServiceConfig(config).key().toString(),
                   is("grpc.grpc-services.security"));
    }

    @Test
    void shouldUseTopLevelConfigWhenServerProtocolNameIsCustomProvider() {
        Config config = Config.just(ConfigSources.create(ConfigNode.ObjectNode.builder()
                .addObject("server.protocols.grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("type", "custom-security")
                        .build())
                .addObject("grpc.grpc-services.security", ConfigNode.ObjectNode.builder()
                        .addValue("services.0.name", "UnaryService")
                        .build())
                .build()));

        assertThat(GrpcMpSecurityExtension.grpcSecurityServiceConfig(config).key().toString(),
                   is("grpc.grpc-services.security"));
    }

    private static boolean containsGrpcSecurityInterceptor(Config config) {
        GrpcRouting.Builder routing = GrpcRouting.builder()
                .config(config);
        new GrpcMpSecurityExtension().configure(new TestGrpcMpContext(config, routing, null));
        return routing.build()
                .interceptors()
                .stream()
                .anyMatch(GrpcSecurity.class::isInstance);
    }

    private static long countGrpcSecurityInterceptors(GrpcRouting.Builder routing) {
        return routing.build()
                .interceptors()
                .stream()
                .filter(GrpcSecurity.class::isInstance)
                .count();
    }

    private record TestGrpcMpContext(Config config, GrpcRouting.Builder routing, BeanManager beanManager)
            implements GrpcMpContext {
        @Override
        public BeanManager beanManager() {
            if (beanManager != null) {
                return beanManager;
            }
            throw new AssertionError("BeanManager should not be used for disabled or protocol-only gRPC security config");
        }
    }
}
