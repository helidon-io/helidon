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

package io.helidon.pico.integrations.oci.runtime;

import java.util.Set;

import io.helidon.builder.Singular;
import io.helidon.common.types.AnnotationAndValueDefault;
import io.helidon.pico.api.InjectionPointInfoDefault;

import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import static io.helidon.pico.integrations.oci.runtime.OciAuthenticationDetailsProvider.canReadPath;
import static io.helidon.pico.integrations.oci.runtime.OciAuthenticationDetailsProvider.userHomePrivateKeyPath;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class OciAuthenticationDetailsProviderTest {

    @Test
    void testCanReadPath() {
        assertThat(canReadPath("./target"),
                   is(true));
        assertThat(canReadPath("./~bogus~"),
                   is(false));
    }

    @Test
    void testUserHomePrivateKeyPath() {
        OciConfigBean ociConfigBean = OciExtension.ociConfig();
        assertThat(userHomePrivateKeyPath(ociConfigBean),
                   endsWith("/.oci/oci_api_key.pem"));

        ociConfigBean = OciConfigBeanDefault.toBuilder(ociConfigBean)
                .configPath("/decoy/path")
                .authKeyFile("key.pem")
                .build();
        assertThat(userHomePrivateKeyPath(ociConfigBean),
                   endsWith("/.oci/key.pem"));
    }

    @Test
    void testToNamedProfile() {
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(null),
                   nullValue());

        InjectionPointInfoDefault.Builder ipi = InjectionPointInfoDefault.builder()
                .annotations(Set.of());
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.addAnnotation(AnnotationAndValueDefault.create(Singular.class));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.addAnnotation(AnnotationAndValueDefault.create(Named.class));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.annotations(Set.of(AnnotationAndValueDefault.create(Singular.class),
                               AnnotationAndValueDefault.create(Named.class, "")));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   nullValue());

        ipi.annotations(Set.of(AnnotationAndValueDefault.create(Singular.class),
                               AnnotationAndValueDefault.create(Named.class, " profileName ")));
        assertThat(OciAuthenticationDetailsProvider.toNamedProfile(ipi),
                   equalTo("profileName"));
    }

}
