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

/**
 * OCI integration module using Helidon Service Registry.
 * This core module provides services for {@link com.oracle.bmc.Region} and
 * {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider}.
 * <p>
 * The module does not require {@link io.helidon.common.config.Config} service to be available, as it is considered
 * a prerequisite for possible config sources.
 * <p>
 *
 * To customize configuration of this module, the following options exist:
 * <ul>
 *     <li>Create a custom service provider that provides {@link io.helidon.integrations.oci.OciConfig}</li>
 *     <li>Create a file {@code oci-config.yaml} either on classpath, or in the current directory with configuration
 *     required by {@link io.helidon.integrations.oci.OciConfig}, this also requires YAML config parser on classpath.</li>
 *     <li>Add environment variables to override configuration options of {@link io.helidon.integrations.oci.OciConfig},
 *     such as {@code OCI_AUTHENTICATION_METHOD=config_file}</li>
 *     <li>Add system properties to override configuration options of {@link io.helidon.integrations.oci.OciConfig},
 *     such as {@code oci.authenticationMethod=config_file}</li>
 * </ul>
 *
 * To customize authentication details provider, you can implement {@link io.helidon.integrations.oci.spi.OciAuthenticationMethod}
 * service. The out-of-the-box providers have all less than default weight, and are in the
 * following order (authentication method: description (weight)):
 * <ul>
 *     <li>{@code config}: Config based authentication details provider - using only configured options (default weight - 10)</li>
 *     <li>{@code config-file}: Config file based authentication details provider (default weight - 20)</li>
 *     <li>{@code resource-principal}: Resource principal, used for example by fn (default-weight - 30)</li>
 *     <li>{@code instance-principal}: Instance principal, used for VMs (default-weight - 40)</li>
 * </ul>
 */
module io.helidon.integrations.oci {
    requires io.helidon.common.configurable;
    requires io.helidon.service.registry;
    requires io.helidon.common.config;
    requires io.helidon.config;

    requires oci.java.sdk.common;
    requires vavr;

    exports io.helidon.integrations.oci;
    exports io.helidon.integrations.oci.spi;
}
