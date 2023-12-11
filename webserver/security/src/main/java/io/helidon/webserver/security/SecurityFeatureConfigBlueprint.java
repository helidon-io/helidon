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

package io.helidon.webserver.security;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.security.Security;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Configuration of security feature fow webserver.
 */
@Prototype.Blueprint(decorator = SecurityConfigSupport.SecurityFeatureConfigDecorator.class)
@Prototype.Configured(value = SecurityFeature.SECURITY_ID, root = false)
@Prototype.Provides(ServerFeatureProvider.class)
interface SecurityFeatureConfigBlueprint extends Prototype.Factory<SecurityFeature> {
    /**
     * Weight of the security feature. Value is:
     * {@value io.helidon.webserver.security.SecurityFeature#WEIGHT}.
     *
     * @return weight of the feature
     */
    @Option.DefaultDouble(SecurityFeature.WEIGHT)
    @Option.Configured
    double weight();

    /**
     * The default security handler.
     *
     * @return security handler defaults
     */
    @Option.Configured
    @Option.DefaultCode("SecurityHandler.create()")
    SecurityHandler defaults();

    /**
     * Configuration for webserver paths.
     *
     * @return path configuration
     */
    @Option.Configured
    @Option.Singular
    List<PathsConfig> paths();

    /**
     * Security associated with this feature.
     * If not specified here, the feature uses security registered with
     * {@link io.helidon.common.context.Contexts#globalContext()}, if not found, it creates a new
     * instance from root of configuration (using {@code security} key).
     * <p>
     * This configuration allows usage of a different security instance for a specific security feature setup.
     *
     * @return security instance to be used to handle security in this feature configuration
     */
    @Option.Configured
    Security security();

    /**
     * Name of this instance.
     *
     * @return instance name
     */
    @Option.Default(SecurityFeature.SECURITY_ID)
    String name();
}
