/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.integrations.consul.cdi;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.literal.NamedLiteral;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeforeBeanDiscovery;
import javax.enterprise.inject.spi.Extension;

import com.ecwid.consul.v1.ConsulClient;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * An {@link Extension} providing CDI integration for the <em>de
 * facto</em> standard <a
 * href="https://github.com/Ecwid/consul-api">Consul Java client</a>.
 */
public class ConsulExtension implements Extension {

    private static final Pattern CONSUL_CLIENT_NAME_PATTERN =
        Pattern.compile("^(?:com\\.ecwid\\.consul\\.ConsulClient\\.([^.]+)\\.(.*)$");

    private final Map<String, Properties> masterProperties;

    private final Config config;

    /**
     * Creates a new {@link ConsulExtension}.
     */
    public ConsulExtension() {
        super();
        this.masterProperties = new HashMap<>();
        this.config = ConfigProvider.getConfig();
        assert this.config != null;
    }

    private void beforeBeanDiscovery(@Observes final BeforeBeanDiscovery event) {
        final Set<? extends String> allPropertyNames = this.getPropertyNames();
        if (allPropertyNames != null && !allPropertyNames.isEmpty()) {
            for (final String propertyName : allPropertyNames) {
                final Optional<String> propertyValue = this.config.getOptionalValue(propertyName, String.class);
                if (propertyValue != null && propertyValue.isPresent()) {
                    final Matcher matcher = CONSUL_CLIENT_NAME_PATTERN.matcher(propertyName);
                    assert matcher != null;
                    if (matcher.matches()) {
                        final String consulClientName = matcher.group(1);
                        Properties properties = this.masterProperties.get(consulClientName);
                        if (properties == null) {
                            properties = new Properties();
                            this.masterProperties.put(consulClientName, properties);
                        }
                        final String consulClientPropertyName = matcher.group(2);
                        properties.setProperty(consulClientPropertyName, propertyValue.get());
                    }
                }
            }
        }
    }

    private Set<String> getPropertyNames() {
        // The MicroProfile Config specification does not say whether
        // property names must be cached or must not be cached
        // (https://github.com/eclipse/microprofile-config/issues/370).
        // It is implied in the MicroProfile Google group
        // (https://groups.google.com/d/msg/microprofile/tvjgSR9qL2Q/M2TNUQrOAQAJ),
        // but not in the specification, that ConfigSources can be
        // mutable and dynamic.  Consequently one would expect their
        // property names to come and go.  Because of this we have to
        // make sure to get all property names from all ConfigSources
        // "by hand".
        //
        // (The MicroProfile Config specification also does not say
        // whether a ConfigSource is thread-safe
        // (https://github.com/eclipse/microprofile-config/issues/369),
        // so iteration over its coming-and-going dynamic property
        // names may be problematic, but there's nothing we can do.)
        //
        // As of this writing, the Helidon MicroProfile Config
        // implementation caches all property names up front, which
        // may not be correct, but is also not forbidden.
        final Set<String> returnValue;
        final Set<String> propertyNames = getPropertyNames(this.config.getConfigSources());
        if (propertyNames == null || propertyNames.isEmpty()) {
            returnValue = Collections.emptySet();
        } else {
            returnValue = Collections.unmodifiableSet(propertyNames);
        }
        return returnValue;
    }

    private static Set<String> getPropertyNames(final Iterable<? extends ConfigSource> configSources) {
        final Set<String> returnValue = new HashSet<>();
        if (configSources != null) {
            for (final ConfigSource configSource : configSources) {
                if (configSource != null) {
                    final Set<? extends String> configSourcePropertyNames = configSource.getPropertyNames();
                    if (configSourcePropertyNames != null && !configSourcePropertyNames.isEmpty()) {
                        returnValue.addAll(configSourcePropertyNames);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(returnValue);
    }



    private void afterBeanDiscovery(@Observes final AfterBeanDiscovery event) {
        if (event != null) {
            final Set<? extends Entry<? extends String, ? extends Properties>> masterPropertiesEntries =
                this.masterProperties.entrySet();
            if (masterPropertiesEntries != null && !masterPropertiesEntries.isEmpty()) {
                for (final Entry<? extends String, ? extends Properties> entry : masterPropertiesEntries) {
                    if (entry != null) {
                        event.<ConsulClient>addBean()
                            .addQualifier(NamedLiteral.of(entry.getKey())) // ...and Default and Any?
                            .addTransitiveTypeClosure(ConsulClient.class)
                            .beanClass(ConsulClient.class)
                            .scope(ApplicationScoped.class)
                            .createWith(cc -> createConsulClient(cc, entry.getValue()));
                    }
                }
            }
        }
        this.masterProperties.clear();
    }

    private static ConsulClient createConsulClient(final CreationalContext<ConsulClient> cc,
                                                   final Properties properties) {
        final String host;
        final int port;
        if (properties == null) {
            host = "localhost";
            port = 8500;
        } else {
            host = properties.getProperty("agentHost", "localhost");
            port = Integer.parseInt(properties.getProperty("agentPort", "8500"));
        }
        final ConsulClient returnValue = new ConsulClient(host, port, null);
        return returnValue;
    }

}
