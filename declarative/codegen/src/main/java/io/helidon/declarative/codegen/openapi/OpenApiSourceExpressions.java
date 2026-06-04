/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.declarative.codegen.openapi;

import java.util.List;

import static io.helidon.declarative.codegen.openapi.OpenApiCodegenTypes.OPENAPI_DOCUMENT_CONTEXT_SUPPORT;

final class OpenApiSourceExpressions {
    private final OpenApiAnnotationValidator validator;

    OpenApiSourceExpressions(OpenApiAnnotationValidator validator) {
        this.validator = validator;
    }

    String stringLiteral(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '\\' -> result.append("\\\\");
            case '"' -> result.append("\\\"");
            case '\n' -> result.append("\\n");
            case '\r' -> result.append("\\r");
            case '\t' -> result.append("\\t");
            default -> result.append(ch);
            }
        }
        return result.append('"').toString();
    }

    String stringExpression(String value) {
        return OPENAPI_DOCUMENT_CONTEXT_SUPPORT.fqName() + ".resolveExpression(context, " + stringLiteral(value) + ")";
    }

    String validatedStringExpression(String value) {
        return stringLiteral(validator.expressionDefaultValue(value));
    }

    String validatedStringListExpression(List<String> values) {
        if (values.isEmpty()) {
            return "java.util.List.of()";
        }
        StringBuilder result = new StringBuilder("java.util.List.of(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(validatedStringExpression(values.get(i)));
        }
        return result.append(")").toString();
    }
}
