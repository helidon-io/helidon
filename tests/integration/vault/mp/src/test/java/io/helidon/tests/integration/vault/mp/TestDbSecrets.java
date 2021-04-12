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

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import io.helidon.integrations.vault.cdi.VaultCdiExtension;
import io.helidon.integrations.vault.secrets.database.DbCreateRole;
import io.helidon.integrations.vault.secrets.database.DbCredentials;
import io.helidon.integrations.vault.secrets.database.DbSecrets;
import io.helidon.integrations.vault.secrets.database.MySqlConfigureRequest;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.helidon.config.testing.OptionalMatcher.present;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@DisableDiscovery
@AddExtension(VaultCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestDbSecrets {
    @Inject
    DbSecrets db;

    @Inject
    Sys vault;

    @Inject
    @ConfigProperty(name = "vault.db.host", defaultValue = "mysql")
    String dbHost;

    @Test
    @Order(1)
    void initializeEngines() {
        vault.enableEngine(DbSecrets.ENGINE);
    }

    @Test
    @Order(2)
    void testAddDatabase() {
        db.configure(MySqlConfigureRequest.builder("{{username}}:{{password}}@tcp(" + dbHost + ":3306)/")
                             .name("mysql")
                             .username("root")
                             .password("root")
                             .maxOpenConnections(5)
                             .maxConnectionLifetime(Duration.ofMinutes(1))
                             .addAllowedRole("readonly"));
    }

    @Test
    @Order(3)
    void testAddRole() {
        db.createRole(DbCreateRole.Request.builder()
                              .name("readonly")
                              .dbName("mysql")
                              .addCreationStatement("CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'")
                              .addCreationStatement("GRANT SELECT ON *.* TO '{{name}}'@'%'"));
    }

    @Test
    @Order(4)
    void testGetSecrets() {
        List<String> list = db.list("");

        Optional<DbCredentials> maybeCreds = db.get("readonly");

        assertThat(maybeCreds, is(present()));

        DbCredentials mysql = maybeCreds.get();

        String username = mysql.username();
        String password = mysql.password();
        // todo connect to database
        System.out.println("username = " + username);
        System.out.println("password = " + password);
    }

    @Test
    @Order(5)
    void testRemoveRole() {
        db.deleteRole("readonly");
    }

    @Test
    @Order(6)
    @Disabled
    void testRemoveDatabase() {
        // we cannot do this, as then the credentials failed to get revoked when we disable the engine
        db.delete("mysql");
    }

    @Test
    @Order(100)
    void removeEngines() {
        vault.disableEngine(DbSecrets.ENGINE);
    }
}
