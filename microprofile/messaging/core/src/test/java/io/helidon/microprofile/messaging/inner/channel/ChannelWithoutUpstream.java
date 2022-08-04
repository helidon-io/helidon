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
package io.helidon.microprofile.messaging.inner.channel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.spi.DeploymentException;
import jakarta.inject.Inject;

import io.helidon.microprofile.messaging.AssertThrowException;

import org.eclipse.microprofile.reactive.messaging.Channel;
import org.reactivestreams.Publisher;

@ApplicationScoped
@AssertThrowException(DeploymentException.class)
public class ChannelWithoutUpstream {

    @Inject
    @Channel("channel-without-upstream")
    private Publisher<String> publisher;
}
