/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

import static java.util.Objects.requireNonNull;

/**
 * Converter beans.
 */
public class Converters {

    public static class Of {
        private final String value;

        // method to make sure the "of" is used to map to this instance
        public static Of of(final String value) {
            return new Of(value);
        }

        Of(final String value) {
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Of of = (Of) o;
            return value.equals(of.value);
        }
    }

    public static class ValueOf {
        private final String value;

        // method to make sure the "valueOf" is used to map to this instance
        public static ValueOf valueOf(final String value) {
            return new ValueOf(value);
        }

        ValueOf(final String value) {
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ValueOf other = (ValueOf) o;
            return value.equals(other.value);
        }
    }

    public static class Parse {
        private final String value;

        // method to make sure the "parse" is used to map to this instance
        public static Parse parse(final String value) {
            return new Parse(value);
        }

        Parse(final String value) {
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Parse other = (Parse) o;
            return value.equals(other.value);
        }
    }

    public static class Ctor {
        private final String value;

        // method to make sure the constructor is used to map to this instance
        public Ctor(final String value) {
            this.value = requireNonNull(value);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Ctor other = (Ctor) o;
            return value.equals(other.value);
        }
    }
}
