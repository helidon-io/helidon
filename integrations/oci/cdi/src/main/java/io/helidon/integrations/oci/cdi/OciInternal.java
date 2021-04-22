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

package io.helidon.integrations.oci.cdi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

import javax.inject.Qualifier;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Qualifier
@interface OciInternal {
    String value();

    class Literal implements OciInternal {
        private final String value;

        private Literal(String name) {
            this.value = name;
        }

        static Literal create(String name) {
            return new Literal(name);
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<OciInternal> annotationType() {
            return OciInternal.class;
        }

        @Override
        public String toString() {
            return OciInternal.class.getName() + "(value=\"" + value + "\")";
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
            return value.equals(literal.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }
}
