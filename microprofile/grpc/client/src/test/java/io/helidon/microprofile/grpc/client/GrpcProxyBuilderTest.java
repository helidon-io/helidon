/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.client;

import java.util.Collection;
import java.util.concurrent.CompletionStage;

import io.helidon.microprofile.grpc.core.Unary;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcProxyBuilderTest {
    @Test
    public void shouldCreateProxyForMethodWithWithNestedGenerics() {
        TestService service = GrpcProxyBuilder.create(TestService.class).build();
        assertThat(service, is(notNullValue()));
    }

    public interface TestService {
       @Unary
       CompletionStage<Collection<String>> getBooks();
    }
}
