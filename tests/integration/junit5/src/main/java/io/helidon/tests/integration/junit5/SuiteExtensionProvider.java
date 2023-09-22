/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.junit5;

/**
 * Helidon integration tests Junit 5 extension provider.
 * Common SPI extension base.
 */
public interface SuiteExtensionProvider {

    /**
     * Cast Junit 5 extension provider to its implementing class.
     *
     * @param cls Junit 5 extension provider implementing class
     * @return Junit 5 extension provider as its implementing class
     * @param <T> Junit 5 extension provider implementing class
     * @throws java.lang.IllegalArgumentException when {@code cls} parameter does not match
     *         class implemented by the provider.
     */
    default <T extends SuiteExtensionProvider> T as(Class <T> cls) {
        try {
            return cls.cast(this);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                    String.format("Cannot cast this SuiteExtensionProvider implementation as %s", cls.getName()), e);
        }
    }

    /**
     * Pass {@link Suite} context to the Junit 5 extension provider during initialization phase.
     * This method is called before any other initialization method of the provider.
     *
     * @param suiteContext the {@link Suite} context
     */
    default void suiteContext(SuiteContext suiteContext) {
    }

}
