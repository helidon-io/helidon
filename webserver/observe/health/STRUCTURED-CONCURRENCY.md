# Structured health checks on JDK 28 EA

This experiment requires OpenJDK 28 EA build 21 or later with the permanent
`StructuredTaskScope` API. No `--enable-preview` option is needed. CI and the
application Dockerfiles pin build 21 and verify its official SHA-256 checksums.

An aggregate health request opens a scope named `health-checks` and starts one
virtual thread per check. Thread names include the registration index and check
name, for example `health-check-0[database]`. Responses retain registration order,
including duplicate names. A check exception becomes its usual `ERROR` result;
the remaining checks still complete. Interrupting the request cancels the scope,
waits for its children to exit, and restores the request thread's interrupt flag.

Independent blocking checks can finish in roughly the duration of the slowest
check, rather than the sum of their durations. Checks must not rely on executing
on the request thread or on another check finishing first. Scoped values,
including Helidon's scoped context, are inherited; ordinary thread locals are
not propagated. The experiment adds no deadline or concurrency limit. A check
that ignores interruption can delay scope cleanup.

To capture a real dump with two checks in progress, first build the repository
with JDK 28, then run from the repository root using that JDK:

```shell
mvn -pl :helidon-webserver-observe-health test-compile exec:java \
    -Dexec.classpathScope=test \
    -Dexec.mainClass=io.helidon.webserver.observe.health.StructuredHealthThreadDumpDemo \
    -Dexec.args="--dump-file /absolute/path/health-threads.json"
```

The output path must be absolute and initially absent. The demo starts an HTTP
health request, waits until both `database` and `cache` checks are blocked on a
test gate, writes a JSON thread dump through the JDK diagnostic MXBean, releases
both checks, and prints the successful response. These are demonstration checks;
they do not connect to a database or cache.

The JSON dump records both child threads in the named scope container and links
that container to the request thread through its `owner` field:

```text
WebServer socket (request thread)
  StructuredTaskScopeImpl.join
  HealthHandler.evaluateChecks
  health-checks (scope container, owner = request thread)
    health-check-0[database] -> CountDownLatch.await
    health-check-1[cache]    -> CountDownLatch.await
```

For an external capture, start the demo with an initially absent release-marker
path instead of `--dump-file`. After it prints `READY PID=...`, run
`jcmd <pid> Thread.dump_to_file -format=json /absolute/path/health-threads.json`,
then create the marker file. A conventional platform-thread dump does not expose
this virtual-thread hierarchy. The demo has a watchdog and always releases its
gates during cleanup.
