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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import javax.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Qualifier
@interface VaultInternal {
    String name();

    String path();

    class Literal implements VaultInternal {
        private final String name;
        private final String path;

        private Literal(String name, String path) {
            this.name = name;
            this.path = path;
        }

        static Literal create(String name, String path) {
            return new Literal(name, path);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public Class<VaultInternal> annotationType() {
            return VaultInternal.class;
        }

        @Override
        public String toString() {
            return VaultInternal.class.getName() + "(name=\"" + name + "\", path=\"" + path + "\")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Literal literal = (Literal) o;
            return name.equals(literal.name) && path.equals(literal.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, path);
        }
    }
}
