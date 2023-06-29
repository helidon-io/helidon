/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.observe.log;

import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.config.Config;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.media.type.MediaType;
import io.helidon.nima.http.media.EntityReader;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.jsonp.JsonpSupport;
import io.helidon.nima.webserver.http.HttpRules;
import io.helidon.nima.webserver.http.HttpService;
import io.helidon.nima.webserver.http.SecureHandler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;
import io.helidon.nima.webserver.http1.Http1LoggingConnectionListener;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

class LogService implements HttpService {
    private static final EntityWriter<JsonObject> WRITER = JsonpSupport.serverResponseWriter();
    private static final EntityReader<JsonObject> READER = JsonpSupport.serverRequestReader();
    private static final JsonBuilderFactory JSON = Json.createBuilderFactory(Map.of());
    private static final String DEFAULT_IDLE_STRING = "%\n";

    private final LogManager logManager;
    private final Logger root;
    private final Map<Object, Consumer<String>> listeners = Collections.synchronizedMap(new IdentityHashMap<>());
    private final AtomicBoolean logHandlingInitialized = new AtomicBoolean();
    private final boolean permitAll;
    private final boolean logStreamEnabled;
    private final Http.HeaderValue logStreamMediaTypeHeader;
    private final long logStreamSleepSeconds;
    private final int logStreamQueueSize;
    private final String logStreamIdleString;
    private final Charset logStreamCharset;

    private LogService(Builder builder) {
        this.logManager = LogManager.getLogManager();
        this.root = Logger.getLogger("");

        this.permitAll = builder.permitAll;
        this.logStreamEnabled = builder.logStreamEnabled;
        this.logStreamMediaTypeHeader = builder.logStreamMediaTypeHeader;
        this.logStreamSleepSeconds = builder.logStreamSleepSeconds;
        this.logStreamQueueSize = builder.logStreamQueueSize;
        this.logStreamIdleString = builder.logStreamIdleString;
        this.logStreamCharset = builder.logStreamCharset;
    }

    static HttpService create(Config config) {
        return builder().config(config).build();
    }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public void routing(HttpRules rules) {
        if (!permitAll) {
            rules.any(SecureHandler.authorize("nima-observe"));
        }

        rules.get("/loggers")
                .get("/loggers", this::allLoggersHandler)
                .get("/loggers", this::allLoggersHandler)
                .get("/loggers/{logger}", this::loggerHandler)
                .post("/loggers/{logger}", this::setLevelHandler)
                .delete("/loggers/{logger}", this::unsetLoggerHandler);

        if (logStreamEnabled) {
            rules.get("/", this::logHandler);
        }
    }

    @Override
    public void afterStop() {
        listeners.clear();
    }

    private void logHandler(ServerRequest req, ServerResponse res) throws Exception {
        initializeLogHandling();
        res.header(logStreamMediaTypeHeader);
        // do not cache more than x lines, to prevent OOM
        var q = new ArrayBlockingQueue<String>(logStreamQueueSize);

        // we do not care if the offer fails, it means the consumer is slow and will miss some lines
        listeners.put(req, q::offer);

        try (OutputStreamWriter out = new OutputStreamWriter(res.outputStream(), logStreamCharset)) {

            while (true) {
                try {
                    String poll = q.poll(logStreamSleepSeconds, TimeUnit.SECONDS);
                    if (poll == null) {
                        // check if we are still alive
                        out.write(logStreamIdleString);
                        out.flush();
                    } else {
                        out.write(poll);
                    }
                } catch (InterruptedException e) {
                    // interrupted - we should finish
                    return;
                }
            }
        } finally {
            listeners.remove(req);
        }
    }

    private void initializeLogHandling() {
        if (logHandlingInitialized.compareAndSet(false, true)) {
            for (java.util.logging.Handler handler : root.getHandlers()) {
                handler.setFilter(new LogMessageFilter(handler.getFormatter(), handler.getFilter(), listeners));
                break;
            }
        }
    }

    private void unsetLoggerHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        Logger.getLogger(logger).setLevel(null);
        res.status(Http.Status.NO_CONTENT_204).send();
    }

    private void setLevelHandler(ServerRequest req, ServerResponse res) {
        System.out.println("Here");
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObject requestJson = READER.read(JsonpSupport.JSON_OBJECT_TYPE,
                                             req.content().inputStream(),
                                             req.headers());

        Level desiredLevel = Level.parse(requestJson.getString("level"));
        Logger.getLogger(logger)
                .setLevel(desiredLevel);

        res.status(Http.Status.NO_CONTENT_204).send();
    }

    private void loggerHandler(ServerRequest req, ServerResponse res) {
        String logger = req.path().pathParameters().first("logger").orElse("");
        JsonObjectBuilder rootObject = JSON.createObjectBuilder();
        logger(rootObject, logger);

        write(req, res, rootObject.build());
    }

    private void allLoggersHandler(ServerRequest req, ServerResponse res) {
        JsonObjectBuilder rootObject = JSON.createObjectBuilder();

        levels(rootObject);
        loggers(rootObject);

        write(req, res, rootObject.build());
    }

    private void loggers(JsonObjectBuilder rootObject) {
        JsonObjectBuilder loggersJson = JSON.createObjectBuilder();

        Enumeration<String> loggerNames = logManager.getLoggerNames();
        while (loggerNames.hasMoreElements()) {
            logger(loggersJson, loggerNames.nextElement());
        }

        rootObject.add("loggers", loggersJson);
    }

    private void logger(JsonObjectBuilder parentJson, String loggerName) {
        Logger logger = Logger.getLogger(loggerName);

        Level configuredLevel = logger.getLevel();
        Level effectiveLevel = effectiveLevel(logger);

        JsonObjectBuilder loggerJson = JSON.createObjectBuilder();
        if (configuredLevel != null) {
            loggerJson.add("configuredLevel", configuredLevel.getName());
        }
        loggerJson.add("level", effectiveLevel.getName());

        parentJson.add("".equals(loggerName) ? "ROOT" : loggerName, loggerJson);
    }

    private void levels(JsonObjectBuilder rootObject) {
        JsonArrayBuilder levels = JSON.createArrayBuilder();
        levels.add(Level.OFF.getName())
                .add(Level.SEVERE.getName())
                .add(Level.WARNING.getName())
                .add(Level.INFO.getName())
                .add(Level.FINE.getName())
                .add(Level.FINER.getName())
                .add(Level.FINEST.getName());
        rootObject.add("levels", levels);
    }

    private Level effectiveLevel(Logger logger) {
        Level level = logger.getLevel();
        if (level == null) {
            if (logger == root) {
                // we did not get a log level for parent, just assume the default
                return Level.INFO;
            }

            Logger parent = logger.getParent();
            if (parent == null) {
                return effectiveLevel(root);
            }

            return effectiveLevel(parent);
        }
        return level;
    }

    private void write(ServerRequest req, ServerResponse res, JsonObject json) {
        WRITER.write(JsonpSupport.JSON_OBJECT_TYPE,
                     json,
                     res.outputStream(),
                     req.headers(),
                     res.headers());
    }

    static class Builder implements io.helidon.common.Builder<Builder, LogService> {
        private boolean permitAll = false;
        private boolean logStreamEnabled = true;
        private Http.HeaderValue logStreamMediaTypeHeader = Http.Header.create(Http.Header.CONTENT_TYPE,
                                                                               HttpMediaType.PLAINTEXT_UTF_8.text());
        private Charset logStreamCharset = StandardCharsets.UTF_8;
        private long logStreamSleepSeconds = 5L;
        private int logStreamQueueSize = 100;
        private String logStreamIdleString = DEFAULT_IDLE_STRING;


        private Builder() {
        }

        @Override
        public LogService build() {
            return new LogService(this);
        }

        Builder config(Config config) {
            config.get("permit-all").asBoolean().ifPresent(this::permitAll);

            Config logStreamConfig = config.get("stream");
            logStreamConfig.get("enabled").asBoolean().ifPresent(this::logStreamEnabled);
            logStreamConfig.get("content-type")
                    .asString()
                    .as(HttpMediaType::create)
                    .ifPresent(this::logStreamMediaType);
            logStreamConfig.get("sleep-seconds").asLong().ifPresent(this::logStreamSleepSeconds);
            logStreamConfig.get("queue-size").asInt().ifPresent(this::logStreamQueueSize);
            logStreamConfig.get("idle-string").asString().ifPresent(this::logStreamIdleString);

            return this;
        }

        Builder permitAll(boolean permitAll) {
            this.permitAll = permitAll;
            return this;
        }

        Builder logStreamEnabled(boolean logStreamAllow) {
            this.logStreamEnabled = logStreamAllow;
            return this;
        }

        Builder logStreamMediaType(HttpMediaType logStreamMediaType) {
            this.logStreamMediaTypeHeader = Http.Header.createCached(Http.Header.CONTENT_TYPE, logStreamMediaType.text());
            this.logStreamCharset = logStreamMediaType.charset().map(Charset::forName).orElse(StandardCharsets.UTF_8);
            return this;
        }

        Builder logStreamMediaType(MediaType logStreamMediaType) {
            return logStreamMediaType(HttpMediaType.create(logStreamMediaType));
        }

        Builder logStreamSleepSeconds(long logStreamSleepSeconds) {
            this.logStreamSleepSeconds = logStreamSleepSeconds;
            return this;
        }

        Builder logStreamQueueSize(int logStreamQueueSize) {
            this.logStreamQueueSize = logStreamQueueSize;
            return this;
        }

        Builder logStreamIdleString(String string) {
            this.logStreamIdleString = string;
            return this;
        }
    }

    private static class LogMessageFilter implements Filter {
        private final Formatter formatter;
        private final Filter filter;
        private final Map<Object, Consumer<String>> listeners;
        private final Set<LoggerAndLevel> excludedLoggers;

        LogMessageFilter(Formatter formatter, Filter filter, Map<Object, Consumer<String>> listeners) {
            this.formatter = formatter;
            this.filter = filter;
            this.listeners = listeners;

            excludedLoggers = Set.of(new LoggerAndLevel(Http1LoggingConnectionListener.class.getName() + ".send", Level.FINER),
                                     new LoggerAndLevel(Http1LoggingConnectionListener.class.getName() + ".send", Level.FINE));
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            boolean result = filter == null || filter.isLoggable(record);

            if (result) {
                // now we have a problem that when tracing of HTTP is enabled, we create an infinite loop
                // so we will not send data related to network traffic itself over to listeners
                if (!excludedLoggers.contains(new LoggerAndLevel(record.getLoggerName(), record.getLevel()))) {
                    fire(formatter.format(record));
                }
            }

            return result;
        }

        private void fire(String message) {
            listeners.values().forEach(it -> {
                try {
                    it.accept(message);
                } catch (Exception ignored) {
                    // ignore exception, we cannot stop printing to stdout
                }
            });
        }
    }

    private record LoggerAndLevel(String logger, Level level) {
    }
}
