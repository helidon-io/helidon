/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.tests.integration.tools.client;

import java.util.logging.Logger;

import io.helidon.common.LogConfig;
import io.helidon.tests.integration.tools.client.HelidonProcessRunner.ExecType;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * jUnit test life cycle extensions.
 */
public abstract class TestsLifeCycleExtension implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final Logger LOGGER = Logger.getLogger(TestsLifeCycleExtension.class.getName());

    private static final String STORE_KEY = TestsLifeCycleExtension.class.getName();

    protected HelidonProcessRunner runner;

    /**
     * Test setup.
     *
     * @param ec current extension context
     * @throws Exception when test setup fails
     */
    @Override
    public void beforeAll(ExtensionContext ec) throws Exception {
        final Object resource = ec.getRoot().getStore(GLOBAL).get(STORE_KEY);
        if (resource == null) {
            LogConfig.configureRuntime();
            LOGGER.finest("Running beforeAll lifecycle method for the first time, invoking setup()");
            ec.getRoot().getStore(GLOBAL).put(STORE_KEY, this);
            check();
            runner = processRunner();
            if (runner != null) {
                runner.startApplication();
            }
            setup();
        } else {
            LOGGER.finest("Running beforeAll lifecycle method next time, skipping setup()");
        }
    }

    /**
     * Application environment check.
     * This method is executed before starting application using process runner
     * to make sure all application environment (e.g. database) is ready.
     */
    public abstract void check();

    /**
     * Tests application setup.
     * This method is executed after application was started using process runner.
     */
    public abstract void setup();

    /**
     * Helidon process runner provider.
     *
     * @return Helidon process runner
     */
    private HelidonProcessRunner processRunner() {
        return HelidonProcessRunner.create(
                processRunnerExecType(),
                processRunnerModuleName(),
                processRunnerMainClassModuleName(),
                processRunnerMainClass(),
                processRunnerFinalName(),
                processRunnerArgs(),
                processRunnerStartCommand(),
                processRunnerStopCommand());
    }

    /**
     * Process execution type.
     * See {@link ExecType} for supported values.
     *
     * @return execution type to be used by {@link HelidonProcessRunner}
     */
    protected abstract ExecType processRunnerExecType();

    /**
     * Process module name.
     * Name of the application module (JPMS) used for {@link ExecType#MODULE_PATH}.
     *
     * @return application module to be used by {@link HelidonProcessRunner}
     */
    protected abstract String processRunnerModuleName();

    /**
     * Process Main class module name.
     * Name of the module of the main class (JPMS) used for {@link ExecType#MODULE_PATH}.
     * Override this method if the module of the main class differs from the application module.
     * Shall return {@code "io.helidon.microprofile.cdi"} for MP.
     *
     * @return main class module to be used by {@link HelidonProcessRunner}
     */
    protected  String processRunnerMainClassModuleName() {
        return processRunnerModuleName();
    }

    /**
     * Process Main class.
     * Name of the main class to run.
     * Shall return {@code "io.helidon.microprofile.cdi.Main"} for MP.
     *
     * @return Main class to be executed by {@link HelidonProcessRunner}
     */
    protected abstract String processRunnerMainClass();

    /**
     * Process artifact name.
     * This is the expected name of the native image or jar file.
     *
     * @return the native image or jar file to be executed by {@link HelidonProcessRunner}
     */
    protected abstract String processRunnerFinalName();

    /**
     * Arguments to be passed to main method.
     * Override this method to pass custom arguments to main method.
     *
     * @return main method arguments to be passed by {@link HelidonProcessRunner}
     */
    protected String[] processRunnerArgs() {
        return null;
    }

    /**
     * Command to start the server in memory.
     * Used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}.
     *
     * @return startup code to be executed by {@link HelidonProcessRunner}
     */
    protected Runnable processRunnerStartCommand() {
        return null;
    }

    /**
     * Command to stop the server in memory.
     * Used for {@link HelidonProcessRunner.ExecType#IN_MEMORY}.
     *
     * @return shutdown code to be executed by {@link HelidonProcessRunner}
     */
    protected Runnable processRunnerStopCommand() {
        return null;
    }

}
