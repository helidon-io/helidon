/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.common.config.GlobalConfig;
import io.helidon.common.types.Annotation;
import io.helidon.config.Config;
import io.helidon.inject.InjectionConfig;
import io.helidon.inject.InjectionServiceProviderException;
import io.helidon.inject.InjectionServices;
import io.helidon.inject.Services;
import io.helidon.inject.service.Injection;
import io.helidon.inject.service.Ip;
import io.helidon.inject.service.Qualifier;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.inject.testing.InjectionTestingSupport.resetAll;
import static io.helidon.inject.testing.InjectionTestingSupport.testableServices;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciAuthenticationDetailsProviderTest {

    InjectionServices injectionServices;
    Services services;

    @BeforeEach
    @AfterEach
    void reset() {
        OciExtension.ociConfigFileName(null);
    }

    @AfterAll
    static void tearDown() {
        resetAll();
    }

    void resetWith(Config config, InjectionConfig injectionConfig) {
        GlobalConfig.config(() -> config, true);
        resetAll();
        this.injectionServices = testableServices(injectionConfig);
        this.services = injectionServices.services();
    }

    @Test
    void testCanReadPath() {
        assertThat(OciAuthenticationDetailsProvider.canReadPath("./target"),
                                 is(true));
        assertThat(OciAuthenticationDetailsProvider.canReadPath("./~bogus~"),
                                 is(false));
    }

    @Test
    void testUserHomePrivateKeyPath() {
        OciConfig ociConfig = Objects.requireNonNull(OciExtension.ociConfig());
        assertThat(OciAuthenticationDetailsProvider.userHomePrivateKeyPath(ociConfig),
                                 endsWith("/.oci/oci_api_key.pem"));

        ociConfig = OciConfig.builder(ociConfig)
                .configPath("/decoy/path")
                .authKeyFile("key.pem")
                .build();
        assertThat(OciAuthenticationDetailsProvider.userHomePrivateKeyPath(ociConfig),
                                 endsWith("/.oci/key.pem"));
    }

    @Test
    void testToNamedProfile() {
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile((Ip) null),
                   nullValue());

        Ip.Builder ipi = Ip.builder()
                .annotations(Set.of());
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.addAnnotation(Annotation.create(Option.Singular.class));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.addAnnotation(Annotation.create(Injection.Named.class));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.qualifiers(Set.of(Qualifier.create(Option.Singular.class),
                              Qualifier.createNamed("")));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.qualifiers(Set.of(Qualifier.create(Option.Singular.class),
                              Qualifier.createNamed("profileName")));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   equalTo("profileName"));
    }

    @Test
    void authStrategiesAvailability() {
        Config config = OciExtensionTest.createTestConfig(
                        OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                        OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"))
                .get(OciConfig.CONFIG_KEY);
        OciConfig cfg = OciConfig.create(config);
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.AUTO.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG.isAvailable(cfg),
                   is(false));
        // this code is dependent upon whether and OCI config-file is present - so leaving this commented out intentionally
//        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG_FILE.isAvailable(cfg),
//                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.INSTANCE_PRINCIPALS.isAvailable(cfg),
                   is(false));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.RESOURCE_PRINCIPAL.isAvailable(cfg),
                   is(false));

        config = OciExtensionTest.createTestConfig(
                        OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                        OciExtensionTest.ociAuthConfigFile("./target", null),
                        OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.AUTO.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG_FILE.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.INSTANCE_PRINCIPALS.isAvailable(cfg),
                   is(false));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.RESOURCE_PRINCIPAL.isAvailable(cfg),
                   is(false));
    }

    @Test
    void selectionWhenNoConfigIsSet() {
        resetWith(Config.empty(), InjectionConfig.builder()
                          .permitsDynamic(true)
                          .build());

        assertThat(OciExtension.isSufficientlyConfigured(Config.empty()),
                   is(false));

        Supplier<AbstractAuthenticationDetailsProvider> authServiceProvider =
                services.get(AbstractAuthenticationDetailsProvider.class);
        Objects.requireNonNull(authServiceProvider);

        // this code is dependent upon whether and OCI config-file is present - so leaving this commented out intentionally
//        InjectionServiceProviderException e = assertThrows(InjectionServiceProviderException.class, authServiceProvider::get);
//        assertThat(e.getCause().getMessage(),
//                   equalTo("No instances of com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider available for use. " +
//                           "Verify your configuration named: oci"));
    }

    @Test
    void selectionWhenFileConfigIsSetWithAuto() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthConfigFile("./target", "profile"));
        resetWith(config, InjectionConfig.builder()
                .permitsDynamic(true)
                .build());

        Supplier<AbstractAuthenticationDetailsProvider> authServiceProvider =
                services.get(AbstractAuthenticationDetailsProvider.class);

        InjectionServiceProviderException e = assertThrows(InjectionServiceProviderException.class, authServiceProvider::get);
        e.printStackTrace();
        assertThat(e.getCause().getClass(),
                   equalTo(UncheckedIOException.class));
    }

    @Test
    void selectionWhenSimpleConfigIsSetWithAuto() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "passphrase", "fp", "privKey", null, "us-phoenix-1"));
        resetWith(config, InjectionConfig.builder()
                .permitsDynamic(true)
                .build());

        Supplier<AbstractAuthenticationDetailsProvider> authServiceProvider =
                services.get(AbstractAuthenticationDetailsProvider.class);

        AbstractAuthenticationDetailsProvider authProvider = authServiceProvider.get();
        assertThat(authProvider.getClass(),
                   equalTo(SimpleAuthenticationDetailsProvider.class));
        SimpleAuthenticationDetailsProvider auth = (SimpleAuthenticationDetailsProvider) authProvider;
        assertThat(auth.getTenantId(),
                   equalTo("tenant"));
        assertThat(auth.getUserId(),
                   equalTo("user"));
        assertThat(auth.getRegion(),
                   equalTo(Region.US_PHOENIX_1));
        assertThat(new String(auth.getPassphraseCharacters()),
                   equalTo("passphrase"));
    }

}
