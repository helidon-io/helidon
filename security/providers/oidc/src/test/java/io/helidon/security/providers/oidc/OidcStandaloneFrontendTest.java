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

package io.helidon.security.providers.oidc;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.security.SecurityEnvironment;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcStandaloneFrontendTest {
    @Test
    void frontendDiscoveryDoesNotRequireOptionalWebServer() throws Exception {
        String classpath = Arrays.stream(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                                                 .split(Pattern.quote(File.pathSeparator)))
                .filter(path -> !path.contains("helidon-webserver") && !path.replace('\\', '/').contains("/webserver/"))
                .collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                             "-cp", classpath, StandaloneMain.class.getName())
                .redirectErrorStream(true)
                .start();
        try {
            assertThat("standalone process finished", process.waitFor(30, TimeUnit.SECONDS), is(true));
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(),
                                                                                 StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }
            assertThat(output, process.exitValue(), is(0));
        } finally {
            process.destroyForcibly();
        }
    }

    public static class StandaloneMain {
        public static void main(String[] args) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName("io.helidon.webserver.http.ServerRequest"));
            URI expected = URI.create("https://frontend.example/path");
            SecurityEnvironment environment = SecurityEnvironment.builder().targetUri(expected).build();

            assertThat(OidcUtil.requestedUri(environment), is(expected));
            Contexts.runInContext(Context.builder().id("standalone-oidc").build(),
                                  () -> assertThat(OidcUtil.requestedUri(environment), is(expected)));
        }
    }
}
