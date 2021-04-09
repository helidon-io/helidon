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

package io.helidon.tests.integration.vault.mp;

import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.se.SeContainer;
import javax.inject.Inject;

import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.k8s.ConfigureK8s;
import io.helidon.integrations.vault.auths.k8s.CreateRole;
import io.helidon.integrations.vault.auths.k8s.K8sAuthRx;
import io.helidon.integrations.vault.cdi.VaultCdiExtension;
import io.helidon.integrations.vault.cdi.VaultName;
import io.helidon.integrations.vault.cdi.VaultPath;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.secrets.kv2.Kv2SecretsRx;
import io.helidon.integrations.vault.sys.EnableEngine;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.Configuration;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.config.testing.OptionalMatcher.empty;
import static io.helidon.config.testing.OptionalMatcher.present;
import static io.helidon.config.testing.OptionalMatcher.value;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
@AddExtension(VaultCdiExtension.class)
@Configuration(configSources = "vault-cdi-extension.properties")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestKubernetesAuth {
    @Test
    @Order(0)
    void setupK8s(SeContainer container) {
        Sys sys = container.select(Sys.class).get();
        Vault vault = container.select(Vault.class).get();
        sys.enableAuth(K8sAuthRx.AUTH_METHOD);

        K8sAuthRx k8sAuth = vault.auth(K8sAuthRx.AUTH_METHOD);

        k8sAuth.configure(ConfigureK8s.Request.builder()
                                  .address("https://kubernetes.docker.internal:6443"))
                .await();

        sys.createPolicy("admin", VaultPolicy.POLICY);
        k8sAuth.createRole(CreateRole.Request.builder()
                                   .roleName("my-role")
                                   .addBoundServiceAccountName("*")
                                   .addBoundServiceAccountNamespace("default")
                                   .addTokenPolicy("admin"))
                .await();
    }

    @Test
    @Order(1)
    void initializeEngines(SeContainer container) {
        Sys sys = container.select(Sys.class, NamedLiteral.of("k8s")).get();
        sys.enableEngine(EnableEngine.Request.builder()
                                 .engine(Kv2SecretsRx.ENGINE)
                                 .path("kv"));
    }

    @Test
    @Order(2)
    @AddBean(SecretsHolder.class)
    void testSecrets(SeContainer container) {
        Kv2Secrets secrets = container.select(SecretsHolder.class).get().secrets();
        secrets.create("nested/path/secret", Map.of("first", "a value"));

        try {
            Optional<Kv2Secret> maybeSecret = secrets.get("nested/path/secret");
            assertThat(maybeSecret, is(present()));
            Kv2Secret theSecret = maybeSecret.get();
            assertThat(theSecret.value("first"), value(is("a value")));
            assertThat(theSecret.metadata().version(), is(1));
            assertThat(theSecret.metadata().deleted(), is(false));
            assertThat(theSecret.metadata().destroyed(), is(false));
            assertThat(theSecret.metadata().deletedTime(), is(empty()));
            assertThat(theSecret.metadata().createdTime(), notNullValue());
        } finally {
            // we want to remote the secret even if validation fails
            // delete latest version
            secrets.delete("nested/path/secret", 1);
            // destroy it
            secrets.destroy("nested/path/secret", 1);
            // delete all versions and history for this secret
            secrets.deleteAll("nested/path/secret");
        }
    }

    @Test
    @Order(100)
    void removeEngines(SeContainer container) {
        // using token authentication again
        Sys sys = container.select(Sys.class).get();
        sys.disableEngine("kv");
        sys.deletePolicy("admin");
        sys.disableAuth(K8sAuthRx.AUTH_METHOD.defaultPath());
    }

    @ApplicationScoped
    private static class SecretsHolder {
        @Inject
        @VaultPath("kv")
        @VaultName("k8s")
        Kv2Secrets secrets;

        Kv2Secrets secrets() {
            return secrets;
        }
    }
}
