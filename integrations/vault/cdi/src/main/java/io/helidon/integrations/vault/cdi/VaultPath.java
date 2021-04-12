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
 * Customize the mount path of a secrets engine.
 * <p>
 * This annotation is optional, by default the default path of the engine/auth method is used.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
public @interface VaultPath {
    /**
     * Custom mount path.
     *
     * @return path
     */
    String value();

    /**
     * Utility to obtain {@link io.helidon.integrations.vault.cdi.VaultPath} instances.
     */
    class Literal implements VaultPath {
        private final String name;

        private Literal(String name) {
            this.name = name;
        }

        /**
         * Create a new literal.
         *
         * @param path vault path value
         * @return new vault path
         */
        public static VaultPath create(String path) {
            return new Literal(path);
        }

        @Override
        public String value() {
            return name;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return VaultPath.class;
        }
    }
}
