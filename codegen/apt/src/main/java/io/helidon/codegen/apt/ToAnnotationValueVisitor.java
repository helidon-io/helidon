/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.codegen.apt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

class ToAnnotationValueVisitor implements AnnotationValueVisitor<Object, Object> {
    private final Elements elements;
    private boolean mapVoidToNull;
    private boolean mapFalseToNull;
    private boolean mapEmptyStringToNull;
    private boolean mapBlankArrayToNull;
    private boolean mapToSourceDeclaration;

    ToAnnotationValueVisitor(Elements elements) {
        this.elements = elements;
    }

    ToAnnotationValueVisitor mapVoidToNull(boolean val) {
        this.mapVoidToNull = val;
        return this;
    }

    ToAnnotationValueVisitor mapBooleanToNull(boolean val) {
        this.mapFalseToNull = val;
        return this;
    }

    ToAnnotationValueVisitor mapEmptyStringToNull(boolean val) {
        this.mapEmptyStringToNull = val;
        return this;
    }

    ToAnnotationValueVisitor mapBlankArrayToNull(boolean val) {
        this.mapBlankArrayToNull = val;
        return this;
    }

    ToAnnotationValueVisitor mapToSourceDeclaration(boolean val) {
        this.mapToSourceDeclaration = val;
        return this;
    }

    @Override
    public Object visit(AnnotationValue av, Object o) {
        return av.accept(this, o);
    }

    @Override
    public Object visitBoolean(boolean b, Object o) {
        if (!b && mapFalseToNull) {
            return null;
        }
        return b;
    }

    @Override
    public Object visitByte(byte b, Object o) {
        return b;
    }

    @Override
    public Object visitChar(char c, Object o) {
        return c;
    }

    @Override
    public Object visitDouble(double d, Object o) {
        return d;
    }

    @Override
    public Object visitFloat(float f, Object o) {
        return f;
    }

    @Override
    public Object visitInt(int i, Object o) {
        return i;
    }

    @Override
    public Object visitLong(long i, Object o) {
        return i;
    }

    @Override
    public Object visitShort(short s, Object o) {
        return s;
    }

    @Override
    public Object visitString(String s, Object o) {
        if (mapEmptyStringToNull && s != null && s.isBlank()) {
            return null;
        }

        if (mapToSourceDeclaration) {
            return "\"" + s + "\"";
        }

        return s;
    }

    @Override
    public Object visitType(TypeMirror t, Object o) {
        String val = t.toString();
        if (mapVoidToNull && ("void".equals(val) || Void.class.getName().equals(val))) {
            val = null;
        }
        return val;
    }

    @Override
    public Object visitEnumConstant(VariableElement c, Object o) {
        return String.valueOf(c.getSimpleName());
    }

    @Override
    public Object visitAnnotation(AnnotationMirror a, Object o) {
        return AptAnnotationFactory.createAnnotation(a, elements);
    }

    @Override
    public Object visitArray(List<? extends AnnotationValue> vals, Object o) {
        List<Object> values = new ArrayList<>(vals.size());

        for (AnnotationValue val : vals) {
            Object elementValue = val.accept(this, null);
            if (elementValue != null) {
                values.add(elementValue);
            }
        }

        if (mapBlankArrayToNull && values.isEmpty()) {
            return null;
        } else if (mapToSourceDeclaration) {
            return vals.stream()
                    .map(v -> v.accept(this, null))
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .collect(Collectors.joining(", ", "{", "}"));
        }

        return values;
    }

    @Override
    public String visitUnknown(AnnotationValue av, Object o) {
        return null;
    }

}
