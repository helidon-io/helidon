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

package io.helidon.grpc.server;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import services.EchoService;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcRoutingTest {

    @Test
    public void shouldValidateRegisteredServiceConfigurer() {
        AtomicBoolean validated = new AtomicBoolean();

        GrpcRouting.builder()
                .register(new EchoService(), new ServiceDescriptor.Configurer() {
                    @Override
                    public void configure(ServiceDescriptor.Rules rules) {
                    }

                    @Override
                    public void validate(ServiceDescriptor descriptor) {
                        assertThat(descriptor.name(), is("EchoService"));
                        validated.set(true);
                    }
                })
                .build();

        assertThat(validated.get(), is(true));
    }
}
