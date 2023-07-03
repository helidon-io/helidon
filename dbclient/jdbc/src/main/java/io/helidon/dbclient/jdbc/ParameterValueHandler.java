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
package io.helidon.dbclient.jdbc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;

interface ParameterValueHandler {

    // Type specific setter.
    void set(PreparedStatement statement, int index) throws SQLException;

    // String representation of the value for logger and exception messages.
    String valueToString();

    Object rawValue();

    // Common code for Objects.
    // Primitive types are stored directly in their own records.
    abstract class AbstractHandler<T> implements ParameterValueHandler {

        private final T value;

        AbstractHandler(T value) {
            this.value = value;
        }

        T value() {
            return value;
        }

        @Override
        public String valueToString() {
            return value.toString();
        }

        @Override
        public Object rawValue() {
            return value;
        }

    }

    final class ObjectHandler extends AbstractHandler<Object> {

        ObjectHandler(Object value) {
            super(value);
        }

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setObject(index, value());
        }

    }

    final class StringHandler extends AbstractHandler<String> {

        StringHandler(String value) {
            super(value);
        }

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setString(index, value());
        }

    }

    record BooleanHandler(boolean value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setBoolean(index, value);
        }

        @Override
        public String valueToString() {
            return Boolean.toString(value);
        }

        @Override
        public Object rawValue() {
            return Boolean.valueOf(value);
        }

    }

    record ByteHandler(byte value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setByte(index, value);
        }

        @Override
        public String valueToString() {
            return Byte.toString(value);
        }

        @Override
        public Object rawValue() {
            return Byte.valueOf(value);
        }

    }

    record ShortHandler(short value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setShort(index, value);
        }

        @Override
        public String valueToString() {
            return Short.toString(value);
        }

        @Override
        public Object rawValue() {
            return Short.valueOf(value);
        }

    }

    record IntHandler(int value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setInt(index, value);
        }

        @Override
        public String valueToString() {
            return Integer.toString(value);
        }

        @Override
        public Object rawValue() {
            return Integer.valueOf(value);
        }

    }

    record LongHandler(long value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setLong(index, value);
        }

        @Override
        public String valueToString() {
            return Long.toString(value);
        }

        @Override
        public Object rawValue() {
            return Long.valueOf(value);
        }

    }

    record FloatHandler(float value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setFloat(index, value);
        }

        @Override
        public String valueToString() {
            return Float.toString(value);
        }

        @Override
        public Object rawValue() {
            return Float.valueOf(value);
        }

    }

    record
    DoubleHandler(double value) implements ParameterValueHandler {

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setDouble(index, value);
        }

        @Override
        public String valueToString() {
            return Double.toString(value);
        }

        @Override
        public Object rawValue() {
            return Double.valueOf(value);
        }

    }

    final class BigDecimalHandler extends AbstractHandler<BigDecimal> {

        BigDecimalHandler(BigDecimal value) {
            super(value);
        }

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setBigDecimal(index, value());
        }

    }

    final class BytesHandler extends AbstractHandler<byte[]> {

        BytesHandler(byte[] value) {
            super(value);
        }

        @Override
        public void set(PreparedStatement statement, int index) throws SQLException {
            statement.setBytes(index, value());
        }

    }

    static ParameterValueHandler handlerOf(Object value) {
        if (value instanceof Number) {
            if (value instanceof Byte) {
                return new ByteHandler((Byte) value);
            }
            if (value instanceof Short) {
                return new ShortHandler((Short) value);
            }
            if (value instanceof Integer) {
                return new IntHandler((Integer) value);
            }
            if (value instanceof Long) {
                return new LongHandler((Long) value);
            }
            if (value instanceof Float) {
                return new FloatHandler((Float) value);
            }
            if (value instanceof Double) {
                return new DoubleHandler((Double) value);
            }
            if (value instanceof BigInteger) {
                return new BigDecimalHandler(new BigDecimal((BigInteger) value));
            }
            if (value instanceof BigDecimal) {
                return new BigDecimalHandler((BigDecimal) value);
            }
        }
        if (value instanceof String) {
            return new StringHandler((String) value);
        }
        if (value instanceof byte[]) {
            return new BytesHandler((byte[]) value);
        }
        return new ObjectHandler(value);
    }

}
