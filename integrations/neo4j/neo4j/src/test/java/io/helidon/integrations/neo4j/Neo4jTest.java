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

package io.helidon.integrations.neo4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.neo4j.driver.Config.TrustStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Neo4jTest {

    @Test
    void hostnameVerificationEnabledByDefault() {
        assertTrue(Neo4j.builder()
                           .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_ALL_CERTIFICATES)
                           .toInternalTrustStrategy()
                           .isHostnameVerificationEnabled());
        assertTrue(Neo4j.builder()
                           .trustStrategy(Neo4j.Builder.TrustStrategy.TRUST_ALL_CERTIFICATES)
                           .config(Config.empty())
                           .toInternalTrustStrategy()
                           .isHostnameVerificationEnabled());
    }

    @Test
    void hostnameVerificationCanBeDisabled() {
        assertFalse(Neo4j.builder()
                            .hostnameVerificationEnabled(false)
                            .toInternalTrustStrategy()
                            .isHostnameVerificationEnabled());

        Config config = Config.just(ConfigSources.create(Map.of("trustsettings.hostnameVerificationEnabled", "false")));
        assertFalse(Neo4j.builder()
                            .config(config)
                            .toInternalTrustStrategy()
                            .isHostnameVerificationEnabled());
    }

    @Test
    void publishedLegacyTrustSettingsRemainSupported(@TempDir Path tempDir) throws IOException {
        Path certificate = Files.createFile(tempDir.resolve("legacy-ca.pem"));
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_CUSTOM_CA_SIGNED_CERTIFICATES",
                "certificate", certificate.toString(),
                "hostname-verification-enabled", "false")));

        TrustStrategy strategy = Neo4j.builder()
                .config(config)
                .toInternalTrustStrategy();

        assertEquals(TrustStrategy.Strategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES, strategy.strategy());
        assertEquals(List.of(certificate.toFile()), strategy.certFiles());
        assertFalse(strategy.isHostnameVerificationEnabled());
    }

    @Test
    void canonicalTrustSettingsTakePrecedence(@TempDir Path tempDir) throws IOException {
        Path legacyCertificate = Files.createFile(tempDir.resolve("legacy-ca.pem"));
        Path canonicalCertificate = Files.createFile(tempDir.resolve("canonical-ca.pem"));
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_SYSTEM_CA_SIGNED_CERTIFICATES",
                "certificate", legacyCertificate.toString(),
                "hostname-verification-enabled", "false",
                "trustsettings.trustStrategy", "TRUST_CUSTOM_CA_SIGNED_CERTIFICATES",
                "trustsettings.certificate", canonicalCertificate.toString(),
                "trustsettings.hostnameVerificationEnabled", "true")));

        TrustStrategy strategy = Neo4j.builder()
                .config(config)
                .toInternalTrustStrategy();

        assertEquals(TrustStrategy.Strategy.TRUST_CUSTOM_CA_SIGNED_CERTIFICATES, strategy.strategy());
        assertEquals(List.of(canonicalCertificate.toFile()), strategy.certFiles());
        assertTrue(strategy.isHostnameVerificationEnabled());
    }

    @Test
    void invalidCanonicalTrustSettingDoesNotFallBackToLegacyValue() {
        Config config = Config.just(ConfigSources.create(Map.of(
                "trust-strategy", "TRUST_SYSTEM_CA_SIGNED_CERTIFICATES",
                "trustsettings.trustStrategy", "invalid")));

        assertThrows(IllegalArgumentException.class, () -> Neo4j.builder().config(config));
    }
}
