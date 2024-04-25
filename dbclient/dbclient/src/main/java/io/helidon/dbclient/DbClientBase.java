/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.dbclient;

/**
 * Base {@link DbClient} implementation.
 */
public abstract class DbClientBase implements DbClient {

    private final DbClientContext context;

    /**
     * Create a new instance.
     *
     * @param context context
     */
    protected DbClientBase(DbClientContext context) {
        this.context = context;
    }

    /**
     * Get the {@link DbClientContext}.
     *
     * @return DbClientContext
     */
    public DbClientContext context() {
        return context;
    }

    @Override
    public void close() {
        // No-op by default
    }
}
