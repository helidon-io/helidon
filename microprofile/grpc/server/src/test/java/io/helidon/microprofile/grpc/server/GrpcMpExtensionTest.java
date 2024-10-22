/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.util.HashSet;
import java.util.Set;

import io.helidon.microprofile.grpc.server.spi.GrpcMpContext;
import io.helidon.microprofile.grpc.server.spi.GrpcMpExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;

@HelidonTest
@AddExtension(GrpcMpCdiExtension.class)
@AddBean(GrpcMpExtensionTest.Extension1.class)
@AddBean(GrpcMpExtensionTest.Extension2.class)
public class GrpcMpExtensionTest {

    private static final Set<Class<?>> EXTENSIONS_LOADED = new HashSet<>();

    @Test
    void testExtensions() {
        assertThat(EXTENSIONS_LOADED, hasItems(Extension1.class, Extension2.class));

    }

    @ApplicationScoped
    public static class Extension1 implements GrpcMpExtension {

        @Override
        public void configure(GrpcMpContext context) {
            if (context.beanManager() != null && context.routing() != null) {
                EXTENSIONS_LOADED.add(getClass());
            }
        }
    }

    @ApplicationScoped
    public static class Extension2 implements GrpcMpExtension {

        @Override
        public void configure(GrpcMpContext context) {
            if (context.beanManager() != null && context.routing() != null) {
                EXTENSIONS_LOADED.add(getClass());
            }
        }
    }
}
