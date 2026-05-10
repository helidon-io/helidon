/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

import io.helidon.common.Base64Value;
import io.helidon.common.context.Contexts;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.security.Security;
import io.helidon.security.spi.EncryptionProvider.EncryptionSupport;

final class OidcEncryption {
    private static final System.Logger LOGGER = System.getLogger(OidcEncryption.class.getName());
    private static final Set<PosixFilePermission> OWNER_READ = Set.of(PosixFilePermission.OWNER_READ);
    private static final Set<PosixFilePermission> OWNER_READ_WRITE = Set.of(PosixFilePermission.OWNER_READ,
                                                                            PosixFilePermission.OWNER_WRITE);

    private OidcEncryption() {
    }

    static EncryptionSupport create(String type,
                                    String encryptionConfigurationName,
                                    char[] encryptionPassword) {
        EncryptionSupport found = null;

        if (encryptionConfigurationName != null) {
            found = nameBasedCipher(encryptionConfigurationName);
        }

        char[] masterPassword = encryptionPassword;
        if (encryptionPassword == null && found == null) {
            masterPassword = generateMasterPassword();
        }

        if (found != null && masterPassword != null) {
            throw new SecurityException("Cannot define both name based encryption and password based encryption for " + type);
        }
        if (found != null) {
            return found;
        }

        return symmetricCipher(masterPassword);
    }

    private static EncryptionSupport symmetricCipher(char[] masterPassword) {
        SymmetricCipher cipher = SymmetricCipher.create(masterPassword);
        return EncryptionSupport.create(
                bytes -> cipher.encrypt(Base64Value.create(bytes)).toBase64(),
                cipherText -> cipher.decrypt(Base64Value.createFromEncoded(cipherText)).toBytes()
        );
    }

    private static EncryptionSupport nameBasedCipher(String encryptionConfigurationName) {
        return EncryptionSupport.create(
                bytes -> securityFromContext().encrypt(encryptionConfigurationName, bytes),
                cipherText -> securityFromContext().decrypt(encryptionConfigurationName, cipherText)
        );
    }

    private static char[] generateMasterPassword() {
        Path path = Paths.get(".helidon-oidc-secret");
        Path parent = path.toAbsolutePath().getParent();
        boolean posix = parent != null && Files.getFileAttributeView(parent, PosixFileAttributeView.class) != null;
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return readMasterPassword(path, posix);
        }

        String password = UUID.randomUUID().toString();
        try {
            if (posix) {
                Path tempPath = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
                try {
                    ByteBuffer passwordBytes = StandardCharsets.UTF_8.encode(password);
                    Set<StandardOpenOption> options = Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    try (var channel = Files.newByteChannel(tempPath,
                                                            options,
                                                            PosixFilePermissions.asFileAttribute(OWNER_READ_WRITE))) {
                        while (passwordBytes.hasRemaining()) {
                            channel.write(passwordBytes);
                        }
                    }
                    Files.createLink(path, tempPath);
                } catch (FileAlreadyExistsException e) {
                    try {
                        Files.deleteIfExists(tempPath);
                    } catch (IOException deleteException) {
                        e.addSuppressed(deleteException);
                    }
                    return readMasterPassword(path, posix);
                } catch (IOException | RuntimeException e) {
                    try {
                        Files.deleteIfExists(tempPath);
                    } catch (IOException deleteException) {
                        e.addSuppressed(deleteException);
                    }
                    throw e;
                }
                try {
                    Files.deleteIfExists(tempPath);
                } catch (IOException e) {
                    LOGGER.log(Level.DEBUG,
                               "Could not delete temporary OIDC secret file " + tempPath.toAbsolutePath(),
                               e);
                }
            } else {
                ByteBuffer passwordBytes = StandardCharsets.UTF_8.encode(password);
                try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.CREATE_NEW,
                                                                     StandardOpenOption.WRITE))) {
                    while (passwordBytes.hasRemaining()) {
                        channel.write(passwordBytes);
                    }
                }
            }
        } catch (FileAlreadyExistsException e) {
            return readMasterPassword(path, posix);
        } catch (IOException e) {
            throw new SecurityException("Failed to create OIDC secret " + path.toAbsolutePath(), e);
        }
        LOGGER.log(Level.WARNING, "OIDC requires encryption configuration which was not provided. We will generate"
                               + " a password that will only work for the current service instance. To disable encryption,"
                               + " use cookie-encryption-enabled: false configuration, to configure master password, use"
                               + " cookie-encryption-password: ******* (must be configured to same value on all"
                               + " instances that share the cookie), to configure encryption using security"
                               + " (support for vaults), use"
                               + " cookie-encryption-name: name (must have corresponding encryption provider and"
                               + " configuration with the provided name in security), this also requires Security to be"
                               + " registered with current or global Context (this works automatically in Helidon MP)."
                               + " This message is logged just once, before generating the master password");

        return password.toCharArray();
    }

    private static char[] readMasterPassword(Path path, boolean posix) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new SecurityException("OIDC secret file must be a regular file: " + path.toAbsolutePath());
        }

        try {
            if (posix) {
                Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
                if (!OWNER_READ.equals(permissions) && !OWNER_READ_WRITE.equals(permissions)) {
                    throw new SecurityException("OIDC secret file permissions must allow only owner read or read/write"
                                                        + " access: "
                                                        + path.toAbsolutePath());
                }
            }
            try (var channel = Files.newByteChannel(path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                String password = new String(Channels.newInputStream(channel).readAllBytes(), StandardCharsets.UTF_8);
                return password.toCharArray();
            }
        } catch (IOException e) {
            throw new SecurityException("Cannot read OIDC secret file: " + path.toAbsolutePath(), e);
        }
    }

    private static Security securityFromContext() {
        return Contexts.context()
                .orElseGet(Contexts::globalContext)
                .get(Security.class)
                .orElseThrow(() -> new SecurityException("When using encryption configuration name for OIDC,"
                                                                 + " Security must be registered with current or"
                                                                 + " global context"));
    }
}
