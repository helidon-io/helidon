/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.builder.processor.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

class ToStringAnnotationValueVisitor implements AnnotationValueVisitor<String, Object> {
    private boolean mapVoidToNull;
    private boolean mapFalseToNull;
    private boolean mapEmptyStringToNull;
    private boolean mapBlankArrayToNull;
    private boolean mapToSourceDeclaration;

    public ToStringAnnotationValueVisitor mapVoidToNull(boolean val) {
        this.mapVoidToNull = val;
        return this;
    }

    public ToStringAnnotationValueVisitor mapBooleanToNull(boolean val) {
        this.mapFalseToNull = val;
        return this;
    }

    public ToStringAnnotationValueVisitor mapEmptyStringToNull(boolean val) {
        this.mapEmptyStringToNull = val;
        return this;
    }

    public ToStringAnnotationValueVisitor mapBlankArrayToNull(boolean val) {
        this.mapBlankArrayToNull = val;
        return this;
    }

    public ToStringAnnotationValueVisitor mapToSourceDeclaration(boolean val) {
        this.mapToSourceDeclaration = val;
        return this;
    }

    @Override
    public String visit(AnnotationValue av, Object o) {
        return av.accept(this, o);
    }

    @Override
    public String visitBoolean(boolean b, Object o) {
        String val =  String.valueOf(b);
        if (mapFalseToNull && !Boolean.parseBoolean(val)) {
            val = null;
        }
        return val;
    }

    @Override
    public String visitByte(byte b, Object o) {
        return String.valueOf(b);
    }

    @Override
    public String visitChar(char c, Object o) {
        return String.valueOf(c);
    }

    @Override
    public String visitDouble(double d, Object o) {
        return String.valueOf(d);
    }

    @Override
    public String visitFloat(float f, Object o) {
        return String.valueOf(f);
    }

    @Override
    public String visitInt(int i, Object o) {
        return String.valueOf(i);
    }

    @Override
    public String visitLong(long i, Object o) {
        return String.valueOf(i);
    }

    @Override
    public String visitShort(short s, Object o) {
        return String.valueOf(s);
    }

    @Override
    public String visitString(String s, Object o) {
        if (mapEmptyStringToNull && Objects.nonNull(s) && s.isBlank()) {
            return null;
        }

        if (mapToSourceDeclaration) {
            return "\"" + s + "\"";
        }

        return s;
    }

    @Override
    public String visitType(TypeMirror t, Object o) {
        String val = t.toString();
        if (mapVoidToNull && ("void".equals(val) || Void.class.getName().equals(val))) {
            val = null;
        }
        return val;
    }

    @Override
    public String visitEnumConstant(VariableElement c, Object o) {
        return String.valueOf(c.getSimpleName());
    }

    @Override
    public String visitAnnotation(AnnotationMirror a, Object o) {
        // todo do something (nested annotations)
        return null;
    }

    @Override
    public String visitArray(List<? extends AnnotationValue> vals, Object o) {
        List<String> values = new ArrayList<>(vals.size());

        for (AnnotationValue val : vals) {
            String stringVal = val.accept(this, null);
            if (stringVal != null) {
                values.add(stringVal);
            }
        }

        String result = String.join(", ", values);
        if (mapBlankArrayToNull && result.isBlank()) {
            result = null;
        } else if (Objects.nonNull(result) && mapToSourceDeclaration) {
            result = "{";
            for (AnnotationValue val : vals) {
                String stringVal = val.accept(this, null);
                if (stringVal != null) {
                    if (result.length() > 1) {
                        result += ", ";
                    }
                    result += stringVal;
                }
            }
            result += "}";
        }

        return result;
    }

    @Override
    public String visitUnknown(AnnotationValue av, Object o) {
        return null;
    }

}
