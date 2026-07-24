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
package io.helidon.microprofile.lra;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.helidon.common.Reflected;

import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Reflected
class NonJaxRsCallbackAuthenticator {

    static final String CONFIG_PREFIX = NonJaxRsResource.CONFIG_CONTEXT_KEY + ".callback-auth";
    static final String CONFIG_SECRET_KEY = CONFIG_PREFIX + ".secret";
    static final String CONFIG_COMPATIBILITY_MODE_KEY = CONFIG_PREFIX + ".compatibility-mode";
    static final String CAPABILITY_QUERY_PARAMETER = "helidon-lra-capability";

    private static final System.Logger LOGGER = System.getLogger(NonJaxRsCallbackAuthenticator.class.getName());
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_PREFIX = "v1.";
    private static final int ENCODED_MAC_LENGTH = 43;
    private static final int MAC_BYTES = 32;
    private static final int MINIMUM_SECRET_BYTES = MAC_BYTES;

    private final Optional<SecretKeySpec> activeSecret;
    private final boolean compatibilityMode;

    @Inject
    NonJaxRsCallbackAuthenticator(
            @ConfigProperty(name = CONFIG_SECRET_KEY) Optional<String> configuredSecret,
            @ConfigProperty(name = CONFIG_COMPATIBILITY_MODE_KEY, defaultValue = "false") boolean compatibilityMode) {
        this.activeSecret = configuredSecret.map(secret -> decodeSecret(CONFIG_SECRET_KEY, secret));
        this.compatibilityMode = compatibilityMode;
    }

    void validateConfiguration() {
        if (activeSecret.isEmpty()) {
            throw new DeploymentException("Non-JAX-RS LRA callbacks require configuration property "
                                                  + CONFIG_SECRET_KEY);
        }
        if (compatibilityMode) {
            LOGGER.log(Level.WARNING,
                       "Non-JAX-RS LRA callbacks use unsigned URLs and accept unsigned requests. "
                               + "Use this setting only during a rolling upgrade and disable it after legacy LRAs drain.");
        }
    }

    String capability(URI lraId, String callbackType, String classFqdn, String methodName) {
        SecretKeySpec secret = activeSecret.orElseThrow(() ->
                new IllegalStateException("Missing non-JAX-RS LRA callback authentication secret"));
        return TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac(secret, lraId, callbackType, classFqdn, methodName));
    }

    boolean authenticate(List<String> capabilities,
                         URI lraId,
                         String callbackType,
                         String classFqdn,
                         String methodName) {
        if (capabilities.isEmpty()) {
            return compatibilityMode;
        }
        if (capabilities.size() != 1 || lraId == null) {
            return false;
        }

        String capability = capabilities.getFirst();
        if (capability.length() != TOKEN_PREFIX.length() + ENCODED_MAC_LENGTH
                || !capability.startsWith(TOKEN_PREFIX)) {
            return false;
        }

        final byte[] suppliedMac;
        try {
            suppliedMac = Base64.getUrlDecoder().decode(capability.substring(TOKEN_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (suppliedMac.length != MAC_BYTES) {
            return false;
        }

        return activeSecret
                .map(secret -> MessageDigest.isEqual(suppliedMac,
                                                     mac(secret, lraId, callbackType, classFqdn, methodName)))
                .orElse(false);
    }

    boolean compatibilityMode() {
        return compatibilityMode;
    }

    private static byte[] mac(SecretKeySpec secret,
                              URI lraId,
                              String callbackType,
                              String classFqdn,
                              String methodName) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secret);
            update(mac, lraId.toASCIIString());
            update(mac, callbackType);
            update(mac, classFqdn);
            update(mac, methodName);
            return mac.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot authenticate non-JAX-RS LRA callback", e);
        }
    }

    private static void update(Mac mac, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        mac.update((byte) (bytes.length >>> 24));
        mac.update((byte) (bytes.length >>> 16));
        mac.update((byte) (bytes.length >>> 8));
        mac.update((byte) bytes.length);
        mac.update(bytes);
    }

    private static SecretKeySpec decodeSecret(String configKey, String configuredSecret) {
        final byte[] secret;
        try {
            secret = Base64.getUrlDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException e) {
            throw new DeploymentException("Configuration property " + configKey
                                                  + " must contain a Base64 URL encoded secret", e);
        }
        if (secret.length < MINIMUM_SECRET_BYTES) {
            throw new DeploymentException("Configuration property " + configKey
                                                  + " must contain at least " + MINIMUM_SECRET_BYTES
                                                  + " bytes of secret material");
        }
        return new SecretKeySpec(secret, HMAC_ALGORITHM);
    }
}
