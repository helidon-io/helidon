/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
package io.helidon.codegen.classmodel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

import io.helidon.common.types.AnnotationProperty.ConstantValue;
import io.helidon.common.types.EnumValue;
import io.helidon.common.types.TypeName;

import static io.helidon.codegen.classmodel.ClassModel.PADDING_TOKEN;

class ModelWriter extends Writer {

    private final Writer delegate;
    private final String padding;
    private String currentPadding = ""; //no padding
    private int paddingLevel = 0;
    private boolean firstWrite = true;

    ModelWriter(Writer delegate, String padding) {
        this.delegate = delegate;
        this.padding = padding;
    }

    @Override
    public void write(String str)  {
        try {
            if (firstWrite) {
                delegate.write(currentPadding);
                firstWrite = false;
            }
            String padded = str.replaceAll("\n", "\n" + currentPadding);
            padded = padded.replaceAll(PADDING_TOKEN, padding);
            delegate.write(padded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void write(char[] cbuf, int off, int len) {
        try {
            delegate.write(cbuf, off, len);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void flush() {
        try {
            delegate.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            delegate.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ModelWriter append(CharSequence csq) {
        try {
            delegate.append(csq);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return this;
    }

    String currentPadding() {
        return currentPadding;
    }

    void increasePaddingLevel() {
        paddingLevel++;
        currentPadding = padding.repeat(paddingLevel);
    }

    void decreasePaddingLevel() {
        paddingLevel--;
        currentPadding = padding.repeat(paddingLevel);
    }

    void writeLine(String str) {
        write(str);
        write("\n");
    }

    void writeSeparatorLine() {
        try {
            delegate.write("\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void writeValue(Number value) {
        switch (value) {
            case Long longValue -> writeValue(longValue);
            case Float floatValue ->  writeValue(floatValue);
            case Double doubleValue ->  writeValue(doubleValue);
            case Byte byteValue ->  writeValue(byteValue);
            case Short shortValue ->  writeValue(shortValue);
            case Integer intValue -> writeValue(intValue);
            default -> {
            }
        }
    }

    void writeValue(Long longValue) {
        write(Long.toString(longValue));
        write("L");
    }

    void writeValue(Float floatValue) {
        write(Float.toString(floatValue));
        write("F");
    }

    void writeValue(Double doubleValue) {
        write(Double.toString(doubleValue));
        write("D");
    }

    void writeValue(Byte byteValue) {
        write("(byte) ");
        write(Byte.toString(byteValue));
    }

    void writeValue(Short shortValue) {
        write("(short) ");
        write(Short.toString(shortValue));
    }

    void writeValue(Integer intValue) {
        write(Integer.toString(intValue));
    }

    void writeValue(Character character) {
        write("'");
        write(escape(character));
        write("'");
    }

    void writeValue(String str) {
        if (str.startsWith("\"") && str.endsWith("\"")) {
            write(str);
        } else {
            write("\"");
            write(escape(str));
            write("\"");
        }
    }

    void writeValue(ImportOrganizer imports, TypeName typeName) {
        var valueType = Type.fromTypeName(typeName);
        var identifier = imports.typeName(valueType, true);
        write(identifier);
        write(".class");
    }

    void writeValue(ImportOrganizer imports, Class<?> clazz) {
        var valueType = Type.fromTypeName(TypeName.create(clazz));
        var identifier = imports.typeName(valueType, true);
        write(identifier);
        write(".class");
    }

    void writeValue(ImportOrganizer imports, EnumValue enumValue) {
        var valueType = Type.fromTypeName(enumValue.type());
        var identifier = imports.typeName(valueType, true);
        write(identifier);
        write(".");
        write(enumValue.name());
    }

    void writeValue(ImportOrganizer imports, Enum<?> enumValue) {
        var valueType = Type.fromTypeName(TypeName.create(enumValue.getClass()));
        var identifier = imports.typeName(valueType, true);
        write(identifier);
        write(".");
        write(enumValue.name());
    }

    void writeValue(ImportOrganizer imports, ConstantValue constantValue) {
        var valueType = Type.fromTypeName(constantValue.type());
        var identifier = imports.typeName(valueType, true);
        write(identifier);
        write(".");
        write(constantValue.name());
    }

    private static String escape(String str) {
        var escaped = str.replace("\\", "\\\\");
        escaped = escaped.replace("\"", "\\\"");
        return escaped;
    }

    private static String escape(Character ch) {
        return switch (ch) {
            case '\'' -> "\\'";
            case '\t' -> "\\t";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            default -> String.valueOf(ch);
        };
    }
}
