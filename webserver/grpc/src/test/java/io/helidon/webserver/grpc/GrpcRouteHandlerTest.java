/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import io.helidon.grpc.core.WeightedBag;

import com.google.protobuf.Descriptors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class GrpcRouteHandlerTest {

    @Test
    void testBadServiceNames() throws Descriptors.DescriptorValidationException {
        assertThrows(IllegalArgumentException.class,
                () -> GrpcRouteHandler.unary(Strings.getDescriptor(),
                                             "foo",
                                             "Upper",
                                             null,
                                             WeightedBag.create()));
        assertThrows(IllegalArgumentException.class,
                () -> GrpcRouteHandler.unary(Strings.getDescriptor(),
                                             "StringService",
                                             "foo",
                                             null,
                                             WeightedBag.create()));
    }
}
