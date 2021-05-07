/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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
package io.helidon.integrations.jedis.cdi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Named;

import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ApplicationScoped
class TestExtension {

    private SeContainer cdiContainer;
    
    TestExtension() {
        super();
    }
    
    @BeforeEach
    void startCdiContainer() {
        System.setProperty("redis.clients.jedis.JedisPoolConfig.fred.maxIdle", "10");
        final SeContainerInitializer initializer = SeContainerInitializer.newInstance();
        assertThat(initializer, is(notNullValue()));
        this.cdiContainer = initializer.initialize();
    }

    @AfterEach
    void shutDownCdiContainer() {
        if (this.cdiContainer != null) {
            this.cdiContainer.close();
        }
    }

    private void onStartup(@Observes @Initialized(ApplicationScoped.class) final Object event,
                           final Config config,
                           @Named("fred") final JedisPoolConfig fredJedisPoolConfig,
                           @Named("fred") final JedisPool fredJedisPool) {
        assertThat(event, is(notNullValue()));
        assertThat(config, is(notNullValue()));
        assertThat(fredJedisPoolConfig, is(notNullValue()));
        assertThat(fredJedisPoolConfig.getMaxIdle(), is(10));
        assertThat(fredJedisPool, is(notNullValue()));
    }
  
    @Test
    void testBeansWereAdded() {
        final Instance<JedisPoolConfig> poolConfigs = CDI.current().select(JedisPoolConfig.class);
        assertThat(poolConfigs, is(notNullValue()));
        boolean found = false;
        for (final JedisPoolConfig poolConfig : poolConfigs) {
            found = found || poolConfig != null;
        }
        assertThat("No JedisPoolConfigs found", found);
    }
    
}
