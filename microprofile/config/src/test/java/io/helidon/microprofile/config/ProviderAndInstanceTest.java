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

import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.theInstance;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderAndInstanceTest {

    // Can't use HelidonTest (it depends on a MicroProfile Config CDI implementation, which this project is, so that's a
    // circular dependency). Have to do it by hand.
    private SeContainer container;

    // A contextal reference to this.
    private ProviderAndInstanceTest self;

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

    @Inject
    ProviderAndInstanceTest() {
        super();
    }
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void startContainer() {
        this.container = SeContainerInitializer.newInstance()
            .disableDiscovery()
            .addBeanClasses(this.getClass())
            .addExtensions(ConfigCdiExtension.class)
            .initialize();
        this.self = this.container.select(this.getClass()).get();
    }

    @AfterEach
    void closeContainer() {
        this.self = null;
        this.container.close();
    }
    
    @Test
    void testInstanceForExistingProperty() {
        assertThat(self.camelCaseInstance.get(), is("no"));
    }

    @Test
    void testProviderForExistingProperty() {
        assertThat(self.camelCaseProvider.get(), is("no"));
    }

    @Test
    void testInstanceForNonExistentProperty() {
        // The MicroProfile Config specification ends up effectively and inadvertently requiring that an Instance be
        // resolvable, even when a configuration value for the relevant configuration property name is not supplied.
        assertThat(self.nonExistentInstance.isResolvable(), is(true));
        assertThat(self.nonExistentInstance.iterator().hasNext(), is(true)); // !

        // It's resolvable but:
        assertThrows(NoSuchElementException.class, self.nonExistentInstance::get);
    }

    @Test
    void testProviderForNonExistentProperty() {
        assertThrows(NoSuchElementException.class, self.nonExistentProvider::get);
    }

    @Test
    void testContainerImplementsProviderAndInstance() {
        assertThat(self.camelCaseInstance.getClass().getName(), startsWith("org.jboss."));
        assertThat(self.camelCaseProvider.getClass().getName(), startsWith("org.jboss."));
        assertThat(self.camelCaseInstance, is(self.camelCaseProvider));
        assertThat(self.camelCaseInstance, not(theInstance(self.camelCaseProvider)));
    }

}
