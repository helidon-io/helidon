/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.oci.vault.reactive;

import io.helidon.common.LogConfig;
import io.helidon.config.Config;
import io.helidon.integrations.oci.vault.OciVault;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

public class OciVaultMain {
    public static void main(String[] args) {
        LogConfig.configureRuntime();

        // as I cannot share my configuration of OCI, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        Config vaultConfig = config.get("oci.vault");
        // the following three parameters are required
        String vaultOcid = vaultConfig.get("vault-ocid").asString().get();
        String compartmentOcid = vaultConfig.get("compartment-ocid").asString().get();
        String encryptionKey = vaultConfig.get("encryption-key-ocid").asString().get();
        String signatureKey = vaultConfig.get("signature-key-ocid").asString().get();

        // this requires OCI configuration in the usual place
        // ~/.oci/config
        OciVault ociVault = OciVault.create(config.get("oci"));

        WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/vault", new VaultService(ociVault,
                                                                      vaultOcid,
                                                                      compartmentOcid,
                                                                      encryptionKey,
                                                                      signatureKey)))
                .build()
                .start()
                .await();
    }

    private static Config buildConfig() {
        return Config.builder()
                .sources(
                        // you can use this file to override the defaults that are built-in
                        file(System.getProperty("user.home") + "/helidon/conf/examples.yaml").optional(),
                        // in jar file (see src/main/resources/application.yaml)
                        classpath("application.yaml"))
                .build();
    }
}
