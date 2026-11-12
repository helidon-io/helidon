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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.helidon.codegen.classmodel.Method;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;

final class JdbcCallableGenerator {
    private JdbcCallableGenerator() {
    }

    static void terminal(JdbcMethodPlan plan, Method.Builder method, String statement, Map<String, String> parameters) {
        JdbcCallablePlan call = plan.callable();
        Set<String> names = new HashSet<>(parameters.values());
        names.add(statement);
        String callName = unique("__helidonCall", names);
        String result = unique("__helidonResult", names);
        String row = unique("__helidonRow", names);
        method.addContent(JdbcCodegenTypes.CALL).addContent(" ").addContent(callName)
                .addContent(" = ").addContent(statement).addContentLine(".call();");
        for (JdbcCallablePlan.Output output : call.outputs()) {
            method.addContent(callName);
            if (output.marker().isEmpty()) {
                method.addContent(".returns(");
            } else {
                method.addContent(".out(").addContentLiteral(output.name()).addContent(", ");
                if (!plan.configured()) {
                    method.addContent(String.valueOf(output.index())).addContent(", ");
                }
            }
            method.addContent(JdbcCodegenTypes.JDBC_TYPE).addContent(".").addContent(output.type());
            if (!output.typeName().isEmpty()) {
                method.addContent(", ").addContentLiteral(output.typeName());
            } else if (output.scale() >= 0) {
                method.addContent(", ").addContent(String.valueOf(output.scale()));
            }
            method.addContentLine(");");
        }
        if (call.control() != null) {
            if (!call.consumer()) {
                method.addContent("return ");
            }
            method.addContent(callName).addContent(call.consumer() ? ".consume(" : ".map(")
                    .addContent(parameters.get(call.control().elementName())).addContentLine(");");
        } else if (plan.method().typeName().equals(TypeNames.PRIMITIVE_VOID)) {
            method.addContent(callName).addContentLine(".execute();");
        } else if (call.snapshot()) {
            method.addContent("return ").addContent(callName).addContentLine(".outputs();");
        } else {
            method.addContent("return ").addContent(callName).addContent(".map(").addContent(result)
                    .addContentLine(" -> {");
            method.addContent(Objects.class).addContent(".requireNonNull(").addContent(result)
                    .addContentLine(", \"The callable result must not be null.\");");
            if (!call.outputs().isEmpty()) {
                method.addContent(result).addContentLine(".skipRemainingResults();");
                if (call.aggregate()) {
                    Map<JdbcCallablePlan.Output, String> locals = new LinkedHashMap<>();
                    // Capture scalars before selecting any cursors, preserving constructor order through locals.
                    List<JdbcCallablePlan.Output> ordered = new ArrayList<>(call.components());
                    ordered.sort(Comparator.comparing(output -> output.type().equals("REF_CURSOR")));
                    for (JdbcCallablePlan.Output output : ordered) {
                        String local = unique("__helidonOutput", names);
                        locals.put(output, local);
                        method.addContent(output.valueType()).addContent(" ").addContent(local).addContent(" = ");
                        output(method, output, output.valueType(), result, row);
                        method.addContentLine(";");
                    }
                    method.addContent("return new ").addContent(plan.method().typeName()).addContent("(");
                    for (int index = 0; index < call.components().size(); index++) {
                        if (index > 0) {
                            method.addContent(", ");
                        }
                        method.addContent(locals.get(call.components().get(index)));
                    }
                    method.addContentLine(");");
                } else {
                    method.addContent("return ");
                    output(method, call.outputs().getFirst(), plan.method().typeName(), result, row);
                    method.addContentLine(";");
                }
            } else {
                method.addContent("while (").addContent(result).addContent(".resultKind() == ")
                        .addContent(JdbcCodegenTypes.JDBC_CLIENT).addContentLine(".CallResultKind.UPDATE_COUNT) {")
                        .addContent(result).addContentLine(".next();").addContentLine("}");
                method.addContent("if (").addContent(result).addContent(".resultKind() != ")
                        .addContent(JdbcCodegenTypes.JDBC_CLIENT).addContentLine(".CallResultKind.ROWS) {")
                        .addContent("throw new ").addContent(TypeName.create("io.helidon.data.DataException"))
                        .addContentLine("(\"The callable statement returned no direct rows.\");")
                        .addContentLine("}").addContent("return ").addContent(result).addContent(".rows(");
                boolean nullable = plan.returnShape() == JdbcMethodPlan.ReturnShape.OPTIONAL
                        && plan.mappingKind() == JdbcMethodPlan.MappingKind.SCALAR;
                mapper(method, plan, row, nullable);
                method.addContent(")");
                cardinality(method, plan.method().typeName());
                if (nullable) {
                    method.addContent(".flatMap(").addContent(row).addContent(" -> ").addContent(row).addContent(")");
                }
                method.addContentLine(";");
            }
            method.decreaseContentPadding().addContentLine("});");
        }
    }

    private static void output(Method.Builder method, JdbcCallablePlan.Output output, TypeName type,
                               String result, String row) {
        if (!output.type().equals("REF_CURSOR")) {
            boolean optional = type.isOptional();
            TypeName scalar = optional ? type.typeArguments().getFirst() : type;
            method.addContent(result).addContent(optional ? ".optional(" : ".get(")
                    .addContentLiteral(output.name()).addContent(", ").addContent(scalar.boxed()).addContent(".class)");
            return;
        }
        boolean optionalList = type.isOptional() && type.typeArguments().getFirst().isList();
        method.addContent(result).addContent(optionalList ? ".optionalCursor(" : ".cursor(")
                .addContentLiteral(output.name()).addContent(", ");
        mapper(method, output.mapping(), row, false);
        method.addContent(")");
        if (optionalList) {
            method.addContent(".map(").addContent(row).addContent(" -> ").addContent(row).addContent(".list())");
        } else {
            cardinality(method, type);
        }
    }

    private static void mapper(Method.Builder method, JdbcMethodPlan plan, String row, boolean nullable) {
        if (plan.mappingKind() == JdbcMethodPlan.MappingKind.SCALAR) {
            method.addContent(row).addContent(" -> ").addContent(Objects.class).addContent(".requireNonNull(")
                    .addContent(row).addContent(", \"The current row must not be null.\")")
                    .addContent(nullable ? ".optional(1, " : ".get(1, ")
                    .addContent(plan.mappedType().boxed()).addContent(".class)");
            return;
        }
        method.addContent(switch (plan.mappingKind()) {
        case RECORD -> plan.mapperFieldName();
        case SERVICE, EXPLICIT -> "this." + plan.mapperFieldName();
        case NONE, SCALAR -> throw new IllegalStateException("A callable row source requires a mapper.");
        });
    }

    private static void cardinality(Method.Builder method, TypeName type) {
        method.addContent(type.isOptional() ? ".optional()" : type.isList() ? ".list()" : ".one()");
    }

    private static String unique(String base, Set<String> names) {
        String candidate = base;
        for (int index = 2; !names.add(candidate); index++) {
            candidate = base + index;
        }
        return candidate;
    }
}
