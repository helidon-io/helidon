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

package io.helidon.integrations.oci.tls.certificates;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.tls.TlsManager;
import io.helidon.common.tls.spi.TlsManagerProvider;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("deprecation")
class OciCertificateBundleTlsManagerConfigTest {
    private static final URI VAULT_ENDPOINT = URI.create("https://vault.example.test");

    @Test
    void existingVaultApiSignaturesRemainSourceCompatible() {
        OciCertificatesTlsManagerConfig.Builder builder = OciCertificatesTlsManagerConfig.builder()
                .schedule("0 0 * * * ?")
                .vaultCryptoEndpoint(VAULT_ENDPOINT)
                .caOcid("certificate-authority")
                .certOcid("certificate")
                .keyOcid("vault-key")
                .keyPassword("password");

        Optional<URI> builderEndpoint = builder.vaultCryptoEndpoint();
        Optional<String> builderKeyOcid = builder.keyOcid();
        Optional<Supplier<char[]>> builderPassword = builder.keyPassword();
        OciCertificatesTlsManagerConfig config = builder.buildPrototype();
        URI endpoint = config.vaultCryptoEndpoint();
        String keyOcid = config.keyOcid();
        Supplier<char[]> password = config.keyPassword();

        assertThat(builderEndpoint, is(Optional.of(VAULT_ENDPOINT)));
        assertThat(builderKeyOcid, is(Optional.of("vault-key")));
        assertThat(builderPassword.isPresent(), is(true));
        assertThat(endpoint, is(VAULT_ENDPOINT));
        assertThat(keyOcid, is("vault-key"));
        assertThat(new String(password.get()), is("password"));
    }

    @Test
    void bundleConfigHasOnlyBundleOptionsAndDefaultsToChangeDetection() {
        OciCertificateBundleTlsManagerConfig config = bundleBuilder().buildPrototype();

        assertThat(config.schedule(), is("0 0 * * * ?"));
        assertThat(config.caOcid(), is("certificate-authority"));
        assertThat(config.certOcid(), is("certificate"));
        assertThat(config.alwaysReload(), is(false));
        assertThrows(NoSuchMethodException.class,
                     () -> OciCertificateBundleTlsManagerConfig.class.getMethod("vaultCryptoEndpoint"));
        assertThrows(NoSuchMethodException.class,
                     () -> OciCertificateBundleTlsManagerConfig.class.getMethod("keyPassword"));
    }

    @Test
    void bundleConfigCanBeCreatedFromConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("schedule", "0 0 * * * ?",
                                                                "always-reload", "true",
                                                                "ca-ocid", "certificate-authority",
                                                                "cert-ocid", "certificate")));

        OciCertificateBundleTlsManagerConfig result = OciCertificateBundleTlsManagerConfig.create(config);

        assertThat(result.alwaysReload(), is(true));
        assertThat(result.certOcid(), is("certificate"));
    }

    @Test
    void providerKeysAreDistinctAndServiceLoadedOnce() {
        DefaultOciCertificatesTlsManagerProvider vaultProvider = new DefaultOciCertificatesTlsManagerProvider();
        DefaultOciCertificateBundleTlsManagerProvider bundleProvider =
                new DefaultOciCertificateBundleTlsManagerProvider();

        assertThat(vaultProvider.configKey(), is("oci-certificates-tls-manager"));
        assertThat(bundleProvider.configKey(), is("oci-certificate-bundle-tls-manager"));
        assertThat(bundleProvider.configKey(), not(equalTo(vaultProvider.configKey())));

        long bundleProviderCount = ServiceLoader.load(TlsManagerProvider.class)
                .stream()
                .filter(provider -> provider.type().equals(DefaultOciCertificateBundleTlsManagerProvider.class))
                .count();
        assertThat(bundleProviderCount, is(1L));
    }

    @Test
    void bundleProviderCreatesNamedBundleManager() {
        Config config = Config.just(ConfigSources.create(Map.of("schedule", "0 0 * * * ?",
                                                                "ca-ocid", "certificate-authority",
                                                                "cert-ocid", "certificate")));
        TlsManager manager = new DefaultOciCertificateBundleTlsManagerProvider().create(config, "managed");

        assertThat(manager, instanceOf(DefaultOciCertificateBundleTlsManager.class));
        assertThat(manager.name(), is("managed"));
        assertThat(manager.type(), is("oci-certificate-bundle-tls-manager"));
        OciCertificateBundleTlsManager typed = (OciCertificateBundleTlsManager) manager;
        assertThat(typed.prototype().certOcid(), is("certificate"));
    }

    @Test
    void generatedMetadataKeepsVaultSchemaAndAddsBundleSchema() throws IOException {
        try (InputStream input = OciCertificateBundleTlsManagerConfig.class.getResourceAsStream(
                "/META-INF/helidon/config-metadata.json")) {
            assertNotNull(input);
            String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            String vault = metadataType(metadata, OciCertificatesTlsManager.class.getName());
            assertThat(metadataOption(vault, "vault-crypto-endpoint"), containsString("\"required\":true"));
            assertThat(metadataOption(vault, "key-ocid"), containsString("\"required\":true"));
            assertThat(metadataOption(vault, "key-password"), containsString("\"required\":true"));
            assertThat(vault, not(containsString("\"key\":\"private-key-source\"")));

            String bundle = metadataType(metadata, OciCertificateBundleTlsManager.class.getName());
            assertThat(metadataOption(bundle, "schedule"), containsString("\"required\":true"));
            assertThat(metadataOption(bundle, "ca-ocid"), containsString("\"required\":true"));
            assertThat(metadataOption(bundle, "cert-ocid"), containsString("\"required\":true"));
            assertThat(metadataOption(bundle, "always-reload"), containsString("\"defaultValue\":\"false\""));
            assertThat(bundle, not(containsString("vault-crypto-endpoint")));
            assertThat(bundle, not(containsString("key-password")));
        }
    }

    private static OciCertificateBundleTlsManagerConfig.Builder bundleBuilder() {
        return OciCertificateBundleTlsManagerConfig.builder()
                .schedule("0 0 * * * ?")
                .caOcid("certificate-authority")
                .certOcid("certificate");
    }

    private static String metadataType(String metadata, String type) {
        Matcher matcher = Pattern.compile("\\{\"type\":\"" + Pattern.quote(type)
                                                  + "\".*?(?=\\{\"type\":|$)",
                                          Pattern.DOTALL)
                .matcher(metadata);
        assertThat("metadata type " + type, matcher.find(), is(true));
        return matcher.group();
    }

    private static String metadataOption(String metadata, String key) {
        Matcher matcher = Pattern.compile("\\{\"key\":\"" + Pattern.quote(key) + "\"[^}]*}")
                .matcher(metadata);
        assertThat("metadata option " + key, matcher.find(), is(true));
        return matcher.group();
    }
}
