/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.service.configuration.kubernetes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A non-{@linkplain
 * io.helidon.service.configuration.api.System#isAuthoritative()
 * authoritative} {@link io.helidon.service.configuration.api.System}
 * implementation that {@linkplain #isEnabled() is enabled} when
 * running on any of several possible <a
 * href="https://kubernetes.io/">Kubernetes</a> systems.
 *
 * @see #isEnabled()
 *
 * @see <a href="https://kubernetes.io/">Kubernetes</a>
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public final class KubernetesSystem extends io.helidon.service.configuration.api.System {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link KubernetesSystem} whose {@linkplain
     * #getName() name} is {@code kubernetes} and whose {@linkplain
     * #isAuthoritative() authoritative status} is {@code false}.
     *
     * @see io.helidon.service.configuration.api.System#System(String, boolean)
     */
    public KubernetesSystem() {
        super("kubernetes", false /* not authoritative; don't know if it's minikube, GKE, AKS, etc. */);
    }


    /*
     * Instance methods.
     */


    /**
     * Returns {@code true} if there is a file named {@code
     * /proc/1/cpuset} that contains at least one line starting with
     * {@code /kubepods/}.
     *
     * @return {@code true} if the caller is running on any of several
     * possible <a href="https://kubernetes.io/">Kubernetes</a>
     * systems; {@code false} otherwise
     *
     * @see io.helidon.service.configuration.api.System#isEnabled()
     *
     * @see <a href="https://kubernetes.io/">Kubernetes</a>
     */
    @Override
    public boolean isEnabled() {
        try {
            return
                Files.lines(Paths.get("/proc/1/cpuset"), StandardCharsets.UTF_8)
                .filter(l -> l.startsWith("/kubepods/"))
                .findAny()
                .isPresent();
        } catch (final IOException ioException) {
            return false;
        }
    }

}
