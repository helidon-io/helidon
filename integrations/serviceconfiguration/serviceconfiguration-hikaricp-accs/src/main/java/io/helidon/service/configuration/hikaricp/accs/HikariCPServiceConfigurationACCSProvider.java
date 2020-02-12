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
package io.helidon.service.configuration.hikaricp.accs;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.sql.DataSource;

/**
 * A {@link
 * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider}
 * that provides {@link
 * io.helidon.service.configuration.hikaricp.HikariCPServiceConfiguration}
 * instances when running on the <a
 * href="https://docs.oracle.com/en/cloud/paas/app-container-cloud/index.html">Oracle
 * Application Container Cloud Service</a> {@linkplain
 * io.helidon.service.configuration.api.System system}.
 *
 * @see
 * io.helidon.service.configuration.hikaricp.HikariCPServiceConfiguration
 *
 * @see
 * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider
 *
 * @deprecated This class is slated for removal.
 */
@Deprecated
public class HikariCPServiceConfigurationACCSProvider
    extends io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider {


    /*
     * Constructors.
     */


    /**
     * Creates a new {@link HikariCPServiceConfigurationACCSProvider}.
     */
    public HikariCPServiceConfigurationACCSProvider() {
        super();
    }


    /*
     * Instance methods.
     */


    /**
     * Overrides the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider#appliesTo(Properties,
     * io.helidon.service.configuration.api.System, Properties)}
     * method to return {@code true} if the supplied {@link
     * io.helidon.service.configuration.api.System} {@linkplain
     * io.helidon.service.configuration.api.System#isEnabled() is
     * enabled} and has a {@linkplain
     * io.helidon.service.configuration.api.System#getName() name}
     * equal to {@code accs}, and if its {@linkplain System#getenv()
     * environment} contains at least one key starting with either
     * {@code MYSQLCS_} or {@code DBAAS_}, and if the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider#appliesTo(Properties,
     * io.helidon.service.configuration.api.System, Properties)}
     * method also returns {@code true}.
     *
     * @param properties a {@link Properties} instance that will be
     * used as the basis of the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfiguration}
     * implementation that will be returned by the {@link
     * #create(Properties,
     * io.helidon.service.configuration.api.System, Properties)}
     * method; must not be {@code null}
     *
     * @param system a {@link
     * io.helidon.service.configuration.api.System} determined to be
     * in effect; may, strictly speaking, be {@code null} but
     * ordinarily is non-{@code null} and {@linkplain
     * io.helidon.service.configuration.api.System#isEnabled()
     * enabled}
     *
     * @param coordinates a {@link Properties} instance representing
     * the meta-properties in effect; may be {@code null}
     *
     * @return {@code true} if this {@link
     * HikariCPServiceConfigurationACCSProvider} applies to the
     * configuration space implied by the supplied parameters; {@code
     * false} otherwise
     *
     * @exception NullPointerException if {@code properties} is {@code null}
     */
    @Override
    protected boolean appliesTo(final Properties properties,
                                final io.helidon.service.configuration.api.System system,
                                final Properties coordinates) {
        Objects.requireNonNull(properties);
        boolean returnValue = false;
        if (system != null && system.isEnabled() && "accs".equalsIgnoreCase(system.getName())) {
            final Map<? extends String, ? extends String> env = system.getenv();
            if (env != null && !env.isEmpty()) {
                final Set<? extends String> keys = env.keySet();
                if (keys != null && !keys.isEmpty()) {
                    boolean dbaas = false;
                    boolean mysqlcs = false;
                    for (final String key : keys) {
                        if (key != null) {
                            if (dbaas) {
                                if (mysqlcs || key.startsWith("MYSQLCS_")) {
                                    mysqlcs = true;
                                    break;
                                }
                            } else if (mysqlcs) {
                                if (key.startsWith("DBAAS_")) {
                                    dbaas = true;
                                    break;
                                }
                            } else if (key.startsWith("DBAAS_")) {
                                dbaas = true;
                            } else if (key.startsWith("MYSQLCS_")) {
                                mysqlcs = true;
                            }
                        }
                    }
                    if (dbaas) {
                        if (mysqlcs) {
                            // We deliberately get out of the business of trying to
                            // pick among competing service bindings.
                            returnValue = false;
                        } else {
                            returnValue = super.appliesTo(properties, system, coordinates);
                        }
                    } else if (mysqlcs) {
                        returnValue = super.appliesTo(properties, system, coordinates);
                    } else {
                        returnValue = false;
                    }
                }
            }
        }
        return returnValue;
    }

    /**
     * Overrides the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider#installDataSourceProperties(Properties,
     * io.helidon.service.configuration.api.System, Properties,
     * String)} to install data source-related properties discovered
     * in the <a
     * href="https://docs.oracle.com/en/cloud/paas/app-container-cloud/csjse/exploring-application-deployments-page.html#GUID-843F7013-B6FA-45E0-A9D3-29A0EFD53E11">Oracle
     * Application Container Cloud Service environment</a>.
     *
     * <p>While reading the documentation for this method, note that
     * the Oracle Application Container Cloud Service sets up
     * automatic service bindings for only Oracle or MySQL
     * databases.</p>
     *
     * <p>This method:</p>
     *
     * <ol>
     *
     * <li>Calls the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider#installDataSourceProperties(Properties,
     * io.helidon.service.configuration.api.System, Properties,
     * String)} method.</li>
     *
     * <li>Checks to see if a property named {@code
     * javax.sql.DataSource.}<em>{@code dataSourceName}</em>{@code
     * .explicitlyConfigured} has a {@link String} value equal to
     * anything other than {@code true}, including {@code null}.</li>
     *
     * <li>If so, <strong>and only if {@code dataSourceName} is {@code
     * null}</strong>, then it {@linkplain
     * Properties#setProperty(String, String) sets} certain properties
     * on {@code target}.</li>
     *
     * <li>Specifically, if there is a MySQL service binding and not
     * an Oracle database service binding, the following properties
     * will be set:
     *
     * <ol>
     *
     * <li>{@code javax.sql.DataSource.dataSourceClassName =
     * com.mysql.cj.jdbc.MysqlDataSource}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.url =
     * jdbc:mysql://${MYSQLCS_CONNECT_STRING}}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.description =
     * Autodiscovered}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.user =
     * ${MYSQLCS_USER_NAME}}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.password =
     * ${MYSQLCS_PASSWORD}}</li>
     *
     * </ol></li>
     *
     * <li>If instead there is an Oracle database service binding, but
     * not also a MySQL service binding, the following properties will
     * be set:
     *
     * <ol>
     *
     * <li>{@code javax.sql.DataSource.dataSourceClassName =
     * oracle.jdbc.pool.OracleDataSource}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.url =
     * jdbc:oracle:thin:@//${DBAAS_DEFAULT_CONNECT_DESCRIPTOR}}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.description =
     * Autodiscovered}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.user =
     * ${DBAAS_USER}}</li>
     *
     * <li>{@code javax.sql.DataSource.dataSource.password =
     * ${DBAAS_PASSWORD}}</li>
     *
     * </ol></li>
     *
     * </ol>
     *
     * @param target a {@link Properties} instance that will be used
     * as the basis of the {@link
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfiguration}
     * implementation that will be returned by the {@link
     * #create(Properties,
     * io.helidon.service.configuration.api.System, Properties)}
     * method and into which properties may be installed; must not be
     * {@code null}
     *
     * @param system a {@link
     * io.helidon.service.configuration.api.System} determined to be
     * in effect; may, strictly speaking, be {@code null} but
     * ordinarily is non-{@code null} and {@linkplain
     * io.helidon.service.configuration.api.System#isEnabled()
     * enabled}
     *
     * @param coordinates a {@link Properties} instance representing
     * the meta-properties in effect; may be {@code null}
     *
     * @param dataSourceName the data source name in question; may be
     * {@code null}
     *
     * @exception NullPointerException if {@code target} is {@code
     * null}
     *
     * @see
     * io.helidon.service.configuration.hikaricp.HikariCPServiceConfigurationProvider#installDataSourceProperties(Properties,
     * io.helidon.service.configuration.api.System, Properties,
     * String)
     */
    @Override
    protected void installDataSourceProperties(final Properties target,
                                               final io.helidon.service.configuration.api.System system,
                                               final Properties coordinates,
                                               String dataSourceName) {
        Objects.requireNonNull(target);
        super.installDataSourceProperties(target, system, coordinates, dataSourceName);

        if (!"true".equalsIgnoreCase(this.getDataSourceProperty(target,
                                                                system,
                                                                coordinates,
                                                                dataSourceName,
                                                                "explicitlyConfigured"))) {
            final String prefix = this.getPrefix();
            assert prefix != null;
            assert !prefix.isEmpty();

            // For future reference, see also:
            // https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-reference-configuration-properties.html
            // and
            // https://docs.oracle.com/database/121/JAJDB/oracle/jdbc/pool/OracleDataSource.html.

            final String dataSourceClassName;
            final String description;
            final String url;
            final String urlValue = getJdbcUrl(system, dataSourceName);
            final String user;
            final String password;
            if (dataSourceName == null) {
                dataSourceClassName = prefix + ".dataSourceClassName";
                description = prefix + ".dataSource.description";
                url = prefix + ".dataSource.url";
                user = prefix + ".dataSource.user";
                password = prefix + ".dataSource.password";
            } else {
                dataSourceName = dataSourceName.trim();
                if (dataSourceName.isEmpty()) {
                    dataSourceClassName = prefix + ".dataSourceClassName";
                    description = prefix + ".dataSource.description";
                    url = prefix + ".dataSource.url";
                    user = prefix + ".dataSource.user";
                    password = prefix + ".dataSource.password";
                } else {
                    dataSourceClassName = prefix + "." + dataSourceName + ".dataSourceClassName";
                    description = prefix + "." + dataSourceName + ".dataSource.description";
                    url = prefix + "." + dataSourceName + ".dataSource.url";
                    user = prefix + "." + dataSourceName + ".dataSource.user";
                    password = prefix + "." + dataSourceName + ".dataSource.password";
                }
            }

            target.setProperty(dataSourceClassName, getDataSourceClassName(urlValue));
            target.setProperty(url, urlValue);
            target.setProperty(description, "Autodiscovered");
            target.setProperty(user, getUser(system, dataSourceName));
            target.setProperty(password, getPassword(system, dataSourceName));
        }
    }

    /**
     * Returns the name of a Java class that implements the {@link
     * DataSource} interface that is appropriate for the supplied JDBC
     * URL.
     *
     * <p>This method may return {@code null}.</p>
     *
     * <p>Overrides of this method may return {@code null}.</p>
     *
     * <p>Overrides of this method must return the same value for the
     * same input.</p>
     *
     * <p>This implementation returns {@code
     * com.mysql.cj.jdbc.MysqlDataSource} if the supplied {@code
     * jdbcUrl} starts with {@code jdbc:mysql:}, and returns {@code
     * oracle.jdbc.pool.OracleDataSource} if the supplied {@code
     * jdbcUrl} starts with {@code jdbc:oracle:}, and {@code null} in
     * all other cases.</p>
     *
     * @param jdbcUrl a JDBC URL in {@link String} form; must not be
     * {@code null}
     *
     * @return a {@link DataSource} implementation class name, or {@code
     * null}
     *
     * @exception NullPointerException if {@code jdbcUrl} is {@code
     * null}
     */
    protected String getDataSourceClassName(final String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl);
        String returnValue = null;
        if (jdbcUrl.startsWith("jdbc:") && jdbcUrl.length() > "jdbc:".length()) {
            final int colonIndex = jdbcUrl.indexOf(':', "jdbc:".length());
            if (colonIndex > 0) {
                final String type = jdbcUrl.substring("jdbc:".length(), colonIndex);
                assert type != null;
                assert !type.isEmpty();
                switch (type) {
                case "mysql":
                    returnValue = "com.mysql.cj.jdbc.MysqlDataSource";
                    break;
                case "oracle":
                    returnValue = "oracle.jdbc.pool.OracleDataSource";
                    break;
                default:
                    break;
                }
            }
        }
        return returnValue;
    }

    private static String getUser(final io.helidon.service.configuration.api.System system,
                                  final String suppliedDataSourceName) {
        String returnValue = null;
        if (system != null) {

            final Map<?, ? extends String> env = system.getenv();
            assert env != null;

            String dataSourceName = null;
            if (suppliedDataSourceName != null) {
                dataSourceName = suppliedDataSourceName.trim().toUpperCase();
                if (dataSourceName.isEmpty()) {
                    dataSourceName = null;
                }
            }

            if (dataSourceName == null) {
                if (env.containsKey("DBAAS_USER")) {
                    if (!env.containsKey("MYSQLCS_USER_NAME")) {
                        returnValue = env.get("DBAAS_USER");
                    }
                } else if (env.containsKey("MYSQLCS_USER_NAME")) {
                    returnValue = env.get("MYSQLCS_USER_NAME");
                }
            } else if (env.containsKey("DBAAS_" + dataSourceName + "_USER")) {
                if (!env.containsKey("MYSQLCS_" + dataSourceName + "_USER_NAME")) {
                    returnValue = env.get("DBAAS_" + dataSourceName + "_USER");
                }
            } else if (env.containsKey("MYSQLCS_ " + dataSourceName + "_USER_NAME")) {
                returnValue = env.get("MYSQLCS_" + dataSourceName + "_USER_NAME");
            }
        }
        return returnValue;
    }

    private static String getPassword(final io.helidon.service.configuration.api.System system,
                                      final String suppliedDataSourceName) {
        String returnValue = null;
        if (system != null) {

            final Map<?, ? extends String> env = system.getenv();
            assert env != null;

            String dataSourceName = null;
            if (suppliedDataSourceName != null) {
                dataSourceName = suppliedDataSourceName.trim().toUpperCase();
                if (dataSourceName.isEmpty()) {
                    dataSourceName = null;
                }
            }

            if (dataSourceName == null) {
                if (env.containsKey("DBAAS_PASSWORD")) {
                    if (!env.containsKey("MYSQLCS_USER_PASSWORD")) {
                        returnValue = env.get("DBAAS_PASSWORD");
                    }
                } else if (env.containsKey("MYSQLCS_USER_PASSWORD")) {
                    returnValue = env.get("MYSQLCS_USER_PASSWORD");
                }
            } else if (env.containsKey("DBAAS_" + dataSourceName + "_PASSWORD")) {
                if (!env.containsKey("MYSQLCS_" + dataSourceName + "_USER_PASSWORD")) {
                    returnValue = env.get("DBAAS_" + dataSourceName + "_PASSWORD");
                }
            } else if (env.containsKey("MYSQLCS_ " + dataSourceName + "_USER_PASSWORD")) {
                returnValue = env.get("MYSQLCS_" + dataSourceName + "_USER_PASSWORD");
            }
        }
        return returnValue;
    }

    private static String getJdbcUrl(final io.helidon.service.configuration.api.System system,
                                     final String suppliedDataSourceName) {
        String returnValue = null;
        if (system != null) {

            final Map<?, ? extends String> env = system.getenv();
            assert env != null;

            String dataSourceName = null;
            if (suppliedDataSourceName != null) {
                dataSourceName = suppliedDataSourceName.trim().toUpperCase();
                if (dataSourceName.isEmpty()) {
                    dataSourceName = null;
                }
            }

            if (dataSourceName == null) {
                if (env.containsKey("DBAAS_DEFAULT_CONNECT_DESCRIPTOR")) {
                    if (!env.containsKey("MYSQLCS_CONNECT_STRING")) {
                        returnValue = "jdbc:oracle:thin:@//" + env.get("DBAAS_DEFAULT_CONNECT_DESCRIPTOR");
                    }
                } else if (env.containsKey("MYSQLCS_CONNECT_STRING")) {
                    returnValue = "jdbc:mysql://" + env.get("MYSQLCS_CONNECT_STRING");
                }
            } else if (env.containsKey("DBAAS_" + dataSourceName + "_CONNECT_DESCRIPTOR")) {
                if (!env.containsKey("MYSQLCS_" + dataSourceName + "_URL")) {
                    returnValue = "jdbc:oracle:thin:@//" + env.get("DBAAS_" + dataSourceName + "_CONNECT_DESCRIPTOR");
                }
            } else if (env.containsKey("MYSQLCS_ " + dataSourceName + "_CONNECT_STRING")) {
                returnValue = "jdbc:mysql://" + env.get("MYSQLCS_" + dataSourceName + "_CONNECT_STRING");
            }

        }
        return returnValue;
    }

}
