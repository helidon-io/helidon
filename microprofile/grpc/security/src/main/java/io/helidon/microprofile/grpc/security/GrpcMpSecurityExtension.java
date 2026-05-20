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

import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;
import io.helidon.security.Security;
import io.helidon.webserver.grpc.security.GrpcSecurity;

import jakarta.enterprise.inject.Instance;

/**
 * gRPC MP extension that adds the gRPC security interceptor when gRPC security is configured.
 */
public class GrpcMpSecurityExtension implements GrpcMpExtension {
    private static final String GRPC_SECURITY_SERVICE_NAME = "security";
    private static final String TYPE_CONFIG_KEY = "type";
    private static final String TOP_LEVEL_CONFIG_KEY = "grpc.grpc-services.security";
    private static final String TOP_LEVEL_SERVICES_CONFIG_KEY = "grpc.grpc-services";

    /**
     * Create a gRPC MP security extension.
     */
    public GrpcMpSecurityExtension() {
    }

    @Override
    public void configure(GrpcMpContext context) {
        Config config = context.config();
        context.routing().addExcludedServiceName(GRPC_SECURITY_SERVICE_NAME);
        Config grpcSecurityConfig = grpcSecurityServiceConfig(config);
        if (!isGrpcSecurityServiceEnabled(grpcSecurityConfig)) {
            return;
        }

        Instance<Security> securityInstance = context.beanManager()
                .createInstance()
                .select(Security.class);
        Security security;
        if (securityInstance.isUnsatisfied()) {
            Config securityConfig = config.root().get("security");
            if (!securityConfig.exists()) {
                throw new ConfigException("gRPC MP security requires either a configured Security bean "
                                                  + "or root security configuration");
            }
            security = Security.create(securityConfig);
        } else {
            security = securityInstance.get();
        }

        context.routing()
                .intercept(GrpcSecurity.create(security, grpcSecurityConfig));
    }

    // Package-private test seams for the MP bootstrap decision.
    static boolean isGrpcSecurityServiceConfigured(Config config) {
        return isGrpcSecurityServiceEnabled(grpcSecurityServiceConfig(config));
    }

    static Config grpcSecurityServiceConfig(Config config) {
        Config topLevelConfig = config.get(TOP_LEVEL_CONFIG_KEY);
        if (isGrpcSecurityServiceConfig(topLevelConfig)) {
            return topLevelConfig;
        }
        Config servicesConfig = config.get(TOP_LEVEL_SERVICES_CONFIG_KEY);
        if (servicesConfig.isList()) {
            for (Config serviceConfig : servicesConfig.asNodeList().orElseGet(List::of)) {
                Config grpcSecurityListConfig = grpcSecurityListServiceConfig(serviceConfig);
                if (grpcSecurityListConfig.exists()) {
                    return grpcSecurityListConfig;
                }
            }
        }
        return Config.empty();
    }

    private static Config grpcSecurityListServiceConfig(Config serviceConfig) {
        String type = serviceConfig.get(TYPE_CONFIG_KEY).asString().orElse(null);
        if (type != null) {
            String name = serviceConfig.get("name").asString().orElse(type);
            return isDefaultGrpcSecurityService(type, name) ? serviceConfig : Config.empty();
        }

        List<Config> children = serviceConfig.asNodeList().orElseGet(List::of);
        if (children.size() == 1) {
            Config child = children.getFirst();
            String name = child.name();
            type = child.get(TYPE_CONFIG_KEY).asString().orElse(name);
            if (isDefaultGrpcSecurityService(type, name)) {
                return child;
            }
        }
        return Config.empty();
    }

    private static boolean isGrpcSecurityServiceConfig(Config config) {
        if (!config.exists()) {
            return false;
        }
        String defaultType = GRPC_SECURITY_SERVICE_NAME.equals(config.name()) ? GRPC_SECURITY_SERVICE_NAME : null;
        return GRPC_SECURITY_SERVICE_NAME.equals(config.get(TYPE_CONFIG_KEY).asString()
                                                        .orElse(defaultType));
    }

    private static boolean isDefaultGrpcSecurityService(String type, String name) {
        return GRPC_SECURITY_SERVICE_NAME.equals(type)
                && GRPC_SECURITY_SERVICE_NAME.equals(name);
    }

    private static boolean isGrpcSecurityServiceEnabled(Config config) {
        return config.exists()
                && config.get("enabled").asBoolean().orElse(true);
    }
}
