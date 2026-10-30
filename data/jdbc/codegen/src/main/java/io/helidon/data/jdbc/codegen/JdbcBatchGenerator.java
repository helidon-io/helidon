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
package io.helidon.data.jdbc.codegen;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

/**
 * Emits lazy source alignment and capture through the public batch terminal.
 */
final class JdbcBatchGenerator {
    private JdbcBatchGenerator() {
    }

    static void generate(JdbcMethodPlan plan, Method.Builder method, Map<String, String> parameterNames,
                         Set<String> names, String statement) {
        JdbcBatchPlan batch = plan.batchPlan();
        names.add(statement);
        String rows = unique(names, "rows");
        String bindings = unique(names, "bindings");
        String row = unique(names, "row");
        String index = unique(names, "index");
        List<String> iterators = new ArrayList<>();
        List<String> elements = new ArrayList<>();
        List<String> available = new ArrayList<>();
        // Validate all source references before staging and obtain iterators only after the terminal claims its stage.
        for (JdbcBatchPlan.Source source : batch.sources()) {
            method.addContent(Objects.class).addContent(".requireNonNull(")
                    .addContent(parameterNames.get(source.parameter().elementName()))
                    .addContentLine(", \"The batch source must not be null.\");");
            iterators.add(unique(names, "iterator"));
            elements.add(unique(names, "element"));
            available.add(unique(names, "available"));
        }
        method.addContent(JdbcCodegenTypes.JDBC_CLIENT_STATEMENT).addContent(" " + statement + " = ")
                .addContent(JdbcCodegenTypes.GENERATED_JDBC_DATA).addContent(".createGenerated(")
                .addContent(JdbcCodegenConstants.JDBC_CLIENT_NAME).addContent(", " + plan.sqlFieldName() + ", ")
                .addContent(String.valueOf(plan.parameterPlan().parameterCount())).addContentLine(");");
        method.addContent(Iterable.class).addContent("<Object[]> " + rows + " = () -> {\n");
        for (int i = 0; i < batch.sources().size(); i++) {
            JdbcBatchPlan.Source source = batch.sources().get(i);
            String parameter = parameterNames.get(source.parameter().elementName());
            method.addContent("var " + iterators.get(i) + " = ");
            if (source.array()) {
                method.addContent(IntStream.class).addContent(".range(0, " + parameter + ".length)")
                        .addContent(".mapToObj(" + index + " -> " + parameter + "[" + index + "]).iterator()");
            } else {
                method.addContent(parameter + (source.iterable() ? ".iterator()" : ""));
            }
            method.addContentLine(";");
            method.addContent(Objects.class).addContent(".requireNonNull(" + iterators.get(i))
                    .addContentLine(", \"The batch iterator must not be null.\");");
        }
        method.addContent("return new ").addContent(Iterator.class).addContentLine("<Object[]>() {")
                .addContentLine("public boolean hasNext() {");
        for (int i = 0; i < iterators.size(); i++) {
            method.addContentLine("boolean " + available.get(i) + " = " + iterators.get(i) + ".hasNext();");
        }
        for (int i = 1; i < iterators.size(); i++) {
            method.addContentLine("if (" + available.getFirst() + " != " + available.get(i) + ") {")
                    .addContent("throw new ").addContent(IllegalArgumentException.class)
                    .addContentLine("(\"Batch sources must have equal lengths.\");")
                    .addContentLine("}");
        }
        method.addContentLine("return " + available.getFirst() + ";")
                .addContentLine("}")
                .addContentLine("public Object[] next() {")
                .addContentLine("if (!hasNext()) {")
                .addContent("throw new ").addContent(NoSuchElementException.class).addContentLine("();")
                .addContentLine("}");
        for (int i = 0; i < iterators.size(); i++) {
            boolean record = !JdbcScalarTypes.isScalar(batch.sources().get(i).element());
            method.addContent("var " + elements.get(i) + " = ");
            if (record) {
                method.addContent(Objects.class).addContent(".requireNonNull(");
            }
            method.addContent(iterators.get(i) + ".next()" + (record ? ")" : "")).addContentLine(";");
        }
        method.addContentLine("return new Object[] {");
        for (JdbcBatchPlan.Value value : batch.values()) {
            String expression = value.sourceIndex() < 0 ? parameterNames.get(value.parameter().elementName())
                    : elements.get(value.sourceIndex()) + (value.component() == null ? "" : "." + value.component() + "()");
            method.addContentLine(expression + ",");
        }
        method.decreaseContentPadding().addContentLine("};").addContentLine("}")
                .decreaseContentPadding().addContentLine("};")
                .decreaseContentPadding().addContentLine("};");
        if (!plan.method().typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            method.addContent("return ");
        }
        method.addContentLine(statement + ".executeBatch(" + rows + ", (" + bindings + ", " + row + ") -> {");
        method.addContent(Objects.class).addContent(".requireNonNull(" + bindings)
                .addContentLine(", \"The batch bindings must not be null.\");")
                .addContent(Objects.class).addContent(".requireNonNull(" + row)
                .addContentLine(", \"The batch item must not be null.\");");
        Map<TypedElementInfo, Integer> slots = new IdentityHashMap<>();
        for (int i = 0; i < batch.values().size(); i++) {
            slots.put(batch.values().get(i).parameter(), i);
        }
        for (JdbcSqlParameterPlan.Bind bind : plan.parameterPlan().binds()) {
            String value = row + "[" + slots.get(bind.parameter()) + "]";
            method.addContentLine("if (" + value + " == null) {")
                    .addContent(bindings + ".bindNull(" + bind.position() + ", ")
                    .addContent(JdbcCodegenTypes.JDBC_TYPE).addContentLine("." + bind.nullJdbcTypeConstant() + ");")
                    .decreaseContentPadding().addContentLine("} else {")
                    .addContentLine(bindings + ".bind(" + bind.position() + ", " + value + ");")
                    .addContentLine("}");
        }
        method.decreaseContentPadding().addContentLine("});");
    }

    private static String unique(Set<String> names, String candidate) {
        while (!names.add(candidate)) {
            candidate = "_" + candidate;
        }
        return candidate;
    }
}
