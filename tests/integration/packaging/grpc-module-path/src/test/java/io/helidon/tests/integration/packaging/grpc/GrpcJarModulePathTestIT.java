package io.helidon.tests.integration.packaging.grpc;

import io.helidon.tests.integration.harness.ProcessMonitor;
import io.helidon.tests.integration.harness.ProcessRunner;
import io.helidon.tests.integration.harness.ProcessRunner.ExecMode;
import io.helidon.tests.integration.harness.WaitStrategy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GrpcJarModulePathTestIT {
    private static final String FINAL_NAME = "helidon-tests-integration-packaging-grpc-module-path";
    private static final String MODULE_NAME = "io.helidon.tests.integration.packaging.grpc";
    private static final String MAIN_CLASS = MODULE_NAME + ".Main";

    @Test
    void testModulePath() {
        try (ProcessMonitor process = ProcessRunner.of(ExecMode.MODULE_PATH)
                .finalName(FINAL_NAME)
                .moduleName(MODULE_NAME)
                .mainClass(MAIN_CLASS)
                .start()
                .await(WaitStrategy.waitForCompletion())) {
            assertEquals(0, process.get().exitValue(), process.output());
        }
    }

    @Test
    void testModulePathValidation() {
        // Detect the javax.annotation split package when jsr305 and java.annotation are both on the module path.
        try (ProcessMonitor process = ProcessRunner.of(ExecMode.MODULE_PATH_VALIDATE)
                .finalName(FINAL_NAME)
                .start()
                .await(WaitStrategy.waitForCompletion())) {
            assertEquals(0, process.get().exitValue(), process.output());
        }
    }
}
