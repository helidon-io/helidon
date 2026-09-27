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

package io.helidon.integrations.oci.tls.certificates;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OciCertificateBundleTlsManagerModuleTest {
    private static final String MODULE_NAME = "test.oci.tls.certificates";
    private static final String MODULE_INFO = """
            module test.oci.tls.certificates {
                requires io.helidon.integrations.oci.tls.certificates;
            }
            """;
    private static final String API_USAGE = """
            package test.oci.tls.certificates;

            import io.helidon.builder.api.Prototype;
            import io.helidon.common.Builder;
            import io.helidon.common.tls.TlsManager;
            import io.helidon.config.Config;
            import io.helidon.integrations.oci.tls.certificates.OciCertificateBundleTlsManager;
            import io.helidon.integrations.oci.tls.certificates.OciCertificateBundleTlsManagerConfig;

            @SuppressWarnings("deprecation")
            final class ApiUsage {
                private ApiUsage() {
                }

                static void compile(Config config, io.helidon.common.config.Config legacyConfig) {
                    OciCertificateBundleTlsManagerConfig.Builder builder = OciCertificateBundleTlsManager.builder();
                    builder.config(config).config(legacyConfig);
                    Prototype.Api prototype = builder.buildPrototype();
                    Builder<?, ?> commonBuilder = builder;
                    TlsManager manager = builder.build();
                }
            }
            """;

    @TempDir
    private Path tempDir;

    @Test
    void publicApiCompilesForDownstreamNamedModule() throws Exception {
        Path moduleRoot = tempDir.resolve("src").resolve(MODULE_NAME);
        Path packageRoot = moduleRoot.resolve("test/oci/tls/certificates");
        Files.createDirectories(packageRoot);
        Path moduleInfo = Files.writeString(moduleRoot.resolve("module-info.java"), MODULE_INFO);
        Path apiUsage = Files.writeString(packageRoot.resolve("ApiUsage.java"), API_USAGE);
        Path classes = Files.createDirectory(tempDir.resolve("classes"));

        StringWriter stdout = new StringWriter();
        StringWriter stderr = new StringWriter();
        var javac = java.util.spi.ToolProvider.findFirst("javac")
                .orElseThrow(() -> new IllegalStateException("javac tool is not available"));
        int result = javac.run(new PrintWriter(stdout),
                               new PrintWriter(stderr),
                               "--release", Integer.toString(Runtime.version().feature()),
                               "-d", classes.toString(),
                               "--module-source-path", tempDir.resolve("src").toString(),
                               "--module-path", modulePath(),
                               moduleInfo.toString(), apiUsage.toString());

        assertThat(stderr + System.lineSeparator() + stdout, result, is(0));
    }

    private static String modulePath() {
        String testClassPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return Arrays.stream(testClassPath.split(File.pathSeparator))
                .filter(path -> !path.endsWith(File.separator + "target" + File.separator + "test-classes"))
                .reduce((first, second) -> first + File.pathSeparator + second)
                .orElseThrow(() -> new IllegalStateException("test class path is empty"));
    }
}
