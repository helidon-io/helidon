/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

import com.oracle.bmc.Region;

/**
 * Meta configuration of OCI integration for Helidon.
 * <p>
 * Allows customization of discovery of authentication details provider and of region.
 */
@Prototype.Configured("helidon.oci")
@Prototype.Blueprint
@Prototype.CustomMethods(OciConfigSupport.class)
interface OciConfigBlueprint {
    /**
     * Default authentication method. The default is to use automatic discovery - i.e. cycle through possible
     * providers until one yields an authentication details provider instance.
     */
    String AUTHENTICATION_METHOD_AUTO = "auto";

    /**
     * Explicit region. The configured region will be used by region provider.
     * This may be ignored by authentication detail providers, as in most cases region is provided by them.
     *
     * @return explicit region
     */
    @Option.Configured
    Optional<Region> region();

    /**
     * Authentication method to use. If the configured method is not available, an exception
     * would be thrown for OCI related services.
     * <p>
     * Known and supported authentication strategies for public OCI:
     * <ul>
     *     <li>{@value #AUTHENTICATION_METHOD_AUTO} - use the list of
     *     {@link io.helidon.integrations.oci.OciConfig#allowedAuthenticationMethods()}
     *          (in the provided order), and choose the first one capable of providing data</li>
     *     <li>{@value AuthenticationMethodConfig#METHOD} -
     *     use configuration of the application to obtain values needed to set up connectivity, uses
     *     {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider}</li>
     *     <li>{@value AuthenticationMethodConfigFile#METHOD} - use configuration file of OCI ({@code home/.oci/config}), uses
     *     {@link com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider}</li>
     *     <li>{@code resource-principal}  - use identity of the OCI resource the service is executed on
     *     (fn), uses
     *     {@link com.oracle.bmc.auth.ResourcePrincipalAuthenticationDetailsProvider}, and is available in a
     *     separate module {@code helidon-integrations-oci-authentication-resource}</li>
     *     <li>{@code instance-principal} - use identity of the OCI instance the service is running on, uses
     *     {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}, and is available in a
     *     separate module {@code helidon-integrations-oci-authentication-resource}</li>
     *     <li>{@code oke-workload-identity} - use identity of the OCI Kubernetes workload, uses
     *     {@code com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider}, and is available in a
     *     separate module {@code helidon-integrations-oci-authentication-oke-workload}</li>
     * </ul>
     *
     * @return the authentication method to apply
     */
    @Option.Configured
    @Option.Default(AUTHENTICATION_METHOD_AUTO)
    String authenticationMethod();

    /**
     * List of attempted authentication strategies in case {@link io.helidon.integrations.oci.OciConfig#authenticationMethod()} is
     * set to {@value #AUTHENTICATION_METHOD_AUTO}.
     * <p>
     * In case the list is empty, all available strategies will be tried, ordered by their {@link io.helidon.common.Weight}
     *
     * @return list of authentication strategies to be tried
     * @see io.helidon.integrations.oci.OciConfig#authenticationMethod()
     */
    @Option.Configured
    List<String> allowedAuthenticationMethods();

    /**
     * Config method configuration (if provided and used).
     *
     * @return information needed for config {@link io.helidon.integrations.oci.OciConfig#authenticationMethod()}
     */
    @Option.Configured("authentication.config")
    Optional<ConfigMethodConfig> configMethodConfig();

    /**
     * Config file method configuration (if provided and used).
     *
     * @return information to customize config for {@link io.helidon.integrations.oci.OciConfig#authenticationMethod()}
     */
    @Option.Configured("authentication.config-file")
    Optional<ConfigFileMethodConfig> configFileMethodConfig();

    /**
     * Session token method configuration (if provided and used).
     *
     * @return information to customize config for {@link io.helidon.integrations.oci.OciConfig#authenticationMethod()}
     */
    @Option.Configured("authentication.session-token")
    Optional<SessionTokenMethodConfig> sessionTokenMethodConfig();

    /**
     * The OCI IMDS connection timeout. This is used to auto-detect availability.
     * <p>
     * This configuration property is used when attempting to connect to the metadata service.
     *
     * @return the OCI IMDS connection timeout
     */
    @Option.Configured
    @Option.Default("PT1S")
    Duration imdsTimeout();

    /**
     * The OCI IMDS URI (http URL pointing to the metadata service, if customization needed).
     *
     * @return the OCI IMDS URI
     */
    @Option.Configured
    Optional<URI> imdsBaseUri();

    /**
     * Customize the number of retries to contact IMDS service.
     *
     * @return number of retries, each provider has its own defaults
     */
    @Option.Configured
    Optional<Integer> imdsDetectRetries();

    /**
     * Timeout of authentication operations, where applicable.
     * This is a timeout for each operation (if there are retries, each timeout will be this duration).
     * Defaults to 10 seconds.
     *
     * @return authentication operation timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration authenticationTimeout();

    /**
     * Customization of federation endpoint for authentication providers.
     *
     * @return custom federation endpoint URI
     */
    @Option.Configured
    Optional<URI> federationEndpoint();

    /**
     * OCI tenant id for Instance Principal, Resource Principal or OKE Workload.
     *
     * @return the OCI tenant id
     */
    @Option.Configured
    Optional<String> tenantId();

    /**
     * Get the config used to update the builder.
     *
     * @return configuration
     */
    Optional<Config> config();
}
