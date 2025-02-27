/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.integrations.eureka;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import jakarta.json.JsonBuilderFactory;

/*
 * Note that Javadoc for this interface is actually Javadoc for the prototype interface that is generated from it.
 *
 * Note as well that @see references to the prototype's methods cannot be used because they will reference the (publicly
 * inaccessible) blueprint interface.
 */

/**
 * A {@linkplain Prototype.Api prototype} describing initial Eureka Server service instance registration details.
 *
 * <p>This interface is deliberately modeled to closely resemble the {@code com.netflix.appinfo.InstanceInfo} class for
 * familiarity.</p>
 *
 * <p>Its configuration is deliberately modeled to closely resemble that expressed by the {@code
 * com.netflix.appinfo.PropertiesInstanceConfig} class and its supertypes for user familiarity.</p>
 */
@Prototype.Blueprint(decorator = InstanceInfoConfigSupport.BuilderDecorator.class)
@Prototype.Configured
@Prototype.CustomMethods(InstanceInfoConfigSupport.CustomMethods.class)
interface InstanceInfoConfigBlueprint {

    /**
     * The app name.
     *
     * @return the app name
     */
    @Option.Configured("name")
    @Option.Default("unknown")
    String appName();

    /**
     * The app group name.
     *
     * @return the app group name
     */
    @Option.Configured("appGroup")
    @Option.Default("unknown")
    String appGroupName(); // needs to be uppercase if present; Option.Decorator?

    /**
     * The ASG name.
     *
     * <p><abbr>ASG</abbr> stands for Auto Scaling Group.</p>
     *
     * @return the ASG name
     */
    @Option.Configured("asgName")
    Optional<String> asgName();

    /**
     * The health check URL.
     *
     * @return the health check URL
     */
    @Option.Configured("healthCheckUrl")
    Optional<URI> healthCheckUrl();

    /**
     * The health check URL path (used if any health check URL is not explicitly set).
     *
     * @return the health check URL path
     */
    @Option.Configured("healthCheckUrlPath")
    String healthCheckUrlPath();

    /**
     * The home page URL.
     *
     * @return the home page URL
     */
    @Option.Configured("homePageUrl")
    Optional<URI> homePageUrl();

    /**
     * The home page URL path (used if the homepage URL is not explicitly set).
     *
     * @return the home page URL path
     */
    @Option.Configured("homePageUrlPath")
    @Option.Default("/")
    String homePageUrlPath();

    /**
     * The hostname.
     *
     * @return the hostname
     */
    @Option.Configured("hostName")
    String hostName();

    /**
     * The instance id.
     *
     * @return the instance id
     */
    @Option.Configured("instanceId")
    Optional<String> instanceId();

    /**
     * The IP address.
     *
     * @return the IP address
     */
    @Option.Configured("ipAddr")
    String ipAddr();

    /**
     * A {@link JsonBuilderFactory}.
     *
     * @return the {@link JsonBuilderFactory}
     */
    @Option.DefaultCode("@jakarta.json.Json@.createBuilderFactory(@java.util.Map@.of())")
    JsonBuilderFactory jsonBuilderFactory();

    /**
     * The {@link LeaseInfoConfig}.
     *
     * @return the {@link LeaseInfoConfig}
     *
     * @see LeaseInfoConfig
     */
    @Option.Configured("lease")
    @Option.DefaultMethod("create")
    LeaseInfoConfig leaseInfo();

    /**
     * Metadata.
     *
     * @return the metadata
     */
    @Option.Configured("metadata")
    @Option.DefaultCode("@java.util.Map@.of()")
    Map<String, String> metadata();

    /**
     * Port.
     *
     * @return the port
     */
    default int port() {
        return this.portInfo().port();
    }

    /**
     * (Non-secure) port information.
     *
     * @return {@link PortInfoConfig}
     */
    @Option.Configured("port")
    @Option.DefaultMethod("create")
    PortInfoConfig portInfo();

    /**
     * The secure health check URL.
     *
     * @return the secure health check URL
     */
    @Option.Configured("secureHealthCheckUrl")
    Optional<URI> secureHealthCheckUrl();

    /**
     * Secure port.
     *
     * @return the secure port
     */
    default int securePort() {
        return this.securePortInfo().port();
    }

    /**
     * Secure port information.
     *
     * @return {@link PortInfoConfig}
     */
    @Option.Configured("securePort")
    @Option.DefaultMethod("create")
    PortInfoConfig securePortInfo();

    /**
     * The status page URL.
     *
     * @return the status page URL
     */
    @Option.Configured("statusPageUrl")
    Optional<URI> statusPageUrl();

    /**
     * The status page URL path (used if status page URL is not explicitly set).
     *
     * @return the status page URL path
     */
    @Option.Configured("statusPageUrlPath")
    @Option.Default("/Status")
    String statusPageUrlPath();

    /**
     * Whether traffic is enabled on startup (normally {@code true}).
     *
     * @return whether traffic is enabled on startup
     */
    @Option.Configured("traffic.enabled")
    @Option.DefaultBoolean(true)
    boolean trafficEnabled();

    /**
     * The VIP address.
     *
     * <p><abbr>VIP</abbr> stands for Virtual IP.</p>
     *
     * @return the VIP address
     */
    @Option.Configured("vipAddress")
    Optional<String> vipAddress();

    /**
     * The secure VIP address.
     *
     * <p><abbr>VIP</abbr> stands for Virtual IP.</p>
     *
     * @return the secure VIP address
     */
    @Option.Configured("secureVipAddress")
    Optional<String> secureVipAddress();

}
