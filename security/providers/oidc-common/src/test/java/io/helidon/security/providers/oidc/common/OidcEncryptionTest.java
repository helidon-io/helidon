/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.security.spi.EncryptionProvider;
import io.helidon.security.spi.EncryptionProvider.EncryptionSupport;
import io.helidon.security.spi.ProviderConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OidcEncryptionTest {
    private static final String SECRET_FILE = ".helidon-oidc-secret";
    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ,
                                                                      PosixFilePermission.OWNER_WRITE);

    @TempDir
    private Path tempDir;

    @Test
    void rejectsExistingSecretSymlink() throws Exception {
        Path target = tempDir.resolve("target-secret");
        Files.writeString(target, "known-secret", StandardCharsets.UTF_8);

        try {
            Files.createSymbolicLink(tempDir.resolve(SECRET_FILE), target.getFileName());
        } catch (UnsupportedOperationException | FileSystemException e) {
            assumeTrue(false, "Symbolic links are not supported");
        }

        ProcessResult result = runFallbackSecret(tempDir);

        assertThat(result.output(), result.exitCode(), is(2));
        assertThat(result.output(), containsString("regular file"));
    }

    @Test
    void rejectsExistingPosixSecretReadableByGroup() throws Exception {
        assumeTrue(posixSupported(tempDir), "POSIX permissions are not supported");

        Path secret = tempDir.resolve(SECRET_FILE);
        Files.writeString(secret, "known-secret", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(secret, Set.of(PosixFilePermission.OWNER_READ,
                                                     PosixFilePermission.OWNER_WRITE,
                                                     PosixFilePermission.GROUP_READ));

        ProcessResult result = runFallbackSecret(tempDir);

        assertThat(result.output(), result.exitCode(), is(2));
        assertThat(result.output(), containsString("permissions"));
    }

    @Test
    void createsFallbackSecretWithOwnerOnlyPosixPermissions() throws Exception {
        ProcessResult result = runFallbackSecret(tempDir);

        assertThat(result.output(), result.exitCode(), is(0));

        Path secret = tempDir.resolve(SECRET_FILE);
        assertThat(Files.isRegularFile(secret), is(true));
        if (posixSupported(tempDir)) {
            assertThat(Files.getPosixFilePermissions(secret), is(OWNER_ONLY));
        }
    }

    @Test
    void acceptsExistingPosixSecretWithOwnerOnlyPermissions() throws Exception {
        assumeTrue(posixSupported(tempDir), "POSIX permissions are not supported");

        Path secret = tempDir.resolve(SECRET_FILE);
        Files.writeString(secret, "known-secret", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(secret, OWNER_ONLY);

        ProcessResult result = runFallbackSecret(tempDir);

        assertThat(result.output(), result.exitCode(), is(0));
        assertThat(Files.readString(secret, StandardCharsets.UTF_8), is("known-secret"));
    }

    @Test
    void acceptsExistingPosixSecretWithOwnerReadOnlyPermissions() throws Exception {
        assumeTrue(posixSupported(tempDir), "POSIX permissions are not supported");

        Path secret = tempDir.resolve(SECRET_FILE);
        Files.writeString(secret, "known-secret", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(secret, Set.of(PosixFilePermission.OWNER_READ));

        try {
            ProcessResult result = runFallbackSecret(tempDir);

            assertThat(result.output(), result.exitCode(), is(0));
            assertThat(Files.readString(secret, StandardCharsets.UTF_8), is("known-secret"));
        } finally {
            Files.setPosixFilePermissions(secret, OWNER_ONLY);
        }
    }

    @Test
    void reusesExistingSecretAcrossProcesses() throws Exception {
        Path secret = tempDir.resolve(SECRET_FILE);
        Files.writeString(secret, "known-secret", StandardCharsets.UTF_8);
        if (posixSupported(tempDir)) {
            Files.setPosixFilePermissions(secret, OWNER_ONLY);
        }

        ProcessResult result = runFallbackSecret(tempDir);

        assertThat(result.output(), result.exitCode(), is(0));
        assertThat(Files.readString(secret, StandardCharsets.UTF_8), is("known-secret"));

        ProcessResult encrypt = runFallbackSecret(tempDir, "encrypt");

        assertThat(encrypt.output(), encrypt.exitCode(), is(0));
        assertThat(encrypt.output(), encrypt.output().isBlank(), is(false));

        ProcessResult decrypt = runFallbackSecret(tempDir, "decrypt", encrypt.lastLine());

        assertThat(decrypt.output(), decrypt.exitCode(), is(0));
        assertThat(decrypt.lastLine(), is("test"));
    }

    @Test
    void usesConfiguredPasswordWithoutReadingFallbackSecret() throws Exception {
        Files.createDirectory(tempDir.resolve(SECRET_FILE));

        ProcessResult result = runFallbackSecret(tempDir, "password");

        assertThat(result.output(), result.exitCode(), is(0));
    }

    @Test
    void usesConfiguredNameWithoutReadingFallbackSecret() throws Exception {
        Files.createDirectory(tempDir.resolve(SECRET_FILE));

        ProcessResult result = runFallbackSecret(tempDir, "name");

        assertThat(result.output(), result.exitCode(), is(0));
    }

    private static boolean posixSupported(Path path) {
        return Files.getFileAttributeView(path, PosixFileAttributeView.class) != null;
    }

    private static ProcessResult runFallbackSecret(Path directory) throws Exception {
        return runFallbackSecret(directory, "fallback");
    }

    private static ProcessResult runFallbackSecret(Path directory, String mode, String... arguments) throws Exception {
        String javaCommand = Path.of(System.getProperty("java.home"),
                                     "bin",
                                     System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
        String[] command = new String[5 + arguments.length];
        command[0] = javaCommand;
        command[1] = "-cp";
        command[2] = System.getProperty("java.class.path");
        command[3] = FallbackSecretMain.class.getName();
        command[4] = mode;
        System.arraycopy(arguments, 0, command, 5, arguments.length);
        Process process = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true)
                .start();
        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            completed = process.waitFor(5, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(output, completed, is(true));
        return new ProcessResult(process.exitValue(), output);
    }

    private record ProcessResult(int exitCode, String output) {
        String lastLine() {
            String[] lines = output.lines().toArray(String[]::new);
            return lines[lines.length - 1];
        }
    }

    public static final class FallbackSecretMain {
        private FallbackSecretMain() {
        }

        public static void main(String[] args) {
            try {
                switch (args[0]) {
                case "name" -> useNamedEncryption();
                case "password" -> use(OidcEncryption.create("test", null, "changeit".toCharArray()));
                case "encrypt" -> encrypt();
                case "decrypt" -> decrypt(args[1]);
                default -> use(OidcEncryption.create("test", null, null));
                }
            } catch (Throwable t) {
                t.printStackTrace(System.out);
                System.exit(2);
            }
        }

        private static void useNamedEncryption() {
            EncryptionProvider<ProviderConfig> provider = new EncryptionProvider<>() {
                @Override
                public EncryptionSupport encryption(Config config) {
                    return support();
                }

                @Override
                public EncryptionSupport encryption(ProviderConfig providerConfig) {
                    return support();
                }
            };
            Security security = Security.builder()
                    .addEncryption("test-name", provider, new ProviderConfig() {
                    })
                    .build();
            Context context = Context.create();
            context.register(security);
            Contexts.runInContext(context, () -> use(OidcEncryption.create("test", "test-name", null)));
        }

        private static EncryptionSupport support() {
            return EncryptionSupport.create(bytes -> Base64.getEncoder().encodeToString(bytes),
                                            encrypted -> Base64.getDecoder().decode(encrypted));
        }

        private static void use(EncryptionSupport encryption) {
            String cipherText = encryption.encrypt("test".getBytes(StandardCharsets.UTF_8));
            encryption.decrypt(cipherText);
        }

        private static void encrypt() {
            EncryptionSupport encryption = OidcEncryption.create("test", null, null);
            System.out.println(encryption.encrypt("test".getBytes(StandardCharsets.UTF_8)));
        }

        private static void decrypt(String cipherText) {
            EncryptionSupport encryption = OidcEncryption.create("test", null, null);
            System.out.println(new String(encryption.decrypt(cipherText), StandardCharsets.UTF_8));
        }
    }
}
