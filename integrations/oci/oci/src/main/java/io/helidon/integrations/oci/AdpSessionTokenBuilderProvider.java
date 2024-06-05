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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.ConfigException;
import io.helidon.service.registry.Service;

import com.oracle.bmc.ConfigFileReader.ConfigFile;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SessionTokenAuthenticationDetailsProvider.SessionTokenAuthenticationDetailsProviderBuilder;

@Service.Provider
class AdpSessionTokenBuilderProvider implements Supplier<SessionTokenAuthenticationDetailsProviderBuilder> {
    private static final LazyValue<ScheduledExecutorService> DEFAULT_SCHEDULER =
            LazyValue.create(Executors::newSingleThreadScheduledExecutor);

    private final OciConfig config;
    private final Supplier<Optional<ConfigFile>> configFileSupplier;

    AdpSessionTokenBuilderProvider(OciConfig config,
                                   Supplier<Optional<ConfigFile>> configFileSupplier) {

        this.config = config;
        this.configFileSupplier = configFileSupplier;
    }

    @Override
    public SessionTokenAuthenticationDetailsProviderBuilder get() {
        var builder = SessionTokenAuthenticationDetailsProvider.builder();

        updateFromConfigFile(builder);
        updateFromConfig(builder);

        return builder;
    }

    Optional<String> value(ConfigFile file, String key) {
        return Optional.ofNullable(file.get(key));
    }

    private void updateFromConfig(SessionTokenAuthenticationDetailsProviderBuilder builder) {
        Optional<SessionTokenMethodConfig> maybeSessionTokenConfig = config.sessionTokenMethodConfig();
        if (maybeSessionTokenConfig.isEmpty()) {
            return;
        }
        SessionTokenMethodConfig sessionTokenConfig = maybeSessionTokenConfig.get();

        builder.fingerprint(sessionTokenConfig.fingerprint());
        sessionTokenConfig.passphrase()
                .map(String::new)
                .ifPresent(builder::passPhrase);
        sessionTokenConfig.privateKeyPath()
                .map(Path::toString)
                .ifPresent(builder::privateKeyFilePath);

        builder.region(sessionTokenConfig.region());
        builder.tenantId(sessionTokenConfig.tenantId());
        builder.userId(sessionTokenConfig.userId());

        builder.timeUnit(TimeUnit.MILLISECONDS);
        sessionTokenConfig.initialRefreshDelay()
                .map(Duration::toMillis)
                .ifPresent(builder::initialRefreshDelay);

        sessionTokenConfig.refreshPeriod()
                .map(Duration::toMillis)
                .ifPresent(builder::refreshPeriod);
        sessionTokenConfig.sessionLifetimeHours()
                .ifPresent(builder::sessionLifetimeHours);

        builder.scheduler(sessionTokenConfig.scheduler().orElseGet(DEFAULT_SCHEDULER));

        /*
        Session token
         */
        Optional<String> sessionToken = sessionTokenConfig.sessionToken();
        Optional<Path> sessionTokenPath = sessionTokenConfig.sessionTokenPath();

        if (sessionToken.isEmpty() && sessionTokenPath.isEmpty()) {
            throw new ConfigException("When configuring session token authentication, either session token or session token "
                                              + "path must be provided");
        }
        if (sessionToken.isPresent()) {
            builder.sessionToken(sessionToken.get());
        } else {
            builder.sessionTokenFilePath(sessionTokenPath.get().toString());
        }
    }

    private void updateFromConfigFile(SessionTokenAuthenticationDetailsProviderBuilder builder) {
        Optional<ConfigFile> maybeConfigFile = configFileSupplier.get();
        if (maybeConfigFile.isEmpty()) {
            return;
        }
        ConfigFile configFile = maybeConfigFile.get();

        value(configFile, "security_token_file")
                .ifPresent(builder::sessionTokenFilePath);
        value(configFile, "tenancy")
                .ifPresent(builder::tenantId);
        value(configFile, "key_file")
                .ifPresent(builder::privateKeyFilePath);
        value(configFile, "fingerprint")
                .ifPresent(builder::fingerprint);
        value(configFile, "pass_phrase")
                .ifPresent(builder::passPhrase);
        value(configFile, "user")
                .ifPresent(builder::userId);

        Region region = ConfigFileAuthenticationDetailsProvider.getRegionFromConfigFile(configFile);
        if (region != null) {
            builder.region(region);
        }
    }
}
