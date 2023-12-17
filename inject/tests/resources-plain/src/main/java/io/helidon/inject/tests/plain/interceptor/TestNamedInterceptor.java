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

package io.helidon.inject.tests.plain.interceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.types.Annotation;
import io.helidon.common.types.Annotations;
import io.helidon.common.types.ElementKind;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementInfo;
import io.helidon.inject.service.Interception;
import io.helidon.inject.service.InvocationContext;

import static io.helidon.common.types.TypeNames.PRIMITIVE_LONG;
import static io.helidon.common.types.TypeNames.STRING;

@SuppressWarnings({"ALL", "unchecked"})
public class TestNamedInterceptor implements Interception.Interceptor {
    public static final AtomicInteger CONSTRUCTOR_COUNTER = new AtomicInteger();
    public static final List<Invocation> INVOCATIONS = new ArrayList<>();

    private static final TypeName INTERCEPTED_ANNO = TypeName.create(InterceptorBasedAnno.class);

    public TestNamedInterceptor() {
        CONSTRUCTOR_COUNTER.incrementAndGet();
    }

    @Override
    public <V> V proceed(InvocationContext ctx,
                         Chain<V> chain,
                         Object... args) throws Exception {
        assert (ctx != null);

        TypedElementInfo elementInfo = ctx.elementInfo();
        Annotation annotation = elementInfo.findAnnotation(INTERCEPTED_ANNO)
                .or(() -> Annotations.findFirst(INTERCEPTED_ANNO, ctx.typeAnnotations()))
                .orElse(null);

        if (annotation == null) {
            // this is an error
            throw new IllegalStateException("Invoked an interceptor without annotation being present on element: " + elementInfo);
        }

        INVOCATIONS.add(new Invocation(elementInfo.kind(), elementInfo.elementName(), annotation.value().orElse("")));

        if (elementInfo.typeName().equals(PRIMITIVE_LONG)) {
            V result = chain.proceed(args);
            long longResult = (Long) result;
            Object interceptedResult = (longResult * 2);
            return (V) interceptedResult;
        } else if (elementInfo.typeName().equals(STRING)) {
            V result = chain.proceed(args);
            return (V) ("intercepted:" + result);
        } else {
            return chain.proceed(args);
        }
    }

    public record Invocation(ElementKind kind, String name, String value) {
    }
}
