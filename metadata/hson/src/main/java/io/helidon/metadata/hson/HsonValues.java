/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.metadata.hson;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.Objects;

final class HsonValues {
    private HsonValues() {
    }

    static final class StringValue implements Hson.Value<String> {
        private final String value;

        StringValue(String value) {
            this.value = value;
        }

        public static StringValue create(String value) {
            return new StringValue(value);
        }

        @Override
        public void write(PrintWriter writer) {
            writer.write(quote(escape(value)));
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Hson.Type type() {
            return Hson.Type.STRING;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof StringValue that)) {
                return false;
            }
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return quote(value);
        }

        private String quote(String value) {
            return '"' + value + '"';
        }

        private String escape(String string) {
            String result = string.replaceAll("\n", "\\\\n");

            result = result.replaceAll("\"", "\\\\\"");
            result = result.replaceAll("\t", "\\\\t");
            result = result.replaceAll("\r", "\\\\r");
            // replace two backslashes with four backslashes
            result = result.replaceAll("\\\\\\\\", "\\\\\\\\\\\\\\\\");
            result = result.replaceAll("\f", "\\\\f");
            return result;
        }
    }

    static final class NumberValue implements Hson.Value<BigDecimal> {
        private final BigDecimal value;

        NumberValue(BigDecimal value) {
            this.value = value;
        }

        public static NumberValue create(BigDecimal value) {
            return new NumberValue(value);
        }

        @Override
        public void write(PrintWriter writer) {
            writer.write(String.valueOf(value));
        }

        @Override
        public BigDecimal value() {
            return value;
        }

        @Override
        public Hson.Type type() {
            return Hson.Type.NUMBER;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof NumberValue that)) {
                return false;
            }
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    static final class NullValue implements Hson.Value<Void> {
        static final NullValue INSTANCE = new NullValue();

        private NullValue() {
        }

        @Override
        public void write(PrintWriter writer) {
            writer.write("null");
        }

        @Override
        public Void value() {
            return null;
        }

        @Override
        public Hson.Type type() {
            return Hson.Type.NULL;
        }
    }

    static final class BooleanValue implements Hson.Value<Boolean> {
        private final boolean value;

        BooleanValue(boolean value) {
            this.value = value;
        }

        public static BooleanValue create(boolean value) {
            return new BooleanValue(value);
        }

        @Override
        public void write(PrintWriter writer) {
            writer.write(String.valueOf(value));
        }

        @Override
        public Boolean value() {
            return value;
        }

        @Override
        public Hson.Type type() {
            return Hson.Type.BOOLEAN;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BooleanValue that)) {
                return false;
            }
            return value == that.value;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }
}
