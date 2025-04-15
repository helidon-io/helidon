/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.microprofile.config;

import java.util.NoSuchElementException;

import io.helidon.microprofile.testing.AddExtension;
import io.helidon.microprofile.testing.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.theInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AddExtension(ConfigCdiExtension.class)
@DisableDiscovery
@HelidonTest
class ProviderAndInstanceTest {

    @Inject
    @ConfigProperty(name = "camelCase") // exists; see <environmentVariables> in pom.xml
    private Instance<String> camelCaseInstance;

    @Inject
    @ConfigProperty(name = "camelCase")
    private Provider<String> camelCaseProvider;

    @Inject
    @ConfigProperty(name = "nonexistent") // does not exist
    private Instance<String> nonExistentInstance;

    @Inject
    @ConfigProperty(name = "nonexistent")
    private Provider<String> nonExistentProvider;

    @Test
    void testInstanceForExistingProperty() {
        assertThat(this.camelCaseInstance.get(), is("no"));
    }

    @Test
    void testProviderForExistingProperty() {
        assertThat(this.camelCaseProvider.get(), is("no"));
    }

    @Test
    void testInstanceForNonExistentProperty() {
        // The MicroProfile Config specification ends up effectively and inadvertently requiring that an Instance be
        // resolvable, even when a configuration value for the relevant configuration property name is not supplied.
        assertThat(this.nonExistentInstance.isResolvable(), is(true));
        assertThat(this.nonExistentInstance.iterator().hasNext(), is(true)); // !

        // It's resolvable but:
        assertThrows(NoSuchElementException.class, this.nonExistentInstance::get);
    }

    @Test
    void testProviderForNonExistentProperty() {
        assertThrows(NoSuchElementException.class, this.nonExistentProvider::get);
    }

    @Test
    void testContainerImplementsProviderAndInstance() {
        assertThat(this.camelCaseInstance.getClass().getName(), startsWith("org.jboss."));
        assertThat(this.camelCaseProvider.getClass().getName(), startsWith("org.jboss."));
        assertThat(this.camelCaseInstance, is(this.camelCaseProvider));
        assertThat(this.camelCaseInstance, not(theInstance(this.camelCaseProvider)));
    }

}
