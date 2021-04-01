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

package io.helidon.integrations.vault.cdi;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Define a named vault instance.
 * <p>
 * This annotation is optional. If not defined, the default instance will be injected.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface VaultName {
    /**
     * Vault name to inject.
     *
     * @return name
     */
    String value();

    /**
     * Utility to obtain {@link io.helidon.integrations.vault.cdi.VaultName} instances.
     */
    class Literal implements VaultName {
        private final String name;

        private Literal(String name) {
            this.name = name;
        }

        /**
         * Create a new literal.
         *
         * @param name vault name value
         * @return new vault name
         */
        public static VaultName create(String name) {
            return new Literal(name);
        }

        @Override
        public String value() {
            return name;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return VaultName.class;
        }
    }
}
