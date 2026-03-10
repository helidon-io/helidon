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
package io.helidon.builder.codegen;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import io.helidon.codegen.CodegenContext;
import io.helidon.codegen.CodegenException;
import io.helidon.common.types.TypeInfo;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypeNames;
import io.helidon.common.types.TypedElementInfo;

import static io.helidon.codegen.ElementInfoPredicates.isMethod;

/**
 * Type resolver.
 */
class TypeResolver {

    private final CodegenContext ctx;

    TypeResolver(CodegenContext ctx) {
        this.ctx = ctx;
    }

    TypeInfo resolveTypeParameter(TypeName typeName, TypeName enclosingTypeName) {
        var typeArgs = enclosingTypeName.typeArguments();
        if (!typeArgs.isEmpty()) {
            // the type parameter name
            var rawTargetTypeName = typeName.genericTypeName();

            // search the type parameter name
            var rawTypeName = enclosingTypeName.genericTypeName();
            var rawTypeInfo = ctx.typeInfo(rawTypeName).orElse(null);
            if (rawTypeInfo != null) {
                var rawTypeArgs = rawTypeInfo.typeName().typeArguments();
                for (int i = 0; i < rawTypeArgs.size(); i++) {
                    var rawTypeArg = rawTypeArgs.get(i).genericTypeName();
                    if (rawTypeArg.equals(rawTargetTypeName)) {
                        if (rawTypeArgs.size() != typeArgs.size()) {
                            return null;
                        }
                        var typeArg = typeArgs.get(i);
                        var upperBounds = typeArg.upperBounds();
                        if (upperBounds.isEmpty()) {
                            var rawtargetTypeArg = typeArg.genericTypeName();
                            if (!rawtargetTypeArg.equals(rawTypeArg)) {
                                return ctx.typeInfo(rawtargetTypeArg).orElse(null);
                            }
                        } else if (upperBounds.size() == 1) {
                            return ctx.typeInfo(upperBounds.getFirst()).orElse(null);
                        }
                    }
                }
            }
        }
        return null;
    }

    Set<TypedElementInfo> methodHierarchy(TypeInfo enclosingTypeInfo, TypedElementInfo methodInfo) {
        var hierarchy = new LinkedHashSet<TypedElementInfo>();
        for (var superTypeInfo : typeHierarchy(enclosingTypeInfo, it -> it != enclosingTypeInfo)) {
            for (var superMethodInfo : superTypeInfo.elementInfo()) {
                if (isMethod(superMethodInfo) && isOverride(methodInfo, superMethodInfo)) {
                    hierarchy.add(superMethodInfo);
                }
            }
        }
        return hierarchy;
    }

    Set<TypeInfo> typeHierarchy(TypeInfo typeInfo) {
        return typeHierarchy(typeInfo, it -> true);
    }

    Set<TypeInfo> typeHierarchy(TypeInfo typeInfo, Predicate<TypeInfo> predicate) {
        var hierarchy = new LinkedHashSet<TypeInfo>();
        var stack = new ArrayDeque<TypeInfo>();
        stack.push(typeInfo);
        while (!stack.isEmpty()) {
            var e = stack.pop();
            e.superTypeInfo().ifPresent(stack::push);
            for (TypeInfo info : e.interfaceTypeInfo()) {
                stack.push(info);
            }
            if (predicate.test(e)) {
                hierarchy.add(e);
            }
        }
        return hierarchy;
    }

    boolean isOverride(TypedElementInfo methodInfo, TypedElementInfo superMethodInfo) {
        if (methodInfo.elementName().equals(superMethodInfo.elementName())) {
            var argsInfo = methodInfo.parameterArguments();
            var superArgsInfo = superMethodInfo.parameterArguments();
            if (argsInfo.size() == superArgsInfo.size()) {
                for (int i = 0; i < argsInfo.size(); i++) {
                    var argInfo = argsInfo.get(i);
                    var superArgInfo = superArgsInfo.get(i);
                    var argTypeName = argInfo.typeName().genericTypeName().boxed();
                    if (argTypeName.equals(TypeNames.OBJECT)) {
                        continue;
                    }
                    var superArgTypeName = superArgInfo.typeName().genericTypeName().boxed();
                    if (superArgTypeName.equals(TypeNames.OBJECT)) {
                        return false;
                    }
                    var argInfoType = ctx.typeInfo(argTypeName)
                            .orElseThrow(() -> new CodegenException(
                                    "Cannot resolve type info: " + argTypeName, methodInfo));
                    var superArgInfoType = ctx.typeInfo(superArgTypeName)
                            .orElseThrow(() -> new CodegenException(
                                    "Cannot resolve type info: " + superArgTypeName, methodInfo));
                    if (!isSubtype(argInfoType, superArgInfoType.typeName())) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    boolean isSubtype(TypeInfo typeInfo1, TypeName... typeNames) {
        for (var superTypeInfo : typeHierarchy(typeInfo1)) {
            var superRawTypeName = superTypeInfo.typeName().genericTypeName();
            for (var typeName : typeNames) {
                var rawTypeName = typeName.genericTypeName();
                if (rawTypeName.equals(superRawTypeName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
