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

package io.helidon.integrations.oci.authentication.okeworkload;

import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import io.helidon.common.LazyValue;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.OciConfig;
import io.helidon.integrations.oci.spi.OciAuthenticationMethod;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider;

/**
 * Instance principal authentication method, uses the
 * {@link com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider}.
 */
@Weight(Weighted.DEFAULT_WEIGHT - 40)
@Service.Provider
class AuthenticationMethodOkeWorkload implements OciAuthenticationMethod {
    private static final System.Logger LOGGER = System.getLogger(AuthenticationMethodOkeWorkload.class.getName());

    private static final String METHOD = "oke-workload-identity";

    /*
    These constants are copied from
    com.oracle.bmc.auth.okeworkloadidentity.OkeWorkloadIdentityAuthenticationDetailsProvider
    as they are not public
     */
    private static final String SERVICE_ACCOUNT_CERT_PATH_DEFAULT = "/var/run/secrets/kubernetes.io/serviceaccount/ca.crt";
    private static final String SERVICE_ACCOUNT_CERT_PATH_ENV = "OCI_KUBERNETES_SERVICE_ACCOUNT_CERT_PATH";

    private final LazyValue<Optional<AbstractAuthenticationDetailsProvider>> provider;

    AuthenticationMethodOkeWorkload(OciConfig config) {
        provider = createProvider(config);
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public Optional<AbstractAuthenticationDetailsProvider> provider() {
        return provider.get();
    }

    private static LazyValue<Optional<AbstractAuthenticationDetailsProvider>> createProvider(OciConfig config) {
        return LazyValue.create(() -> {
            if (available()) {
                return Optional.of(OkeWorkloadIdentityAuthenticationDetailsProvider.builder()
                                           .build());

            }
            return Optional.empty();
        });
    }

    /**
     * Returns true if the configured (or default) path with OKE account certificate path exists and is a
     * regular file.
     */
    private static boolean available() {
        String usedPath = System.getenv(SERVICE_ACCOUNT_CERT_PATH_ENV);
        usedPath = usedPath == null ? SERVICE_ACCOUNT_CERT_PATH_DEFAULT : usedPath;

        Path certPath = Paths.get(usedPath);

        if (!Files.exists(certPath)) {
            if (LOGGER.isLoggable(Level.TRACE)) {
                LOGGER.log(Level.TRACE, "OKE Workload Authentication Details Provider is not available, as "
                        + "the certificate file does not exist: " + certPath.toAbsolutePath());
            }
            return false;
        }
        if (Files.isRegularFile(certPath)) {
            return true;
        }

        if (LOGGER.isLoggable(Level.TRACE)) {
            LOGGER.log(Level.TRACE, "OKE Workload Authentication Details Provider is not available, as "
                    + "the certificate file is not a regular file: " + certPath.toAbsolutePath());
        }
        return false;
    }
}
