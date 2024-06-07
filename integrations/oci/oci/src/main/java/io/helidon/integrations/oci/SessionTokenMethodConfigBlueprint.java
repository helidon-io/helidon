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

package io.helidon.integrations.oci;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder;

/**
 * Configuration of the {@code config} authentication method.
 */
@Prototype.Blueprint
@Prototype.Configured
interface SessionTokenMethodConfigBlueprint {
    /**
     * The OCI region.
     *
     * @return the OCI region
     */
    @Option.Configured
    String region();

    /**
     * The OCI authentication fingerprint.
     * <p>
     * This configuration property must be provided in order to set the <a
     * href="https://docs.oracle.com/en-us/iaas/Content/API/Concepts/apisigningkey.htm">API signing key's fingerprint</a>.
     * See {@link com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getFingerprint()} for more details.
     *
     * @return the OCI authentication fingerprint
     */
    @Option.Configured
    String fingerprint();

    /**
     * The OCI authentication passphrase.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getPassphraseCharacters()}.
     *
     * @return the OCI authentication passphrase
     */
    @Option.Configured
    @Option.Confidential
    Optional<char[]> passphrase();

    /**
     * The OCI tenant id.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getTenantId()}.
     *
     * @return the OCI tenant id
     */
    @Option.Configured
    String tenantId();

    /**
     * The OCI user id.
     * <p>
     * This property must be provided in order to set the
     * {@linkplain com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider#getUserId()}.
     *
     * @return the OCI user id
     */
    @Option.Configured
    String userId();

    /**
     * The OCI authentication private key resource.
     * A resource can be defined as a resource on classpath, file on the file system,
     * base64 encoded text value in config, or plain-text value in config.
     * <p>
     * If not defined, we will use {@code ".oci/sessions/DEFAULT/oci_api_key.pem} file in user home directory.
     *
     * @return the OCI authentication key file
     */
    @Option.Configured
    Optional<Path> privateKeyPath();

    /**
     * Session token path.
     * If both this value, and {@link #sessionToken()} is defined, the value of {@link #sessionToken()} is used.
     *
     * @return session token path
     */
    @Option.Configured
    Optional<Path> sessionTokenPath();

    /**
     * Session token value.
     * If both this value, and {@link #sessionTokenPath()} is defined, this value is used.
     *
     * @return session token
     */
    @Option.Configured
    Optional<String> sessionToken();

    /**
     * Delay of the first refresh.
     * Defaults to 0, to refresh immediately (implemented in the authentication details provider).
     *
     * @return initial refresh delay
     * @see SessionTokenAuthenticationDetailsProviderBuilder#initialRefreshDelay(long)
     */
    @Option.Configured
    Optional<Duration> initialRefreshDelay();

    /**
     * Refresh period, i.e. how often refresh occurs.
     * Defaults to 55 minutes (implemented in the authentication details provider).
     *
     * @return refresh period
     * @see SessionTokenAuthenticationDetailsProviderBuilder#refreshPeriod(long)
     */
    @Option.Configured
    Optional<Duration> refreshPeriod();

    /**
     * Maximal lifetime of a session.
     * Defaults to (and maximum is) 24 hours.
     * Can only be set to a lower value.
     *
     * @return lifetime of a session in hours
     */
    @Option.Configured
    Optional<Long> sessionLifetimeHours();

    /**
     * Customize the scheduled executor service to use for scheduling.
     * Defaults to a single thread executor service.
     *
     * @return scheduled executor service
     */
    Optional<ScheduledExecutorService> scheduler();
}
