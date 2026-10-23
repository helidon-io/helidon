/*
 * Copyright (c) 2018, 2026 Oracle and/or its affiliates.
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

package io.helidon.config.encryption;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test Main class (cli).
 */
public class MainTest {
    @Test
    public void testAesEncryption() {
        String masterPassword = "BigMasterPassowrd!!!";
        String secret = "some secret to encrypt";
        String[] args = new String[] {"aes", masterPassword, secret};

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.aes));
        assertThat(ecp.getMasterPassword(), is(masterPassword));
        assertThat(ecp.getSecret(), is(secret));

        String encrypted = ecp.encrypt();

        assertAll(
                () -> assertThat("Encrypted string should contain aes prefix: " + encrypted,
                                 encrypted.startsWith(EncryptionFilter.PREFIX_GCM)),
                () -> assertThat("Encrypted string should contain suffix \"}\": " + encrypted, encrypted.endsWith("}"))
        );

        String orig = EncryptionUtil.decryptAes(ecp.getMasterPassword().toCharArray(),
                                                encrypted.substring(EncryptionFilter.PREFIX_GCM.length(),
                                                                    encrypted.length() - 1));

        assertThat(orig, is(secret));

        Main.main(args);
    }

    @Test
    public void testEncEncryption() {
        String masterPassword = "BigMasterPassowrd!!!";
        String secret = "some secret to encrypt";
        String[] args = new String[] {"enc", masterPassword, secret, "600000"};

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.enc));
        assertThat(ecp.getMasterPassword(), is(masterPassword));
        assertThat(ecp.getAesIterations(), is(600000));
        assertThat(ecp.getSecret(), is(secret));

        String encrypted = ecp.encrypt();

        assertAll(
                () -> assertThat("Encrypted string should contain enc prefix: " + encrypted,
                                 encrypted.startsWith(EncryptionFilter.PREFIX_ENC)),
                () -> assertThat("Encrypted string should contain suffix \"}\": " + encrypted, encrypted.endsWith("}"))
        );

        String orig = EncryptionUtil.decryptAesEnvelope(ecp.getMasterPassword().toCharArray(),
                                                        encrypted.substring(EncryptionFilter.PREFIX_ENC.length(),
                                                                            encrypted.length() - 1));

        assertThat(orig, is(secret));
    }

    @Test
    public void testEncEncryptionRejectsTooFewIterations() {
        String[] args = new String[] {"enc", "BigMasterPassowrd!!!", "some secret to encrypt", "599999"};

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        Main.ValidationException exception = assertThrows(Main.ValidationException.class, () -> ecp.parse(args));

        assertThat(exception.getMessage(), is("PBKDF2 iterations must be between 600000 and 10000000, but was 599999"));
    }

    @Test
    public void testGcmEncryptionAlias() {
        String masterPassword = "BigMasterPassowrd!!!";
        String secret = "some secret to encrypt";
        String[] args = new String[] {"gcm", masterPassword, secret};

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.gcm));

        String encrypted = ecp.encrypt();
        assertAll(
                () -> assertThat("Encrypted string should contain gcm prefix: " + encrypted,
                                 encrypted.startsWith(EncryptionFilter.PREFIX_GCM)),
                () -> assertThat("Encrypted string should contain suffix \"}\": " + encrypted, encrypted.endsWith("}"))
        );

        String orig = EncryptionUtil.decryptAes(ecp.getMasterPassword().toCharArray(),
                                                encrypted.substring(EncryptionFilter.PREFIX_GCM.length(),
                                                                    encrypted.length() - 1));

        assertThat(orig, is(secret));
    }

    @Test
    void testLegacyCliWarnings(@TempDir Path tempDir) throws Exception {
        String masterPassword = "legacy-cli-master-password";
        String secret = "legacy-cli-secret";

        for (String algorithm : List.of("aes", "gcm")) {
            CliOutput output = runCli(tempDir, algorithm, masterPassword, secret);
            String encrypted = output.stdout().strip();

            assertAll(
                    () -> assertThat(algorithm + " warning", output.stderr(), allOf(
                            containsString("WARNING:"),
                            containsString("'aes' and 'gcm'"),
                            containsString("${GCM=...}"),
                            containsString("will be discontinued"),
                            containsString("helidon-config-encryption-{helidon-version}.jar enc masterPassword secretToEncrypt"),
                            containsString("Use the resulting ${ENC=...} value"),
                            not(containsString(masterPassword)),
                            not(containsString(secret)))),
                    () -> assertThat(algorithm + " stdout", encrypted,
                                     allOf(startsWith(EncryptionFilter.PREFIX_GCM), endsWith("}")))
            );

            String decrypted = EncryptionUtil.decryptAes(masterPassword.toCharArray(),
                    encrypted.substring(EncryptionFilter.PREFIX_GCM.length(), encrypted.length() - 1));
            assertThat(algorithm + " encrypted config value", decrypted, is(secret));
        }
    }

    @Test
    void testEncCliProducesValidConfigWithoutWarning(@TempDir Path tempDir) throws Exception {
        String masterPassword = "enc-cli-master-password";
        String secret = "enc-cli-secret";
        CliOutput output = runCli(tempDir, "enc", masterPassword, secret);
        String encrypted = output.stdout().strip();

        assertAll(
                () -> assertThat("enc stderr", output.stderr(), is("")),
                () -> assertThat("enc stdout", encrypted,
                                 allOf(startsWith(EncryptionFilter.PREFIX_ENC), endsWith("}")))
        );

        Config config = Config.builder()
                .disableFilterServices()
                .addSource(ConfigSources.create(Map.of(ConfigProperties.MASTER_PASSWORD_CONFIG_KEY, masterPassword,
                                                       ConfigProperties.REQUIRE_ENCRYPTION_CONFIG_KEY, "false",
                                                       "secret", encrypted)))
                .addFilter(EncryptionFilter.fromConfig())
                .build();
        assertThat(config.get("secret").asString().get(), is(secret));
    }

    @Test
    public void testRsaEncryption() {
        String keystorePath = "src/test/resources/.ssh/keystore.p12";
        String keystorePass = "j4c";
        String secret = "some secret to encrypt";
        String certAlias = "1";

        String[] args = new String[] {"rsa", keystorePath, keystorePass, certAlias, secret};
        PrivateKey pk = Keys.builder()
                .keystore(keystoreBuilder -> keystoreBuilder.keystore(Resource.create(Paths.get(keystorePath)))
                        .keyAlias("1")
                        .passphrase(keystorePass.toCharArray())
                )
                .build()
                .privateKey().orElseThrow(AssertionError::new);

        Main.EncryptionCliProcessor ecp = new Main.EncryptionCliProcessor();
        ecp.parse(args);
        assertThat(ecp.getAlgorithm(), is(Main.Algorithm.rsa));
        assertThat(ecp.getPublicKey(), notNullValue());
        assertThat(ecp.getSecret(), is(secret));

        String encrypted = ecp.encrypt();
        assertAll(() -> assertThat("Encrypted string should start with rsa prefix: " + encrypted,
                                   encrypted.startsWith(EncryptionFilter.PREFIX_RSA)),
                  () -> assertThat("Encrypted string should end with \"}\": " + encrypted, encrypted.endsWith("}")));

        String base64 = encrypted.substring(EncryptionFilter.PREFIX_RSA.length(), encrypted.length() - 1);
        Base64.getDecoder().decode(base64);

        String orig = EncryptionUtil.decryptRsa(pk, base64);

        assertThat(orig, is(secret));
        Main.main(args);
    }

    private CliOutput runCli(Path tempDir, String algorithm, String masterPassword, String secret) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path stdout = tempDir.resolve(algorithm + ".out");
        Path stderr = tempDir.resolve(algorithm + ".err");
        ProcessBuilder builder = new ProcessBuilder(java.toString(), "-cp", classpath,
                Main.class.getName(), algorithm, masterPassword, secret)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");

        Process process = builder.start();
        try {
            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            String output = Files.readString(stdout, StandardCharsets.UTF_8);
            String error = Files.readString(stderr, StandardCharsets.UTF_8);
            String diagnostic = algorithm + " CLI stdout: " + output + "; stderr: " + error;
            assertThat(diagnostic + "; completed", finished, is(true));
            assertThat(diagnostic + "; exit code", process.exitValue(), is(0));
            return new CliOutput(output, error);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertThat(algorithm + " CLI process terminated", process.waitFor(10, TimeUnit.SECONDS), is(true));
            }
        }
    }

    private record CliOutput(String stdout, String stderr) {
    }
}
