/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.integrations.oci.secrets.mp.configsource;

import org.eclipse.microprofile.config.ConfigProvider;

/**
 * A main class that retrieves configuration property values using various Helidon Microprofile Config implementation
 * classes.
 *
 * @see io.helidon.integrations.oci.secrets.mp.configsource.OciSecretsMpMetaConfigProvider
 */
public final class SecretLister {

    private SecretLister() {
        super();
    }

    /**
     * Asks the Helidon MicroProfile Config implementation to retrieve a configuration property whose name is supplied
     * by the first command-line argument and prints its value, if it is found, or {@code No such configuration
     * property: <name>} if it is not.
     *
     * @param args the command-line arguments; must not be {@code null}
     *
     * @exception NullPointerException if {@code args} is {@code null}
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("(No configuration property name supplied as a command-line argument.");
        } else {
            System.out.println(ConfigProvider.getConfig().getOptionalValue(args[0], String.class).orElse("No such configuration property: " + args[0]));
        }
    }

}
