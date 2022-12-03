/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

/**
 * See {@link io.helidon.builder.Interceptor} for the prototypical output for this generated class.
 */
final class GenerateInterceptorSupport {

    private GenerateInterceptorSupport() {
    }

    static void appendExtraInnerClasses(StringBuilder builder,
                                        BodyContext ctx) {
        if (ctx.doingConcreteType()) {
            return;
        }

        if (!ctx.hasParent()
                && !ctx.requireLibraryDependencies()) {
            builder.append("\n\n\t/**\n"
                                   + "\t * A functional interface that can be used to intercept the target type.\n"
                                   + "\t *\n"
                                   + "\t * @param <T> the type to intercept"
                                   + "\t */\n");
            builder.append("\t@FunctionalInterface\n"
                                   + "\tpublic static interface Interceptor<T> {\n"
                                   + "\t\t /**\n"
                                   + "\t\t * Provides the ability to intercept the target.\n"
                                   + "\t\t *\n"
                                   + "\t\t * @param target the target being intercepted\n"
                                   + "\t\t * @return the mutated or replaced target (must not be null)\n"
                                   + "\t\t */\n"
                                   + "\t\tT intercept(T target);\n"
                                   + "\t}\n");
        }
    }

}
