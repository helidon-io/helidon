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

package io.helidon.security.providers.oidc.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import io.helidon.common.Base64Value;
import io.helidon.common.context.Contexts;
import io.helidon.common.crypto.SymmetricCipher;
import io.helidon.common.reactive.Single;
import io.helidon.security.Security;
import io.helidon.security.spi.EncryptionProvider.EncryptionSupport;

final class OidcEncryption {
    private static final Logger LOGGER = Logger.getLogger(OidcEncryption.class.getName());

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

        return symmetricCipher(masterPassword);
    }

    private static EncryptionSupport symmetricCipher(char[] masterPassword) {
        SymmetricCipher cipher = SymmetricCipher.create(masterPassword);
        return EncryptionSupport.create(
                bytes -> Single.just(cipher.encrypt(Base64Value.create(bytes)).toBase64()),
                cipherText -> Single.just(cipher.decrypt(Base64Value.createFromEncoded(cipherText)).toBytes())
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
        if (!Files.exists(path)) {

            String password = UUID.randomUUID().toString();
            try {
                Files.writeString(path, password, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (IOException e) {
                throw new SecurityException("Failed to create OIDC secret " + path.toAbsolutePath(), e);
            }
            LOGGER.warning("OIDC requires encryption configuration which was not provided. We will generate a password"
                                   + " that will only work for the current service instance. To disable encryption, use"
                                   + " cookie-encryption-enabled: false configuration, to configure master password, use"
                                   + " cookie-encryption-password: my-master-password (must be configured to same value on all"
                                   + " instances that share the cookie), to configure encryption using security"
                                   + " (support for vaults), use"
                                   + " cookie-encryption-name: name (must have corresponding encryption provider and"
                                   + " configuration with the provided name in security), this also requires Security to be"
                                   + " registered with current or global Context (this works automatically in Helidon MP)."
                                   + " This message is logged just once, before generating the master password");

        }

        try {
            // to be consistent, I always read the content from the file, even when creating it
            return Files.readString(path, StandardCharsets.UTF_8).toCharArray();
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
