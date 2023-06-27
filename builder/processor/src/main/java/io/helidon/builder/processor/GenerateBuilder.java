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

package io.helidon.builder.processor;

import java.io.PrintWriter;

import io.helidon.common.types.TypeName;

/**
 * Builder is an inner class of the prototype.
 * It extends the base builder, so we can support further extensibility.
 * Class name is always "Builder"
 * Super class name is always "BuilderBase"
 */
final class GenerateBuilder {
    private static final String SOURCE_SPACING = "    ";

    private GenerateBuilder() {
    }

    static void generate(PrintWriter pw,
                         TypeName prototype,
                         TypeName runtimeType,
                         String typeArguments,
                         boolean isFactory,
                         TypeContext typeContext) {
        String prototypeWithTypes = prototype.className() + typeArguments;
        String runtimeTypeWithTypes = runtimeType.className() + typeArguments;
        String typeArgumentNames = "";
        if (!typeArguments.isEmpty()) {
            typeArgumentNames = typeArguments.substring(1, typeArguments.length() - 1) + ", ";
        }

        pw.print(SOURCE_SPACING);
        pw.println("/**");
        pw.print(SOURCE_SPACING);
        pw.print(" * Fluent API builder for {@link ");
        pw.print(runtimeType.className());
        pw.println("}.");
        pw.print(SOURCE_SPACING);
        pw.println(" */");
        pw.print(SOURCE_SPACING);
        // this class is on interface, so it is public by default
        pw.print("class Builder");
        pw.print(typeArguments);
        pw.print(" extends BuilderBase<");
        pw.print(typeArgumentNames);
        pw.print("Builder");
        pw.print(typeArguments);
        pw.print(", ");
        pw.print(prototypeWithTypes);
        pw.print("> implements io.helidon.common.Builder<Builder");
        pw.print(typeArguments);
        pw.print(", ");
        pw.print(runtimeTypeWithTypes);
        pw.println("> {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("private Builder() {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
        /*
        RuntimeObject build()
        Prototype buildPrototype()
         */
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("@Override");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("public ");
        pw.print(prototypeWithTypes);
        pw.println(" buildPrototype() {");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("preBuildPrototype();");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("validatePrototype();");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.print("return new ");
        pw.print(prototype.className());
        pw.print("Impl");
        if (!typeArguments.isEmpty()) {
            pw.print("<>");
        }
        pw.println("(this);");
        pw.print(SOURCE_SPACING);
        pw.print(SOURCE_SPACING);
        pw.println("}");
        pw.println();

        if (isFactory) {
            GenerateAbstractBuilder.buildRuntimeObjectMethod(pw, typeContext, true);
        } else {
            // build method returns the same as buildPrototype method
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("@Override");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print("public ");
            pw.print(runtimeTypeWithTypes);
            pw.println(" build() {");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("return buildPrototype();");
            pw.print(SOURCE_SPACING);
            pw.print(SOURCE_SPACING);
            pw.println("}");
            pw.println();
        }

        // end of class builder
        pw.print(SOURCE_SPACING);
        pw.println("}");
    }
}
