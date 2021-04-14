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

package io.helidon.examples.security.vaults;

import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import static io.helidon.config.ConfigSources.classpath;
import static io.helidon.config.ConfigSources.file;

/**
 * Main class of the example based on configuration.
 */
public final class VaultsExampleMain {
    private VaultsExampleMain() {
    }

    /**
     * Start the server.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        // as I cannot share my configuration of OCI, let's combine the configuration
        // from my home directory with the one compiled into the jar
        // when running this example, you can either update the application.yaml in resources directory
        // or use the same approach
        Config config = buildConfig();

        System.out.println("This example requires a valid OCI Vault, Secret and keys configured. It also requires "
                                   + "a Hashicorp Vault running with preconfigured data. Please see README.md");

        Security security = Security.create(config.get("security"));

        WebServer server = WebServer.builder()
                .config(config.get("server"))
                .routing(Routing.builder()
                                 .register("/secrets", new SecretsService(security))
                                 .register("/encryption", new EncryptionService(security))
                                 .register("/digests", new DigestService(security)))
                .build()
                .start()
                .await(10, TimeUnit.SECONDS);

        System.out.println("Server started on port: " + server.port());
        String baseAddress = "http://localhost:" + server.port() + "/";

        System.out.println("Secrets endpoints:");
        System.out.println();
        System.out.println("OCI secret:");
        System.out.println("\t" + baseAddress + "secrets/password");
        System.out.println("Config secret:");
        System.out.println("\t" + baseAddress + "secrets/token");
        System.out.println("HCP Vault secret:");
        System.out.println("\t" + baseAddress + "secrets/username");
        System.out.println();

        System.out.println("Encryption endpoints:");
        System.out.println("OCI encrypted:");
        System.out.println("\t" + baseAddress + "encryption/encrypt/crypto-1/text");
        System.out.println("\t" + baseAddress + "encryption/decrypt/crypto-1/cipherText");
        System.out.println("Config encrypted:");
        System.out.println("\t" + baseAddress + "encryption/encrypt/crypto-2/text");
        System.out.println("\t" + baseAddress + "encryption/decrypt/crypto-2/cipherText");
        System.out.println("HCP Vault encrypted:");
        System.out.println("\t" + baseAddress + "encryption/encrypt/crypto-3/text");
        System.out.println("\t" + baseAddress + "encryption/decrypt/crypto-3/cipherText");
        System.out.println();

        System.out.println("Signature/HMAC endpoints:");
        System.out.println("OCI Signature:");
        System.out.println("\t" + baseAddress + "digests/digest/sig-1/text");
        System.out.println("\t" + baseAddress + "digests/verify/sig-1/text/signature");
        System.out.println("HCP Vault Signature:");
        System.out.println("\t" + baseAddress + "digests/digest/sig-2/text");
        System.out.println("\t" + baseAddress + "digests/digest/sig-2/text/signature");
        System.out.println("HCP Vault HMAC:");
        System.out.println("\t" + baseAddress + "digests/digest/hmac-1/text");
        System.out.println("\t" + baseAddress + "digests/digest/hmac-2/text/hmac");
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
