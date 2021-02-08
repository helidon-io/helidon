/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.microprofile.cloud.ocifunctions;

import java.util.function.Function;

import io.helidon.microprofile.cloud.common.CommonCloudFunction;

/**
 *
 * Helidon OCI Function.
 * This is the class that should be specified as entry point in OCI FN
 *
 * @param <I> input of the function
 * @param <O> output of the function
 */
public class OCIFunction<I, O> extends CommonCloudFunction<Function<I, O>> implements Function<I, O> {

    @Override
    public O apply(I i) {
        return delegate().apply(i);
    }

}
