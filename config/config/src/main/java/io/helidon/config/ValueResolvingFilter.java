/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.config;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.config.spi.ConfigFilter;

/**
 * A config filter that replaces all placeholders in a config value String with
 * their corresponding values looked up from the config.
 * <p>
 * For example:
 * <pre>
 * {@code message = "Hello ${name}!"
 * name = "Joachim"}</pre>
 * will be resolved as:
 * <pre>
 * {@code message = "Hello Joachim!"}</pre>
 * <h1>How to Activate This Filter</h1>
 * Use any of the following techniques to create a {@code ValueResolvingFilter} and
 * use it for config look-ups.
 * <ol>
 * <li>Programmatically:
 * <ol type="a">
 * <li>Invoke {@link io.helidon.config.ConfigFilters#valueResolving()}
 * to get a builder for the filter.</li>
 * <li>Optionally, invoke the filter builder's {@code failOnMissingReference} method
 * (see below).
 * <li>Invoke the builder's {@code build} method to create
 * the filter.</li>
 * <li>Then, on the {@code Config.Builder} being used to construct the
 * {@code Config} instance, invoke {@code Config.Builder#addFilter} passing the
 * just-created filter.</li>
 * </ol>
 * <li>Implicitly: Create or edit the file
 * {@code io.helidon.config.ConfigFilter} on the application's runtime classpath
 * to contain this line:
 * <pre>
 * {@code io.helidon.config.ValueResolvingFilter}</pre>
 * The config system will then use the Java service loader mechanism to create and add this filter to
 * every {@code Config.Builder} automatically.
 * </li>
 * </ol>
 * <h2>Handling Missing References</h2>
 * By default, references to tokens that are not present <em>do not</em> cause
 * retrievals to fail. You can customize this behavior in several ways.
 * <ol>
 * <li>If you use {@link ConfigFilters#valueResolving()} to get a builder for the
 * filter, invoke the {@code failOnMissingReference} method on that builder before
 * invoking the filter builder's {@code build} method.</li>
 * <li>If you use {@link ConfigFilters.ValueResolvingBuilder#create} to get the
 * filter's builder, define this setting in the {@code Config} instance you pass
 * to the {@code from} method:
 * <pre>
 * {@value ConfigFilters.ValueResolvingBuilder#FAIL_ON_MISSING_REFERENCE_KEY_NAME} = true</pre>
 * or {@code false} which is the default. This sets the behavior for the single
 * filter created from that builder.
 * </li>
 * <li>If you use the Java service loader mechanism to create
 * {@code ValueResolvingFilter}s for every {@code Config.Builder}, specify the
 * following config setting in one of the config sources that composes the
 * default config:
 * <pre>
 * {@value ConfigFilters.ValueResolvingBuilder#FAIL_ON_MISSING_REFERENCE_KEY_NAME} = true</pre>
 * or {@code false} which is the default. This sets the behavior for <em>every</em>
 * {@code ValueResolvingFilter} created for which the {@code failOnMissingReference}
 * value has not already been set, for example by invoking the
 * {@code ConfigFilters.valueResolving().failOnMissingReference()} method.
 * </li>
 * </ol>
 */
public class ValueResolvingFilter implements ConfigFilter {

    private static final Logger LOGGER = Logger.getLogger(ValueResolvingFilter.class.getName());

    private static final boolean DEFAULT_FAIL_ON_MISSING_REFERENCE_BEHAVIOR = false;

    // for references resolving
    // matches string between ${ } with a negative lookbehind if there is not backslash
    private static final String REGEX_REFERENCE = "(?<!\\\\)\\$\\{([^}]+)\\}";
    private static final Pattern PATTERN_REFERENCE = Pattern.compile(REGEX_REFERENCE);

    static final String MISSING_REFERENCE_ERROR =
            "A value of the key '%s' references to the missing key, see stacktrace.";

    // for encoding backslashes
    // matches a backslash with a positive lookahead if it is the backslash that encodes ${}
    private static final String REGEX_BACKSLASH = "\\\\(?=\\$\\{([^}]+)\\})";
    private static final Pattern PATTERN_BACKSLASH = Pattern.compile(REGEX_BACKSLASH);

    // I only care about unresolved key happening within the same thread
    private static final ThreadLocal<Set<Config.Key>> UNRESOLVED_KEYS = ThreadLocal.withInitial(HashSet::new);

    private Config root;
    private Optional<Boolean> failOnMissingReferenceSetting = Optional.empty();
    private boolean failOnMissingReference = false;

    /**
     * Creates an instance of filter with the specified behavior on missing
     * references.
     *
     * @param failOnMissingReference whether to fail when a referenced key is missing
     */
    public ValueResolvingFilter(boolean failOnMissingReference) {
        this.failOnMissingReferenceSetting = Optional.of(failOnMissingReference);
    }

    /**
     * Creates an instance of the filter with no explicit failOnMissing behavior set.
     */
    public ValueResolvingFilter() {
    }

    @Override
    public void init(Config config) {
        root = config;
        /*
         * If failOnMissingReferenceSetting has not already been explicitly set
         * by the constructor, try to get the setting from the configuration. In
         * either case save the result in a simple boolean for efficiency in
         * #apply.
         */
        if (failOnMissingReferenceSetting.isEmpty()) {
            failOnMissingReferenceSetting = Optional.of(
                    config
                        .get(ConfigFilters.ValueResolvingBuilder.FAIL_ON_MISSING_REFERENCE_KEY_NAME)
                        .asBoolean()
                        .orElse(DEFAULT_FAIL_ON_MISSING_REFERENCE_BEHAVIOR));
        }
        failOnMissingReference = failOnMissingReferenceSetting.get();
    }

    @Override
    public String apply(Config.Key key, String stringValue) {
        if (!UNRESOLVED_KEYS.get().add(key)) {
            UNRESOLVED_KEYS.get().clear();
            throw new IllegalStateException("Recursive update");
        }
        try {
            return format(stringValue);
        } catch (MissingValueException e) {
            if (failOnMissingReference) {
                throw new ConfigException(String.format(MISSING_REFERENCE_ERROR, key.name()), e);
            } else {
                LOGGER.log(Level.FINER, e, () -> String.format(MISSING_REFERENCE_ERROR, key.name()));
                return stringValue;
            }
        } finally {
            UNRESOLVED_KEYS.get().remove(key);
        }
    }

    private String format(String template) {
        Matcher m = PATTERN_REFERENCE.matcher(template);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(root.get(m.group(1)).asString().get()));
        }
        m.appendTail(sb);
        // remove all backslash that encodes ${...}
        m = PATTERN_BACKSLASH.matcher(sb.toString());

        return m.replaceAll("");
    }
}
