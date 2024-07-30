/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.examples.integrations.vault.hcp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.Base64Value;
import io.helidon.http.Status;
import io.helidon.integrations.common.rest.ApiResponse;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.auths.approle.AppRoleAuth;
import io.helidon.integrations.vault.auths.approle.AppRoleVaultAuth;
import io.helidon.integrations.vault.auths.approle.CreateAppRole;
import io.helidon.integrations.vault.auths.approle.GenerateSecretId;
import io.helidon.integrations.vault.secrets.cubbyhole.CreateCubbyhole;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecrets;
import io.helidon.integrations.vault.secrets.cubbyhole.DeleteCubbyhole;
import io.helidon.integrations.vault.secrets.database.DbConfigure;
import io.helidon.integrations.vault.secrets.database.DbCreateRole;
import io.helidon.integrations.vault.secrets.database.DbCredentials;
import io.helidon.integrations.vault.secrets.database.DbSecrets;
import io.helidon.integrations.vault.secrets.database.MySqlConfigureRequest;
import io.helidon.integrations.vault.secrets.kv1.CreateKv1;
import io.helidon.integrations.vault.secrets.kv1.DeleteKv1;
import io.helidon.integrations.vault.secrets.kv1.Kv1Secrets;
import io.helidon.integrations.vault.secrets.kv2.CreateKv2;
import io.helidon.integrations.vault.secrets.kv2.DeleteKv2;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secret;
import io.helidon.integrations.vault.secrets.kv2.Kv2Secrets;
import io.helidon.integrations.vault.secrets.transit.CreateKey;
import io.helidon.integrations.vault.secrets.transit.Decrypt;
import io.helidon.integrations.vault.secrets.transit.DecryptBatch;
import io.helidon.integrations.vault.secrets.transit.DeleteKey;
import io.helidon.integrations.vault.secrets.transit.Encrypt;
import io.helidon.integrations.vault.secrets.transit.EncryptBatch;
import io.helidon.integrations.vault.secrets.transit.Hmac;
import io.helidon.integrations.vault.secrets.transit.Sign;
import io.helidon.integrations.vault.secrets.transit.TransitSecrets;
import io.helidon.integrations.vault.secrets.transit.UpdateKeyConfig;
import io.helidon.integrations.vault.secrets.transit.Verify;
import io.helidon.integrations.vault.sys.DisableEngine;
import io.helidon.integrations.vault.sys.EnableEngine;
import io.helidon.integrations.vault.sys.Sys;
import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultTest {
    private static final String TRANSIT_ENCRYPTION_KEY = "encryption-key";
    private static final String TRANSIT_SIGNATURE_KEY = "signature-key";
    private static final String TRANSIT_HMAC_KEY = "hmac-key";
    private static final String APPROLE_POLICY_NAME = "approle_policy";
    private static final String APPROLE_ROLE_NAME = "approle_role";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.0.36");
    private static final DockerImageName HCP_VAULT_IMAGE = DockerImageName.parse("vault:1.11.3");

    private static final Network NETWORK = Network.newNetwork();
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "changeit";
    @Container
    private static final MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE)
            .withUsername(MYSQL_USER)
            .withPassword(MYSQL_PASSWORD)
            .withNetworkAliases("mysql")
            .withDatabaseName("pokemon")
            .withNetwork(NETWORK);

    @Container
    private static final VaultContainer<?> VAULT_CONTAINER = new VaultContainer<>(HCP_VAULT_IMAGE)
            .withVaultToken("myroot")
            .withNetwork(NETWORK)
            .dependsOn(MY_SQL_CONTAINER);

    // it seems that test containers run each method on a different test class instance
    private static Vault tokenVault;
    private static String vaultAddress;

    @BeforeAll
    static void setup() {
        LogConfig.configureRuntime();
    }

    @Test
    @Order(1)
    void validateContainers() {
        assertThat("MySQL must be running", MY_SQL_CONTAINER.isRunning(), is(true));
        assertThat("Vault must be running", VAULT_CONTAINER.isRunning(), is(true));
        vaultAddress = "http://" + VAULT_CONTAINER.getHost() + ":" + VAULT_CONTAINER.getMappedPort(8200);
    }

    @Test
    @Order(2)
    void prepareVaultAndSecrets() {
        tokenVault = Vault.builder()
                .address(vaultAddress)
                .token("myroot")
                .updateWebClient(it -> it.connectTimeout(Duration.ofSeconds(5))
                        .readTimeout(Duration.ofSeconds(5)))
                .build();
    }

    @Test
    @Order(3)
    void testKv1() {
        // kv1 is not enabled by default
        Sys sys = tokenVault.sys(Sys.API);
        EnableEngine.Response enableResponse = sys.enableEngine(Kv1Secrets.ENGINE);

        assertThat("Enable kv1 engine, got response status: " + enableResponse.status(),
                   enableResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        Kv1Secrets secrets = tokenVault.secrets(Kv1Secrets.ENGINE);
        String secretPath = "first/secret";

        // create secret
        CreateKv1.Response createResponse = secrets.create(secretPath, Map.of("key", "secretValue"));
        assertThat("Create secret, got response status: " + createResponse.status(),
                   createResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        // get secret
        Optional<Secret> maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalPresent());
        Secret secret = maybeSecret.get();
        assertThat(secret.path(), is(secretPath));
        assertThat(secret.values(), is(Map.of("key", "secretValue")));

        DeleteKv1.Response deleteResponse = secrets.delete(secretPath);
        assertThat("Delete secret, got response status: " + deleteResponse.status(),
                   deleteResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        List<String> secretList = secrets.list();
        assertThat(secretList, is(List.of()));

        maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalEmpty());

        // and disable engine
        DisableEngine.Response disableResponse = sys.disableEngine(Kv1Secrets.ENGINE);

        assertThat("Disable kv1 engine, got response status: " + disableResponse.status(),
                   disableResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));
    }

    @Test
    @Order(3)
    void testKv2() {
        Kv2Secrets secrets = tokenVault.secrets(Kv2Secrets.ENGINE);
        String secretPath = "first/secret";

        // create secret
        CreateKv2.Response createResponse = secrets.create(secretPath, Map.of("key", "secretValue"));
        assertThat("Create secret, got response status: " + createResponse.status(),
                   createResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        // get secret
        Optional<Kv2Secret> maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalPresent());
        Kv2Secret secret = maybeSecret.get();
        assertThat(secret.path(), is(secretPath));
        assertThat(secret.values(), is(Map.of("key", "secretValue")));
        assertThat(secret.metadata().version(), is(1));

        List<String> secretList = secrets.list();
        assertThat(secretList, hasItems("first/"));

        secretList = secrets.list("first");
        assertThat(secretList, hasItems("secret"));

        DeleteKv2.Response deleteResponse = secrets.delete(secretPath, 1);
        assertThat("Delete secret, got response status: " + deleteResponse.status(),
                   deleteResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalEmpty());
    }

    @Test
    @Order(3)
    void testCubbyhole() {
        CubbyholeSecrets secrets = tokenVault.secrets(CubbyholeSecrets.ENGINE);
        String secretPath = "first/secret";

        // create secret
        CreateCubbyhole.Response createResponse = secrets.create(secretPath, Map.of("key", "secretValue"));
        assertThat("Create secret, got response status: " + createResponse.status(),
                   createResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        // get secret
        Optional<Secret> maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalPresent());
        Secret secret = maybeSecret.get();
        assertThat(secret.path(), is(secretPath));
        assertThat(secret.values(), is(Map.of("key", "secretValue")));

        DeleteCubbyhole.Response deleteResponse = secrets.delete(secretPath);
        assertThat("Delete secret, got response status: " + deleteResponse.status(),
                   deleteResponse.status().family(),
                   is(Status.Family.SUCCESSFUL));

        List<String> secretList = secrets.list();
        assertThat(secretList, is(List.of()));

        maybeSecret = secrets.get(secretPath);

        assertThat(maybeSecret, optionalEmpty());
    }

    @Test
    @Order(3)
    void testDatabase() throws SQLException {
        int mysqlPort = MY_SQL_CONTAINER.getMappedPort(3306);
        String mysqlHost = MY_SQL_CONTAINER.getHost();
        if (mysqlHost.startsWith("/")) {
            mysqlHost = mysqlHost.substring(1);
        }
        // database is not enabled by default
        Sys sys = tokenVault.sys(Sys.API);
        EnableEngine.Response enableResponse = sys.enableEngine(DbSecrets.ENGINE);
        assertSuccess("Enable database engine", enableResponse);
        DbSecrets database = tokenVault.secrets(DbSecrets.ENGINE);

        // configure connection
        String connectionTemplate = "{{username}}:{{password}}@tcp"
                + "(mysql:3306)/";
        DbConfigure.Response dbConfigResponse = database
                .configure(MySqlConfigureRequest.builder(connectionTemplate)
                                   .name("mysql")
                                   .username(MYSQL_USER)
                                   .password(MYSQL_PASSWORD)
                                   .maxOpenConnections(5)
                                   .maxConnectionLifetime(Duration.ofMinutes(1))
                                   .addAllowedRole("readonly"));
        assertSuccess("Configure MySQL", dbConfigResponse);

        // add role
        DbCreateRole.Response roleResponse = database.createRole(
                DbCreateRole.Request.builder()
                        .name("readonly")
                        .dbName("mysql")
                        .addCreationStatement("CREATE USER '{{name}}'@'%' IDENTIFIED BY '{{password}}'")
                        .addCreationStatement("GRANT SELECT ON *.* TO '{{name}}'@'%'"));
        assertSuccess("Create MySQL role", roleResponse);

        // verify we can get secrets to connect to the database
        Optional<DbCredentials> maybeCreds = database.get("readonly");

        assertThat(maybeCreds, optionalPresent());

        DbCredentials mysql = maybeCreds.get();

        String username = mysql.username();
        String password = mysql.password();

        String address = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/";
        com.mysql.jdbc.Driver.class.getName();
        try (Connection conn = DriverManager.getConnection(address, username, password)) {
            assertThat(conn, notNullValue());
        }

        // remove role
        assertSuccess("Delete MySQL role", database.deleteRole("readonly"));
        // remove db
        assertSuccess("Delete database", database.delete("mysql"));
    }

    @Test
    @Order(3)
    void testTransit() {
        // kv1 is not enabled by default
        Sys sys = tokenVault.sys(Sys.API);
        EnableEngine.Response enableResponse = sys.enableEngine(TransitSecrets.ENGINE);
        assertSuccess("Enable transit engine", enableResponse);

        TransitSecrets secrets = tokenVault.secrets(TransitSecrets.ENGINE);

        /*
         Create keys
         */
        CreateKey.Response createResponse = secrets.createKey(CreateKey.Request.builder()
                                                                      .name(TRANSIT_ENCRYPTION_KEY));
        assertSuccess("Create encryption key", createResponse);

        createResponse = secrets.createKey(CreateKey.Request.builder()
                                                   .name(TRANSIT_SIGNATURE_KEY)
                                                   .type("rsa-2048"));
        assertSuccess("Create signature key", createResponse);
        createResponse = secrets.createKey(CreateKey.Request.builder()
                                                   .name(TRANSIT_HMAC_KEY));
        assertSuccess("Create hmac key", createResponse);

        /*
         Test operations
         */
        transitBatch(secrets);
        transitEncryption(secrets);
        transitSignature(secrets);
        transitHmac(secrets);


        /*
        Delete keys
         */
        assertSuccess("Update key config", secrets.updateKeyConfig(UpdateKeyConfig.Request.builder()
                                                                           .name(TRANSIT_ENCRYPTION_KEY)
                                                                           .allowDeletion(true)));

        assertSuccess("Delete encryption key", secrets.deleteKey(DeleteKey.Request.create(TRANSIT_ENCRYPTION_KEY)));

        // and disable engine
        assertSuccess("Disable transit engine", sys.disableEngine(TransitSecrets.ENGINE));
    }

    @Test
    @Order(4)
    void testAppRole() {
        Sys sys = tokenVault.sys(Sys.API);

        assertSuccess("Enable App Role auth", sys.enableAuth(AppRoleAuth.AUTH_METHOD));
        assertSuccess("Create App Role policy", sys.createPolicy(APPROLE_POLICY_NAME, VaultPolicy.POLICY));

        AppRoleAuth auth = tokenVault.auth(AppRoleAuth.AUTH_METHOD);

        assertSuccess("Create App Role", auth.createAppRole(
                CreateAppRole.Request.builder()
                        .roleName(APPROLE_ROLE_NAME)
                        .addTokenPolicy(APPROLE_POLICY_NAME)
                        .tokenExplicitMaxTtl(Duration.ofMinutes(1))));

        Optional<String> roleIdResponse = auth.readRoleId(APPROLE_ROLE_NAME);
        assertThat(roleIdResponse, optionalPresent());
        String roleId = roleIdResponse.get();

        GenerateSecretId.Response secretIdResponse = auth.generateSecretId(GenerateSecretId.Request.builder()
                                                                                   .roleName(APPROLE_ROLE_NAME)
                                                                                   .addMetadata("name", "helidon"));
        assertSuccess("Generate secret id", secretIdResponse);

        String secretId = secretIdResponse.secretId();
        Vault appRoleVault = Vault.builder()
                .address(vaultAddress)
                .addVaultAuth(AppRoleVaultAuth.builder()
                                      .appRoleId(roleId)
                                      .secretId(secretId)
                                      .build())
                .build();

        Kv2Secrets secrets = appRoleVault.secrets(Kv2Secrets.ENGINE);
        String secretPath = "myapprole/secret";
        assertSuccess("Create secrets", secrets.create(secretPath, Map.of("secret-key", "secretValue",
                                                                          "secret-user", "username")));

        Optional<Kv2Secret> secretResponse = secrets.get(secretPath);
        assertThat(secretResponse, optionalPresent());
        Kv2Secret secret = secretResponse.get();
        assertThat(secret.value("secret-key"), optionalValue(is("secretValue")));
        assertThat(secret.value("secret-user"), optionalValue(is("username")));

        assertSuccess("Delete secrets", secrets.deleteAll(secretPath));
    }

    private void transitHmac(TransitSecrets secrets) {
        String secret = "Hello World";
        Base64Value data = Base64Value.create(secret);

        Hmac.Response signResponse = secrets.hmac(Hmac.Request.builder()
                                                          .hmacKeyName(TRANSIT_HMAC_KEY)
                                                          .data(data));
        assertSuccess("HMAC", signResponse);

        String hmac = signResponse.hmac();
        assertThat(hmac, not(""));
        assertThat(hmac, not(secret));

        Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                                .digestKeyName(TRANSIT_HMAC_KEY)
                                                                .hmac(hmac)
                                                                .data(data));
        assertSuccess("Verify HMAC", verifyResponse);
        assertThat("HMAC should be valid", verifyResponse.isValid(), is(true));
    }

    private void transitSignature(TransitSecrets secrets) {
        String secret = "Hello World";
        Base64Value data = Base64Value.create(secret);

        Sign.Response signResponse = secrets.sign(Sign.Request.builder()
                                                          .signatureKeyName(TRANSIT_SIGNATURE_KEY)
                                                          .data(data));
        assertSuccess("Sign", signResponse);

        String signature = signResponse.signature();
        assertThat(signature, not(""));
        assertThat(signature, not(secret));

        Verify.Response verifyResponse = secrets.verify(Verify.Request.builder()
                                                                .digestKeyName(TRANSIT_SIGNATURE_KEY)
                                                                .signature(signature)
                                                                .data(data));
        assertSuccess("Verify", verifyResponse);
        assertThat("Signature should be valid", verifyResponse.isValid(), is(true));
    }

    private void transitEncryption(TransitSecrets secrets) {
        String secret = "text";
        Encrypt.Response encryptResponse = secrets.encrypt(Encrypt.Request.builder()
                                                                   .encryptionKeyName(TRANSIT_ENCRYPTION_KEY)
                                                                   .data(Base64Value.create(secret)));
        assertSuccess("Encrypt", encryptResponse);

        String cipherText = encryptResponse.encrypted().cipherText();
        assertThat(cipherText, not(""));
        assertThat(cipherText, not(secret));

        Decrypt.Response decryptResponse = secrets.decrypt(Decrypt.Request.builder()
                                                                   .cipherText(cipherText)
                                                                   .encryptionKeyName(TRANSIT_ENCRYPTION_KEY));
        assertSuccess("Decrypt", decryptResponse);
        String decrypted = decryptResponse.decrypted().toDecodedString();
        assertThat(decrypted, is(secret));
    }

    private void transitBatch(TransitSecrets secrets) {
        // batch
        String[] data = {"one", "two", "three", "four"};
        EncryptBatch.Request encryptBatch = EncryptBatch.Request.builder()
                .encryptionKeyName(TRANSIT_ENCRYPTION_KEY);
        DecryptBatch.Request decryptBatch = DecryptBatch.Request.builder()
                .encryptionKeyName(TRANSIT_ENCRYPTION_KEY);

        for (String datum : data) {
            encryptBatch.addEntry(EncryptBatch.BatchEntry.create(Base64Value.create(datum)));
        }

        EncryptBatch.Response response = secrets.encrypt(encryptBatch);
        assertSuccess("Encrypt batch", response);

        List<Encrypt.Encrypted> encrypted = response.batchResult();
        Encrypt.Encrypted encryptedOne = encrypted.get(0);
        String oneCipherText = encryptedOne.cipherText();
        assertThat(oneCipherText, not(""));
        assertThat(oneCipherText, not("one"));

        for (Encrypt.Encrypted encryptedValue : encrypted) {
            decryptBatch.addEntry(DecryptBatch.BatchEntry.create(encryptedValue.cipherText()));
        }
        DecryptBatch.Response decryptResponse = secrets.decrypt(decryptBatch);
        assertSuccess("Decrypt batch", decryptResponse);

        String[] decrypted = decryptResponse.batchResult()
                .stream()
                .map(Base64Value::toDecodedString)
                .toArray(String[]::new);
        assertThat(decrypted, is(data));
    }

    private void assertSuccess(String action, ApiResponse response) {
        assertThat(action + ", got response status: " + response.status(),
                   response.status().family(),
                   is(Status.Family.SUCCESSFUL));
    }
}
