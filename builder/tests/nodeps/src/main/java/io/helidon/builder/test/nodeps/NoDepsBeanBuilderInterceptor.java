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

package io.helidon.builder.test.nodeps;

/**
 * See {@link NoDepsInterceptedBean}. Notice how the Builder annotation on {@link NoDepsInterceptedBean} sets the
 * {@link io.helidon.builder.Builder#requireLibraryDependencies()} attribute to {@code false}, which is why this class does not
 * need to implement {@link io.helidon.builder.BuilderInterceptor}.
 */
class NoDepsBeanBuilderInterceptor /* implements Interceptor<DefaultInterceptedBean.Builder> */ {
    private int callCount;

    private NoDepsBeanBuilderInterceptor() {
    }

    static NoDepsBeanBuilderInterceptor create() {
        return new NoDepsBeanBuilderInterceptor();
    }

//    @Override
    NoDepsInterceptedBeanDefault.Builder intercept(NoDepsInterceptedBeanDefault.Builder target) {
        if (callCount++ > 0) {
            throw new AssertionError();
        }
        return target.helloMessage("Hello " + target.name());
    }
}
