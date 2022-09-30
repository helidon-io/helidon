package io.helidon.examples.integrations.vault.hcp.reactive;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.secrets.cubbyhole.CreateCubbyhole;
import io.helidon.integrations.vault.secrets.cubbyhole.CubbyholeSecretsRx;
import io.helidon.integrations.vault.secrets.cubbyhole.DeleteCubbyhole;
import io.helidon.logging.common.LogConfig;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.vault.VaultContainer;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalPresent;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8");
    private static final DockerImageName HCP_VAULT_IMAGE = DockerImageName.parse("vault:1.11.3");

    @Container
    private static final MySQLContainer mySql = new MySQLContainer(MYSQL_IMAGE)
            .withUsername("user")
            .withPassword("password")
            .withDatabaseName("pokemon");

    @Container
    private static final VaultContainer vaultContainer = new VaultContainer<>(HCP_VAULT_IMAGE)
            .withVaultToken("myroot")
            .dependsOn(mySql);

    // it seems that test containers run each method on a different test class instance
    private static Vault tokenVault;

    @BeforeAll
    static void setup() {
        LogConfig.configureRuntime();
    }

    @Test
    @Order(1)
    void validateContainers() {
        assertThat("MySQL must be running", mySql.isRunning(), is(true));
        assertThat("Vault must be running", vaultContainer.isRunning(), is(true));
    }

    @Test
    @Order(2)
    void prepareVaultAndSecrets() {
        tokenVault = Vault.builder()
                .address("http://localhost:" + vaultContainer.getMappedPort(8200))
                .token("myroot")
                .updateWebClient(it -> it.connectTimeout(TIMEOUT)
                        .readTimeout(TIMEOUT))
                .build();
        // TODO approle
        //        vaultSys = tokenVault.sys(SysRx.API);
        //
        //        kv1 = tokenVault.secrets(Kv1SecretsRx.ENGINE);
        //        kv2 = tokenVault.secrets(Kv2SecretsRx.ENGINE);
        //        database = tokenVault.secrets(DbSecretsRx.ENGINE);
        //        transit = tokenVault.secrets(TransitSecretsRx.ENGINE);
    }

    @Test
    @Order(3)
    void testCubbyhole() {
        CubbyholeSecretsRx cubbyhole = tokenVault.secrets(CubbyholeSecretsRx.ENGINE);
        String secretPath = "first/secret";

        // create secret
        CreateCubbyhole.Response createResponse = cubbyhole.create(secretPath, Map.of("key", "secretValue")).await(TIMEOUT);
        assertThat("Create secret, got response status: " + createResponse.status(),
                   createResponse.status().family(),
                   is(Http.Status.Family.SUCCESSFUL));

        // get secret
        Optional<Secret> maybeSecret = cubbyhole.get(secretPath)
                .await(TIMEOUT);

        assertThat(maybeSecret, optionalPresent());
        Secret secret = maybeSecret.get();
        assertThat(secret.path(), is(secretPath));
        assertThat(secret.values(), is(Map.of("key", "secretValue")));

        DeleteCubbyhole.Response deleteResponse = cubbyhole.delete(secretPath).await(TIMEOUT);
        assertThat("Delete secret, got response status: " + deleteResponse.status(),
                   deleteResponse.status().family(),
                   is(Http.Status.Family.SUCCESSFUL));

        List<String> secretList = cubbyhole.list().await(TIMEOUT);
        assertThat(secretList, is(List.of()));
    }

    @Test
    @Order(3)
    void testDatabase() {
        int mysqlPort = mySql.getMappedPort(3306);
    }
}
