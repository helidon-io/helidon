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
 *
 */
package io.helidon.jbatch.example.jobs;

import jakarta.batch.api.AbstractBatchlet;

/**
 * Batchlet example.
 */
public class MyBatchlet extends AbstractBatchlet {

    /**
     * Run inside a batchlet.
     *
     * @return String with status.
     */
    @Override
    public String process() {
        System.out.println("Running inside a batchlet");
        return "COMPLETED";
    }

}
