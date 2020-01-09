/*
 * Copyright (c)  2020 Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package io.helidon.microprofile.reactive;

import org.eclipse.microprofile.reactive.streams.operators.tck.ReactiveStreamsTck;
import org.eclipse.microprofile.reactive.streams.operators.tck.spi.CoupledStageVerification;
import org.eclipse.microprofile.reactive.streams.operators.tck.spi.FlatMapStageVerification;
import org.reactivestreams.tck.TestEnvironment;

public class HelidonReactiveStreamsTckTest extends ReactiveStreamsTck<HelidonReactiveStreamEngine> {

    public HelidonReactiveStreamsTckTest() {
        super(new TestEnvironment(200, 200, false));
    }

    @Override
    protected HelidonReactiveStreamEngine createEngine() {
        return new HelidonReactiveStreamEngine();
    }

    @Override
    protected boolean isEnabled(Object test) {
        // Remove when TCK test issues are solved
        // https://github.com/eclipse/microprofile-reactive-streams-operators/issues/133
        return !(test instanceof FlatMapStageVerification.InnerSubscriberVerification)
                // https://github.com/eclipse/microprofile-reactive-streams-operators/issues/131
                && !(test instanceof CoupledStageVerification.ProcessorVerification);
    }
}
