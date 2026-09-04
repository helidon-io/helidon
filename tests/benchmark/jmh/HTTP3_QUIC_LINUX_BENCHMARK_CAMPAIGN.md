# HTTP/3 and QUIC Linux Benchmark Campaign

This document is a self-contained procedure for collecting the first
authoritative Linux performance baseline for Helidon's HTTP/3 client, HTTP/3
server, and standalone QUIC implementation. It does not depend on external task
records or unstated project history.

The campaign measures the final implementation as one change. It does not
measure commits independently and does not use power-managed laptop results as
a baseline. Laptop measurements are diagnostics only; they are not comparable
with the Linux results and must not appear in regression percentages.

## Execution contract

Run this campaign on a dedicated bare-metal Linux host. Do not run it in Docker,
a virtual machine, an emulator, or a compatibility layer. Do not run Docker,
other builds, reviewer agents, IDE indexing, backups, system updates, or other
CPU-, memory-, or I/O-intensive work concurrently.

Do not change production, test, benchmark, build, or documentation sources while
collecting evidence. Do not rebase, merge, switch branches, update dependencies,
or regenerate source files outside normal Maven output directories. If a
benchmark or harness defect is discovered, stop the affected campaign, preserve
the evidence, and report the defect. Fixing it is a separate task requiring a
new source identity and a new campaign directory.

Run one benchmark process at a time. Do not select a whole JMH suite. Always use
the named runner and an exact include expression where the runner accepts one.
Do not automatically rerun a disappointing result. Preserve it first, identify
the reason for a rerun, and use a new run ID.

The production-code checkpoint immediately before the original handoff was
`63975fbf38e7e10cff209112cd56d4f44fcab38b`. It is a lineage anchor, not the
source identity to benchmark. Final implementation fixes or benchmark-harness
corrections after that checkpoint require a new clean selected commit and a new
campaign directory.

The controlled source and build manifests generated on the Linux host are the
authoritative identity. Before starting, designate the exact clean final-source
commit and record both its HEAD and tree IDs. Verify that it is a descendant of
the checkpoint. Every command and result in one campaign must retain those
selected IDs. If HEAD, tree, or worktree status changes, stop and start a new
campaign directory; do not mix results from the two source identities.

## Required outcome

The campaign must produce:

1. a clean, source-bound controlled evidence root;
2. authoritative QUIC lifecycle, HTTP/3 small-write, and adverse-network
   evidence;
3. a complete server-admission and cap-lifecycle characterization;
4. packet-protection, ACK-decode, and endpoint-ingress evidence;
5. every maintained supporting hot-path matrix that has a dedicated safe
   runner, plus an explicit record of uncovered benchmark classes;
6. an operator report containing host controls, exact commands, result
   summaries, anomalies, and limitations; and
7. one archive and SHA-256 digest covering the complete campaign directory.

This is the first authoritative Linux baseline. Unless exact controlled
evidence with matching source, environment stratum, harness, and settings is
supplied separately, the campaign establishes future comparison data; it does
not claim an old/new percentage for the feature itself.

Do not commit raw benchmark results to the source repository unless explicitly
asked. Store them in a durable campaign directory outside the checkout and every
Maven `target` directory.

## Campaign layout

Choose a new absolute path on durable local storage. The path must not contain
whitespace, must not be a symbolic link, and must not be under the source
checkout, a Maven repository, a temporary filesystem, or a `target` directory.

Use this layout:

```text
__CAMPAIGN_DIR__/
    controlled/
    operator/
        environment-before/
        environment-after/
    supplemental/
```

Replace every `__...__` token in this document before running a command. Never
execute a command containing an unresolved token.

- `__CAMPAIGN_DIR__` is the campaign root shown above.
- `__CAMPAIGN_PARENT__` is the directory containing `__CAMPAIGN_DIR__`.
- `__CAMPAIGN_BASENAME__` is the final path component of `__CAMPAIGN_DIR__`.
- `__CONTROLLED_ROOT__` is `__CAMPAIGN_DIR__/controlled`.
- `__OPERATOR_DIR__` is `__CAMPAIGN_DIR__/operator`.
- `__SUPPLEMENTAL_DIR__` is `__CAMPAIGN_DIR__/supplemental`.
- `__ENVIRONMENT_CAPTURE_DIR__` is
  `__OPERATOR_DIR__/environment-before` during preflight and
  `__OPERATOR_DIR__/environment-after` during final verification.
- `__ENVIRONMENT_MANIFEST__` is
  `__OPERATOR_DIR__/environment-before.manifest` during preflight and
  `__OPERATOR_DIR__/environment-after.manifest` during final verification.
- `__SELECTED_HEAD__` is the exact clean final-source commit selected before
  creating the campaign directory.
- `__SELECTED_TREE__` is the tree ID of `__SELECTED_HEAD__`.
- `__SELECTED_CPU_LIST__` is the fixed Linux CPU-list expression used for the
  campaign, for example `2-5`.
- `__SELECTED_CPU__` is replaced in repeated snapshot commands by each logical
  CPU in `__SELECTED_CPU_LIST__`.
- `__CORE_CPU__` is replaced in repeated throttle commands by one selected
  logical CPU from every selected physical core.
- `__PACKAGE_CPU__` is replaced in repeated snapshot commands by one selected
  logical CPU from every physical package used by the campaign.
- `__JAVA_HOME__` is the canonical Java home reported by the PATH `java` and
  by `mvn -version`; after resolving it, the exact executable
  `"__JAVA_HOME__/bin/java"` must report the same value.
- `__LOCAL_PREFIX__` is the immutable Maven Resolver local prefix printed by
  the controlled-manifest generator.
- `__REMOTE_PREFIX__` is the remote prefix recorded by that generator.
- `__ATTEMPT_ID__` is a fresh UTC timestamp plus sequence for one command, for
  example `20260728T120000Z-a1`. Use a new value for every invocation,
  including retries.

Create `operator` and `supplemental` before starting. Leave `controlled`
nonexistent so the generator can create and own it.

```shell
mkdir -p __OPERATOR_DIR__
mkdir -p __SUPPLEMENTAL_DIR__
mkdir -p __OPERATOR_DIR__/environment-before
mkdir -p __OPERATOR_DIR__/environment-after
test ! -e __CONTROLLED_ROOT__
```

The controlled root contains three campaign identity files and six files for
each of seven designated accepted authoritative runs. A campaign in which all
seven modes succeed on their first attempt has 45 harness-owned files:

```text
http3-quic-controlled-source.manifest
http3-quic-controlled-build.manifest
http3-quic-controlled-build.log
```

Each accepted controlled run adds a JSON result, workload log,
effective-properties file, source-manifest snapshot, build-manifest snapshot,
and controlled-build-log snapshot. The JSON file is published last. A missing
JSON file or any surviving reservation marker rejects that attempt.

A retry uses a fresh run ID and does not overwrite or delete the rejected
attempt. The final inventory must designate exactly one accepted six-file
bundle for each of the seven modes, hash every additional complete or partial
attempt, and classify each additional attempt as rejected with its reason.
Reservation markers are permitted only when they belong to a named rejected
attempt. The controlled root may therefore contain more than 45 files.

Operator metadata and Maven launcher logs belong under `operator`. Results from
runners that do not enforce the controlled six-file format belong under
`supplemental`.

## Linux host requirements

Required software and capabilities:

- bare-metal Linux;
- a Java 26 JDK with JFR support;
- Maven compatible with this checkout;
- Git;
- GNU `tar` and `sha256sum`;
- working IPv4 and IPv6 loopback UDP;
- permission to create child JVMs and UDP proxy processes; and
- enough durable disk space for the controlled Maven prefix, logs, JSON, and
  final archive.

The campaign does not use Docker or Testcontainers.

Use one constant CPU and NUMA policy for every Maven, Surefire, JMH, and child
process. On a multi-node host, select a fixed set of at least four physical
cores from one NUMA node and bind memory to that node. On a single-node host,
using all otherwise-idle online CPUs is acceptable. If `numactl` or `taskset`
is used, prepend the same command and CPU set to every Maven invocation,
including the initial build and manifest generator. All child processes inherit
that affinity.

On a multi-node host, inability to prove both CPU and memory binding is a
preflight failure. `numactl` is optional only on a single-node host or when an
equivalent cpuset and memory-policy mechanism is demonstrated and recorded.
`taskset` alone proves CPU affinity but not NUMA memory binding.

Constancy and recording are mandatory; privileged tuning is optional. Do not
change governor, turbo, SMT, IRQ placement, isolated CPUs, socket buffers, or
other host settings without recording the change and applying it consistently
to the whole campaign. If any such setting changes after measurement begins,
start a new campaign.

Do not constrain the complete client/server workload to one core. The controlled
build's `ActiveProcessorCount=2` setting bounds build-tool concurrency; it is
not workload CPU affinity.

### Operator sidecar

Capture the following before the initial build and again after the final run.
Save the exact commands and full outputs under the active
`__ENVIRONMENT_CAPTURE_DIR__`. Missing optional tools such as `cpupower` or
`sensors` are not failures, but record that they were unavailable. The NUMA
rule above still applies.

```shell
uname -a
cat /etc/os-release
lscpu
numactl --hardware
cpupower -c __SELECTED_CPU_LIST__ frequency-info
sensors
cat /proc/cmdline
cat /sys/devices/system/cpu/smt/active
cat /sys/devices/system/cpu/cpu__SELECTED_CPU__/cpufreq/scaling_governor
cat /sys/devices/system/cpu/cpu__PACKAGE_CPU__/microcode/version
cat /sys/class/dmi/id/bios_vendor
cat /sys/class/dmi/id/bios_version
cat /sys/class/dmi/id/bios_date
cat /sys/class/dmi/id/board_name
cat /sys/class/dmi/id/product_name
free -h
uptime
ulimit -n
sysctl net.core.rmem_max net.core.wmem_max net.core.netdev_max_backlog
sysctl net.ipv4.udp_mem net.ipv4.udp_rmem_min net.ipv4.udp_wmem_min
"__JAVA_HOME__/bin/java" -version
mvn -version
git rev-parse HEAD
git log -1 --format=fuller
git status --short --untracked-files=all
git diff --check
git merge-base --is-ancestor 63975fbf38e7e10cff209112cd56d4f44fcab38b HEAD
git diff --name-only 63975fbf38e7e10cff209112cd56d4f44fcab38b HEAD
git --version
"__JAVA_HOME__/bin/javac" -version
"__JAVA_HOME__/bin/java" -XshowSettings:properties -version
"__JAVA_HOME__/bin/java" -XshowSettings:security -version
ldd --version
openssl version -a
```

Execute each line as a separate invocation and add
`> "__ENVIRONMENT_CAPTURE_DIR__/<sequence>-<command-stem>.txt" 2>&1` to that
invocation. Use stable sequence numbers and stems in both capture phases, and
record the fully expanded commands in
`__ENVIRONMENT_CAPTURE_DIR__/commands.txt` together with each exit status. Do
not depend on copied or possibly truncated terminal rendering.

Also record:

- CPU model, physical-core count, SMT policy, selected CPU set, and NUMA node;
- microcode, kernel, distribution, and BIOS power profile;
- governor and turbo policy;
- total memory;
- JDK vendor and complete build;
- effective JCA security properties and selected cipher providers;
- Maven version and effective local-repository path;
- installed OS package inventory and the versions of Git, libc, and OpenSSL;
- effective receive and send socket-buffer limits;
- whether the host is isolated or shared;
- ambient or package temperature when available; and
- any environmental event during a run.

### Per-invocation host snapshots

Immediately before and after every measured invocation, write a timestamped
`__OPERATOR_DIR__/<descriptive-name>-__ATTEMPT_ID__-host-before.txt` or
`-host-after.txt` sidecar. Capture at least:

```shell
date --utc --iso-8601=seconds
uptime
cat /proc/pressure/cpu
cat /proc/pressure/memory
cat /proc/pressure/io
cat /sys/devices/system/cpu/cpu__SELECTED_CPU__/cpufreq/scaling_governor
cat /sys/devices/system/cpu/cpu__CORE_CPU__/thermal_throttle/core_throttle_count
cat /sys/devices/system/cpu/cpu__PACKAGE_CPU__/thermal_throttle/package_throttle_count
cpupower -c __SELECTED_CPU_LIST__ frequency-info
sensors
ps -eo pid,comm,psr,pcpu,pmem,args --sort=-pcpu
```

Run the governor template once for every selected logical CPU, the core
throttle template once for every selected physical core, and the microcode and
package-throttle templates once for every physical package touched by the
campaign. Also record the exact affinity/NUMA prefix used by the Maven command.
Hardware without a listed thermal counter or tool may record it as unavailable.
Compare thermal-throttle counters, pressure, load, governor, and temperatures
across the pair. A changed policy, new throttling, or material concurrent load
rejects the whole run ID.

A retained low-overhead host monitor may supplement these snapshots. Record its
command, interval, CPU placement, and output, and keep it identical throughout
the campaign. Do not start a heavy profiler or monitoring agent alongside the
workload.

Ensure `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `MAVEN_OPTS`,
and `MAVEN_ARGS` are unset. `JAVA_HOME` is the one required inherited Java
variable and must equal `__JAVA_HOME__`. Do not add unrecorded JVM flags. If
Maven uses a non-default local repository, pass the same
`maven.repo.local` property to the generator and every later command.

### Software environment identity

Security updates take precedence over benchmark continuity. Never retain,
downgrade to, or boot a known-vulnerable kernel, JDK, firmware, system library,
build tool, or dependency merely to reproduce an older environment.

Treat the complete effective software and firmware stack as a measurement
stratum. At minimum, the identity includes:

- kernel release and build, kernel command line, distribution, and installed
  package inventory;
- CPU microcode, system firmware/BIOS, and power-management policy;
- JDK vendor, complete version/build, Java runtime image, security
  configuration, registered-provider order, and initialized packet-protection
  cipher providers;
- Maven and Git versions;
- host-supplied build tools and configuration; and
- the static inherited JVM environment, CPU-affinity, NUMA, socket, and host
  controls held constant across the campaign.

Source-controlled dependencies, plugins, artifacts, and fork classpaths belong
to each source's controlled build identity rather than the shared environment
stratum. The complete identity of one result is the environment-stratum ID
plus that result's source and controlled-build IDs. This separation lets
reference and candidate sources share one host environment while preserving
intentional dependency changes in the source comparison.

Profiler choice, benchmark mode and parameters, fork JVM arguments, and other
invocation-specific settings belong to the run identity, not the environment
stratum. Bind them to every result through the controlled effective-properties
file or the supplemental identity sidecar.

`JAVA_HOME` must be set to `__JAVA_HOME__`. The PATH `java`, the exact
`"__JAVA_HOME__/bin/java"` executable, and the Java home printed by
`mvn -version` must resolve to the same canonical Java 26 installation. Record
and compare:

```shell
command -v java
readlink -f "__JAVA_HOME__"
readlink -f "__JAVA_HOME__/bin/java"
"__JAVA_HOME__/bin/java" -XshowSettings:properties -version
mvn -version
```

Any mismatch is a preflight failure, even if both installations report Java
26.

Capture the installed package inventory in a stable file. Run exactly one of
the first two commands, as appropriate for the host. The redirection is
intentional so the full, untruncated inventory is retained:

```shell
dpkg-query --show --showformat='${binary:Package}\t${Version}\t${Architecture}\n' > "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.txt"
rpm -qa --qf '%{NAME}\t%{EPOCHNUM}:%{VERSION}-%{RELEASE}\t%{ARCH}\n' > "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.txt"
LC_ALL=C sort -o "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.txt" "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.txt"
sha256sum "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.txt" > "__ENVIRONMENT_CAPTURE_DIR__/installed-packages.sha256"
```

Save the complete Java stdout and stderr without relying on a terminal
transcript:

```shell
"__JAVA_HOME__/bin/java" -version > "__ENVIRONMENT_CAPTURE_DIR__/java-version.txt" 2>&1
"__JAVA_HOME__/bin/java" -XshowSettings:properties -version > "__ENVIRONMENT_CAPTURE_DIR__/java-properties.txt" 2>&1
"__JAVA_HOME__/bin/java" -XshowSettings:security -version > "__ENVIRONMENT_CAPTURE_DIR__/java-security.txt" 2>&1
```

After resolving `__JAVA_HOME__`, record these runtime-image digests:

```shell
sha256sum \
    "__JAVA_HOME__/release" \
    "__JAVA_HOME__/bin/java" \
    "__JAVA_HOME__/lib/modules" \
    "__JAVA_HOME__/conf/security/java.security" \
    > "__ENVIRONMENT_CAPTURE_DIR__/java-image.sha256"
```

If a `java.security.properties` override, custom provider JAR, system-wide
cryptography policy, or Maven JVM configuration is present, record its exact
path, contents or package identity, and SHA-256. The provider probe must use
the same security override and provider classpath as the measured fork. If
that equivalence cannot be proved, preflight fails.

Create the following one-shot source file as
`__OPERATOR_DIR__/PacketProtectionProviderIdentity.java`, outside the
repository:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.ChaCha20ParameterSpec;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class PacketProtectionProviderIdentity {
    public static void main(String[] args) throws Exception {
        var aesKey = new SecretKeySpec(new byte[16], "AES");
        var chachaKey = new SecretKeySpec(new byte[32], "ChaCha20");
        var probes = new LinkedHashMap<String, Cipher>();

        var aesGcmEncrypt = Cipher.getInstance("AES/GCM/NoPadding");
        aesGcmEncrypt.init(Cipher.ENCRYPT_MODE,
                           aesKey,
                           new GCMParameterSpec(128, new byte[12]));
        probes.put("AES/GCM/NoPadding:encrypt", aesGcmEncrypt);

        var aesGcmDecrypt = Cipher.getInstance("AES/GCM/NoPadding");
        aesGcmDecrypt.init(Cipher.DECRYPT_MODE,
                           aesKey,
                           new GCMParameterSpec(128, new byte[12]));
        probes.put("AES/GCM/NoPadding:decrypt", aesGcmDecrypt);

        var aesEcb = Cipher.getInstance("AES/ECB/NoPadding");
        aesEcb.init(Cipher.ENCRYPT_MODE, aesKey);
        probes.put("AES/ECB/NoPadding:header-protection", aesEcb);

        var chachaAeadEncrypt = Cipher.getInstance("ChaCha20-Poly1305");
        chachaAeadEncrypt.init(Cipher.ENCRYPT_MODE,
                               chachaKey,
                               new IvParameterSpec(new byte[12]));
        probes.put("ChaCha20-Poly1305:encrypt", chachaAeadEncrypt);

        var chachaAeadDecrypt = Cipher.getInstance("ChaCha20-Poly1305");
        chachaAeadDecrypt.init(Cipher.DECRYPT_MODE,
                               chachaKey,
                               new IvParameterSpec(new byte[12]));
        probes.put("ChaCha20-Poly1305:decrypt", chachaAeadDecrypt);

        var chachaHeader = Cipher.getInstance("ChaCha20");
        chachaHeader.init(Cipher.DECRYPT_MODE,
                          chachaKey,
                          new ChaCha20ParameterSpec(new byte[12], 0));
        probes.put("ChaCha20:header-protection", chachaHeader);

        var result = new StringBuilder();
        for (var entry : probes.entrySet()) {
            var provider = entry.getValue().getProvider();
            result.append(entry.getKey())
                    .append('\t')
                    .append(provider.getName())
                    .append('\t')
                    .append(provider.getVersionStr())
                    .append('\t')
                    .append(provider.getClass().getName())
                    .append('\t')
                    .append(provider.getInfo())
                    .append(System.lineSeparator());
        }
        Files.writeString(Path.of(args[0]), result);
        System.out.print(result);
    }
}
```

Run it once with the same Java executable used by Maven:

```shell
"__JAVA_HOME__/bin/java" "__OPERATOR_DIR__/PacketProtectionProviderIdentity.java" "__ENVIRONMENT_CAPTURE_DIR__/packet-protection-providers.txt"
sha256sum "__OPERATOR_DIR__/PacketProtectionProviderIdentity.java" "__ENVIRONMENT_CAPTURE_DIR__/packet-protection-providers.txt" > "__ENVIRONMENT_CAPTURE_DIR__/packet-protection-providers.sha256"
```

Record the provider name and version for every initialized packet-protection
usage in the final report. The source file and output are operator evidence,
not repository changes. The complete effective Java security configuration
records registered provider order for the lifecycle TLS operations; this probe
does not independently trace the provider selected for every certificate,
signature, digest, HMAC, key-generation, key-agreement, or key-factory
operation. Record that limitation rather than claiming otherwise.

During preflight, set `__ENVIRONMENT_CAPTURE_DIR__` and
`__ENVIRONMENT_MANIFEST__` to their `environment-before` values. Collect every
raw environment sidecar, then create the manifest as UTF-8 `key=value` lines.
It must include the exact values or SHA-256 digests for every static
host-environment identity item above, the package inventory, Java properties,
effective Java security settings, and initialized packet-protection provider
output. Keep the controlled source/build manifest digests in the source
identity rather than this shared environment identity.

```shell
LC_ALL=C sort -o "__ENVIRONMENT_MANIFEST__" "__ENVIRONMENT_MANIFEST__"
sha256sum "__ENVIRONMENT_MANIFEST__" > "__ENVIRONMENT_MANIFEST__.sha256"
```

Use these minimum keys, adding more when the host has relevant controls:

```text
host.cpu_lscpu.sha256=
host.cpu_microcode=
host.firmware.sha256=
host.kernel.cmdline.sha256=
host.kernel.release=
host.kernel.uname.sha256=
host.numa.sha256=
host.os_release.sha256=
host.packages.sha256=
host.static_controls.sha256=
java.home=
java.image.binary.sha256=
java.image.modules.sha256=
java.image.release.sha256=
java.image.security_file.sha256=
java.inherited_environment.sha256=
java.packet_protection_provider_selection.sha256=
java.properties.sha256=
java.security_effective.sha256=
java.version.sha256=
schema=http3-quic-environment-v1
tool.git.version=
tool.maven.configuration.sha256=
tool.maven.version=
```

Each `.sha256` value is only the lowercase hex digest, not a path or
`sha256sum` output line. A genuinely unavailable optional value must be
`unavailable:<reason>`; do not omit the key. `host.static_controls` covers
configured affinity, NUMA, SMT, governor, turbo, socket, and power policy, not
dynamic load, temperature, pressure, or timestamps.

The manifest digest is the environment-stratum ID. During final verification,
repeat every raw capture with the `environment-after` token values, build and
sort the second manifest from those new files, and require:

```shell
cmp --silent __OPERATOR_DIR__/environment-before.manifest __OPERATOR_DIR__/environment-after.manifest
```

Keep capture timestamps in separate sidecars, not in either identity manifest.

## Source preflight and initial build

The following must all be true before the first Maven invocation:

1. `git status --short --untracked-files=all` is empty.
2. `git diff --check` succeeds.
3. `git rev-parse HEAD` is `__SELECTED_HEAD__` and
   `git rev-parse 'HEAD^{tree}'` is `__SELECTED_TREE__`.
4. `__SELECTED_HEAD__` is a descendant of
   `63975fbf38e7e10cff209112cd56d4f44fcab38b`.
5. PATH `java`, `"__JAVA_HOME__/bin/java"`, and the runtime printed by
   `mvn -version` all report Java 26 and the same canonical Java home.
6. No other benchmark, build, container, or high-load process is running.
7. The host power, CPU, affinity, and NUMA policy has been recorded.
8. Every `environment-before` capture and the sorted preflight environment
   manifest has been created and hashed.

From the repository root, create a fresh Java 26 reactor baseline before any
focused benchmark command:

```shell
mvn -T 1C clean install -Ptests -DskipTests -ntp \
    -l __OPERATOR_DIR__/initial-reactor-build-__ATTEMPT_ID__.log
```

This is artifact preparation, not benchmark evidence. If it fails, do not work
around the failure by selecting stale local artifacts. Resolve the build
problem or stop.

## Generate the controlled identity

Use a stable nonblank Resolver remote prefix. `cached` is suitable when the host
does not already use another recorded convention. Do not set the controlled
local prefix yourself; the generator creates an isolated staging prefix,
verifies it, and prints the immutable published prefix.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3QuicEvidenceManifestTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.remotePrefix=cached \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhelidon.benchmark.evidence.activeProcessorCount=2 \
    -Dhelidon.benchmark.evidence.mavenTimeoutSeconds=1800 \
    -l __OPERATOR_DIR__/controlled-manifest-generator-__ATTEMPT_ID__.log \
    test -ntp
```

If the host needs a non-default Maven local repository, add its exact
`-Dmaven.repo.local=...` value.

Record the printed immutable local prefix verbatim as `__LOCAL_PREFIX__`. Copy
the remote prefix from the build manifest verbatim as `__REMOTE_PREFIX__`.
Treat both as opaque values; do not derive or edit them.

Verify that the controlled root contains:

```text
http3-quic-controlled-source.manifest
http3-quic-controlled-build.manifest
http3-quic-controlled-build.log
```

Record their SHA-256 digests. Verify that their HEAD, status, Java, Maven,
active-processor count, timeout, remote prefix, and immutable local prefix agree
with the preflight. A generator timeout, identity mismatch, surviving child,
or retained staging snapshot is a failed campaign, not permission to continue
with ordinary local artifacts.

Retain the immutable Resolver prefix until the complete archive has been
independently verified. Never clean a broad Maven repository path.

### Supplemental-run identity sidecar

Only the seven controlled runners enforce source, build, artifact, and
fork-classpath identity and publish six-file bundles. Every later supplemental
runner rebuilds its test classes from the live checkout and does not
independently verify its fork classpath.

For every supplemental invocation, create a unique
`__OPERATOR_DIR__/<descriptive-name>-__ATTEMPT_ID__.identity.txt`. Record:

- the exact fully expanded Maven command and CPU/NUMA prefix;
- UTC start and end timestamps plus exit status;
- `git rev-parse HEAD` and `git rev-parse HEAD^{tree}` before and after;
- complete `git status --porcelain=v1 --untracked-files=all` output before and
  after, which must be empty;
- SHA-256 of the three controlled root identity files before and after;
- the environment-stratum ID before and after;
- the immutable local prefix, remote prefix, controlled-repository digest, and
  controlled runtime-classpath digest from the build manifest;
- Java and Maven versions;
- the exact runner, include expression, benchmark mode, parameters, forks,
  threads, profilers, fork JVM arguments, and relevant harness-source digests;
- the result, workload-output, and Maven-log paths; and
- SHA-256 of every produced result and log.

Reject the run if HEAD, tree, status, an identity-file digest, or the controlled
prefix changes. This establishes a clean-HEAD, controlled-dependency,
source-coherent supplemental result. Because the simple runner does not perform
the controlled fork-classpath verification itself, classify these files as
supplemental evidence rather than controlled authoritative bundles and state
that limitation in the final report.

## Functional readiness smoke

Smoke runs only prove that the Linux host can execute the workload. Their
numbers are not evidence and must not be included in performance tables.

Run one cold lifecycle scenario:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverLifecycleHandshakeReady$' \
    -Dquic.server.admission.jmh.scenario=COLD_NO_RETRY \
    -Dquic.server.admission.jmh.forks=1 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=0 \
    -Dquic.server.admission.jmh.measurementIterations=1 \
    -Dquic.server.admission.jmh.measurementMillis=100 \
    -l __OPERATOR_DIR__/smoke-lifecycle-__ATTEMPT_ID__.log \
    clean test -ntp
```

Run all four small-write paths with one representative shape:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3SmallWriteJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.small.write.jmh.mode=smoke \
    -Dhttp3.small.write.jmh.runId=linux-readiness \
    -Dhttp3.small.write.jmh.shape=SHORT_8K_1200 \
    -l __OPERATOR_DIR__/smoke-small-write-__ATTEMPT_ID__.log \
    clean test -ntp
```

Run the adverse-network functional smoke:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3AdverseNetworkEvidence \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.adverse.network.mode=smoke \
    -Dhttp3.adverse.network.runId=linux-readiness \
    -l __OPERATOR_DIR__/smoke-adverse-network-__ATTEMPT_ID__.log \
    clean test -ntp
```

Stop if any smoke run reports a protocol, accounting, cleanup, process, or
timeout failure. Do not reduce evidence settings to avoid a readiness failure.

## Authoritative controlled campaign

Use one JMH worker, five forks, five one-second warmups, and ten one-second
measurements for lifecycle and small-write evidence. This exceeds the harness
minimum and provides useful fork distributions without inventing a permanent
machine-independent threshold.

Use a unique run ID for every command. The examples below use stable IDs for a
new campaign. If any is already present, increment its numeric suffix rather
than overwriting it.

### 1. QUIC handshake-ready latency

This unprofiled SampleTime run covers all six cold/resumed and Retry scenarios.
Do not add GC, allocation, CPU, JFR, or stack profilers.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverLifecycleHandshakeReady$' \
    -Dquic.server.admission.jmh.evidence=true \
    -Dquic.server.admission.jmh.runId=linux-lifecycle-latency-__ATTEMPT_ID__ \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=1000 \
    -Dquic.server.admission.jmh.measurementIterations=10 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -l __OPERATOR_DIR__/lifecycle-latency-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### 2. HTTP/3 small-write latency

Omit the `shape` property so the run covers all four paths and all seven shapes:

- buffered upload;
- eager-frame upload;
- buffered download;
- dispatch-barrier download;
- 8 KiB bodies written in 1, 16, 256, 1200, and 4096 byte chunks; and
- 128 KiB bodies written in 1200 and 4096 byte chunks.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3SmallWriteJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.small.write.jmh.mode=latency \
    -Dhttp3.small.write.jmh.runId=linux-small-write-latency-__ATTEMPT_ID__ \
    -Dhttp3.small.write.jmh.forks=5 \
    -Dhttp3.small.write.jmh.threads=1 \
    -Dhttp3.small.write.jmh.warmupIterations=5 \
    -Dhttp3.small.write.jmh.warmupMillis=1000 \
    -Dhttp3.small.write.jmh.measurementIterations=10 \
    -Dhttp3.small.write.jmh.measurementMillis=1000 \
    -l __OPERATOR_DIR__/small-write-latency-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Do not compare eager-frame upload with dispatch-barrier download as symmetric
client/server operations. The latter includes a synchronous server dispatch
barrier; the former does not.

### 3. HTTP/3 adverse-network and stalled-close evidence

Run the complete deterministic matrix:

- control;
- 10 ms delay with up to 2 ms deterministic jitter;
- 5% deterministic loss;
- 10% delayed reordering with a 20 ms delay;
- a 100 ms forwarding stall after eight datagrams; and
- the composite of all impairments.

The evidence run uses five warmups, 100 successful samples per scenario, and 50
active-stall close samples. Do not lower those values or override the maintained
timeouts without a separately reviewed reason.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3AdverseNetworkEvidence \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.adverse.network.mode=evidence \
    -Dhttp3.adverse.network.runId=linux-adverse-network-__ATTEMPT_ID__ \
    -Dhttp3.adverse.network.warmups=5 \
    -Dhttp3.adverse.network.samples=100 \
    -Dhttp3.adverse.network.stalledCloseSamples=50 \
    -l __OPERATOR_DIR__/adverse-network-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Use paired adverse-minus-control exchange deltas and their bootstrap intervals.
Do not compare unpaired scenario point estimates when a paired delta exists.

### 4. Complete-lifecycle process CPU

CPU evidence uses the reconnect-ready-through-peer-termination boundary so
cleanup is inside the lifecycle. Enable only the process CPU profiler.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverLifecycleReconnectReadyPeerTermination$' \
    -Dquic.server.admission.jmh.evidence=true \
    -Dquic.server.admission.jmh.runId=linux-lifecycle-cpu-__ATTEMPT_ID__ \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=1000 \
    -Dquic.server.admission.jmh.measurementIterations=10 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.processCpuProfiler=true \
    -l __OPERATOR_DIR__/lifecycle-cpu-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### 5. Complete-lifecycle process allocation

Run the same complete lifecycle with only the process allocation profiler:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverLifecycleReconnectReadyPeerTermination$' \
    -Dquic.server.admission.jmh.evidence=true \
    -Dquic.server.admission.jmh.runId=linux-lifecycle-allocation-__ATTEMPT_ID__ \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=1000 \
    -Dquic.server.admission.jmh.measurementIterations=10 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.processAllocationProfiler=true \
    -l __OPERATOR_DIR__/lifecycle-allocation-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### 6. HTTP/3 small-write process CPU

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3SmallWriteJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.small.write.jmh.mode=cpu \
    -Dhttp3.small.write.jmh.runId=linux-small-write-cpu-__ATTEMPT_ID__ \
    -Dhttp3.small.write.jmh.forks=5 \
    -Dhttp3.small.write.jmh.threads=1 \
    -Dhttp3.small.write.jmh.warmupIterations=5 \
    -Dhttp3.small.write.jmh.warmupMillis=1000 \
    -Dhttp3.small.write.jmh.measurementIterations=10 \
    -Dhttp3.small.write.jmh.measurementMillis=1000 \
    -l __OPERATOR_DIR__/small-write-cpu-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### 7. HTTP/3 small-write process allocation

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3SmallWriteJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    -Dhelidon.benchmark.evidence.root=__CONTROLLED_ROOT__ \
    -Dhttp3.small.write.jmh.mode=allocation \
    -Dhttp3.small.write.jmh.runId=linux-small-write-allocation-__ATTEMPT_ID__ \
    -Dhttp3.small.write.jmh.forks=5 \
    -Dhttp3.small.write.jmh.threads=1 \
    -Dhttp3.small.write.jmh.warmupIterations=5 \
    -Dhttp3.small.write.jmh.warmupMillis=1000 \
    -Dhttp3.small.write.jmh.measurementIterations=10 \
    -Dhttp3.small.write.jmh.measurementMillis=1000 \
    -l __OPERATOR_DIR__/small-write-allocation-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

The process profilers omit the final measurement because trial teardown would
contaminate it. Their metrics include the client, server, TLS, QUIC, HTTP/3,
executors, and harness. Allocation capacity is not retained or live heap.
Component stack percentages are sampled attribution. Process CPU is whole-fork
CPU, not receiver-thread CPU. Never compare a profiled primary score with the
unprofiled latency score.

## Required server-admission and cap matrix

The controlled lifecycle runs cover ordinary cold/resumed handshakes and
reconnect cleanup. The following current-source matrix additionally covers
shared admission, rejecting listeners, saturated pending capacity, valid
handshakes under concurrency, cap-wide timeout cleanup, and protected route
close. These runners do not publish controlled six-file bundles, so keep the
checkout clean, use the controlled Resolver prefix, save Maven logs, and record
the exact HEAD before and after each command.

### Shared admission and rejection, one worker

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.(sharedDeadlineOfferCancel|serverListenerRejected|serverPendingLimitRejected)$' \
    -Dquic.server.admission.jmh.pendingLimit=256 \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=500 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.gcProfiler=true \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-admission-shared-t1-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-admission-shared-t1-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-admission-shared-t1-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat with four workers:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.(sharedDeadlineOfferCancel|serverListenerRejected|serverPendingLimitRejected)$' \
    -Dquic.server.admission.jmh.pendingLimit=256 \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=4 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=500 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.gcProfiler=true \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-admission-shared-t4-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-admission-shared-t4-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-admission-shared-t4-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### Valid TLS/QUIC handshakes, one and four workers

Run the following once with the shown one-worker settings, then once with
`threads=4` and distinct `t4` result, output, and Maven-log names:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverValidHandshake$' \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=500 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-valid-handshake-t1-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-valid-handshake-t1-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-valid-handshake-t1-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Do not enable a profiler for this latency boundary. This benchmark uses the
maintained server default of 256 pending handshakes; it has no configurable
`pendingLimit` JMH parameter.

### Cap-wide timeout and protected-close lifecycle

This single-shot run creates 256 real pending server connections. It measures
timeout cleanup and the required protected closing-route lifetime, including
capacity recovery. The protected-close wall time contains the protocol-required
three-PTO lifetime; report it separately from cleanup overhead.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.(serverPendingHandshakeExpiry|serverPendingProtectedClose)$' \
    -Dquic.server.admission.jmh.pendingLimit=256 \
    -Dquic.server.admission.jmh.forks=3 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.gcProfiler=true \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-cap-lifecycle-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-cap-lifecycle-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-cap-lifecycle-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

## Guardrails for the remaining 21 invocations

Run every remaining invocation below through a process-tree supervisor. Do not
run the Maven snippets directly without these controls. Configure the
supervisor for every invocation with:

```text
CAMPAIGN_PROGRESS_STARTUP_SECONDS=300
CAMPAIGN_NO_PROGRESS_SECONDS=120
CAMPAIGN_PROGRESS_FILE=<the exact absolute value of this runner's *.jmh.output property>
CAMPAIGN_RUNTIME_MAX_SECONDS=<the hard limit from the table below>
```

Poll the progress file every five seconds. The startup deadline begins when the
supervisor launches the process. After the progress file first appears, its
modification time must advance at least once every 120 seconds. A missing file
after 300 seconds, 120 seconds without progress, or the hard wall-time limit
must terminate the complete Maven, Surefire, JMH, and forked-JVM process tree.
Preserve the partial output, launcher log, systemd telemetry, and host
snapshots; classify that attempt as rejected and do not start another
invocation automatically.

The rows below are the required one-at-a-time order within run-order steps 11
through 13. Start the next row only after the preceding process tree has exited,
the result is a nonempty valid JSON array, the live output and identity sidecars
have been retained, and the host has returned to the predeclared idle and
thermal state. The timed minimum is JMH iteration time; planned wall time adds
Maven, fork, teardown, and profiler overhead. `RuntimeMaxSec` is deliberately
larger than planned wall time, while the live-output watchdog prevents that
margin from becoming an unobservable wait.

| Order | Invocation and live-output stem | Result cells | Timed minimum | Planned wall | `RuntimeMaxSec` |
| ---: | --- | ---: | ---: | ---: | ---: |
| 1 | packet header unprofiled | 8 | `00:06:00` | `00:10:00` | `900` |
| 2 | packet header allocation | 8 | `00:06:00` | `00:12:00` | `1200` |
| 3 | packet/body unprofiled | 48 | `00:36:00` | `00:45:00` | `3300` |
| 4 | packet/body allocation | 48 | `00:36:00` | `00:50:00` | `3600` |
| 5 | packet buffer shapes unprofiled | 108 | `01:21:00` | `01:40:00` | `7200` |
| 6 | packet buffer shapes allocation | 108 | `01:21:00` | `01:50:00` | `7800` |
| 7 | ACK decode unprofiled | 12 | `00:15:00` | `00:20:00` | `1800` |
| 8 | ACK decode allocation | 12 | `00:15:00` | `00:23:00` | `2100` |
| 9 | raw ingress CPU | 10 | `00:06:40` | `00:12:00` | `1200` |
| 10 | endpoint ingress CPU | 20 | `00:13:20` | `00:22:00` | `1800` |
| 11 | raw ingress allocation | 3 | `00:01:15` | `00:08:00` | `900` |
| 12 | endpoint ingress allocation | 6 | `00:02:30` | `00:12:00` | `1200` |
| 13 | deadline batch | 3 | `00:04:22.5` | `00:08:00` | `900` |
| 14 | listener-rejection reservation | 1 | `00:01:27.5` | `00:05:00` | `600` |
| 15 | synthetic establishment t1 | 1 | `00:00:52.5` | `00:05:00` | `600` |
| 16 | synthetic establishment t4 | 1 | `00:00:52.5` | `00:05:00` | `600` |
| 17 | route lifecycle t1 | 5 | `00:03:45` | `00:08:00` | `900` |
| 18 | route lifecycle t4 | 5 | `00:03:45` | `00:08:00` | `900` |
| 19 | established path | 15 | `00:11:15` | `00:18:00` | `1800` |
| 20 | HTTP/3 eligibility | 17 | `00:12:45` | `00:22:00` | `1800` |
| 21 | HTTP/3 DATA ingress | 30 | `00:22:30` | `00:35:00` | `3000` |

For each row, `CAMPAIGN_PROGRESS_FILE` is the absolute expansion of the
runner-specific `*.jmh.output` property shown below. The result, live-output,
Maven-log, invocation, and telemetry names must all use that row's same fresh
attempt ID. Do not monitor the JSON result as a progress signal: JMH publishes
it only after the invocation completes.

## Required packet-protection matrix

Packet protection is sensitive to Linux architecture, CPU crypto instructions,
JDK build, and JCA provider. Run the complete final-source matrix rather than
copying allocation or latency values from another operating system.

The main matrix covers:

- AES-128-GCM and ChaCha20-Poly1305;
- 64, 1200, 1452, and 65527 byte protected datagrams;
- thread-local and shared protection;
- legacy and packed header masks;
- encryption;
- authenticated decryption; and
- bad-tag decryption.

Run the header and packet/body method families separately. One result cell is
one benchmark method and its applicable parameter combination. Every cell below
has three forks, and every fork schedules five one-second warmups plus ten
one-second measurements, for 45 timed seconds per cell. The live JMH output file
records progress and ETA independently from the Maven launcher log. Wall-clock
time is longer than the stated timed minimum because it also includes Maven
clean/build work, fork startup and teardown, and profiler overhead.

### Header-protection method family

The four header-protection methods vary only by the two cipher suites. This is
exactly 8 result cells. The unprofiled invocation therefore has a timed minimum
and matrix-only ETA of `8 * 3 * (5 + 10) * 1 second = 360 seconds`, or
`00:06:00`. Plan about `00:10:00` of wall-clock time on the campaign host and
use the live output for the current ETA.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPacketProtectionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.packet.protection.jmh.include=^io\.helidon\.quic\.QuicPacketProtectionJmhBenchmark\.(threadLocalHeaderProtection|sharedHeaderProtection|threadLocalPackedHeaderProtection|sharedPackedHeaderProtection)$' \
    -Dquic.packet.protection.jmh.forks=3 \
    -Dquic.packet.protection.jmh.threads=4 \
    -Dquic.packet.protection.jmh.warmupIterations=5 \
    -Dquic.packet.protection.jmh.warmupMillis=1000 \
    -Dquic.packet.protection.jmh.measurementIterations=10 \
    -Dquic.packet.protection.jmh.measurementMillis=1000 \
    -Dquic.packet.protection.jmh.result=__SUPPLEMENTAL_DIR__/quic-packet-protection-header-unprofiled-__ATTEMPT_ID__.json \
    -Dquic.packet.protection.jmh.output=__SUPPLEMENTAL_DIR__/quic-packet-protection-header-unprofiled-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-packet-protection-header-unprofiled-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat this header-only command with exactly the same include, parameters, and
timings and a fresh attempt ID. Add
`-Dquic.packet.protection.jmh.gcProfiler=true`, and replace
`header-unprofiled` with `header-allocation` in the JSON result, live JMH
output, and Maven-log names. The profiled repeat is also exactly 8 cells with a
`00:06:00` timed minimum. Use the unprofiled result for latency and the
GC-profiled result for normalized allocation.

### Packet/body method family

The six encrypt, authenticated-decrypt, and bad-tag-decrypt methods vary by two
cipher suites and four datagram sizes. This is exactly
`6 * 2 * 4 = 48` result cells. The unprofiled invocation therefore has a timed
minimum and matrix-only ETA of
`48 * 3 * (5 + 10) * 1 second = 2160 seconds`, or `00:36:00`. Plan about
`00:45:00` of wall-clock time on the campaign host and use the live output for
the current ETA.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPacketProtectionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.packet.protection.jmh.include=^io\.helidon\.quic\.QuicPacketProtectionJmhBenchmark\.(threadLocalEncrypt|sharedEncrypt|threadLocalDecrypt|sharedDecrypt|threadLocalBadTagDecrypt|sharedBadTagDecrypt)$' \
    -Dquic.packet.protection.jmh.forks=3 \
    -Dquic.packet.protection.jmh.threads=4 \
    -Dquic.packet.protection.jmh.warmupIterations=5 \
    -Dquic.packet.protection.jmh.warmupMillis=1000 \
    -Dquic.packet.protection.jmh.measurementIterations=10 \
    -Dquic.packet.protection.jmh.measurementMillis=1000 \
    -Dquic.packet.protection.jmh.result=__SUPPLEMENTAL_DIR__/quic-packet-protection-packet-body-unprofiled-__ATTEMPT_ID__.json \
    -Dquic.packet.protection.jmh.output=__SUPPLEMENTAL_DIR__/quic-packet-protection-packet-body-unprofiled-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-packet-protection-packet-body-unprofiled-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat this packet/body-only command with exactly the same include, parameters,
and timings and a fresh attempt ID. Add
`-Dquic.packet.protection.jmh.gcProfiler=true`, and replace
`packet-body-unprofiled` with `packet-body-allocation` in the JSON result, live
JMH output, and Maven-log names. The profiled repeat is also exactly 48 cells
with a `00:36:00` timed minimum.

Together, the two unprofiled invocations produce 56 result cells with a
`00:42:00` timed minimum. Their profiled repeats produce another 56 result
cells. The complete four-invocation main packet-protection campaign therefore
schedules 112 result-cell executions and at least `01:24:00` of timed benchmark
work, plus untimed build, fork, and profiler overhead. Plan about `00:55:00`
for one complete unprofiled or profiled pair and reserve about `02:00:00` for
all four invocations.

Run the complete same-execution buffer-shape matrix without a profiler:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPacketProtectionBufferShapeJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.packet.protection.buffer.shape.jmh.include=^io\.helidon\.quic\.QuicPacketProtectionBufferShapeJmhBenchmark\..*$' \
    -Dquic.packet.protection.buffer.shape.jmh.forks=3 \
    -Dquic.packet.protection.buffer.shape.jmh.threads=1 \
    -Dquic.packet.protection.buffer.shape.jmh.warmupIterations=5 \
    -Dquic.packet.protection.buffer.shape.jmh.warmupMillis=1000 \
    -Dquic.packet.protection.buffer.shape.jmh.measurementIterations=10 \
    -Dquic.packet.protection.buffer.shape.jmh.measurementMillis=1000 \
    -Dquic.packet.protection.buffer.shape.jmh.result=__SUPPLEMENTAL_DIR__/quic-packet-protection-buffer-shapes-unprofiled-__ATTEMPT_ID__.json \
    -Dquic.packet.protection.buffer.shape.jmh.output=__SUPPLEMENTAL_DIR__/quic-packet-protection-buffer-shapes-unprofiled-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-packet-protection-buffer-shapes-unprofiled-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat the command with exactly the same matrix and timings, add
`-Dquic.packet.protection.buffer.shape.jmh.gcProfiler=true`, and use fresh
`quic-packet-protection-buffer-shapes-allocation-__ATTEMPT_ID__.json` and
matching live-output and Maven-log names.

The shape matrix covers mutable, read-only, heap, direct, distinct, and
overlapping inputs. Compare shapes within the same execution. Heap exact-overlap
production paths should not regain payload-proportional Helidon-owned copies.
Direct-buffer provider allocation is a separate JCA behavior and must not be
described as retained Helidon heap. Shared-state timing exposes lock contention;
it is not single-packet latency.

## Required ACK-decode matrix

Run both the configured range limit and unbounded control for:

- one range;
- 32 fragmented ranges;
- the 1024-range boundary;
- one 1025-range excess;
- four repeated excess frames; and
- a 32-frame non-ACK control.

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicAckDecodeJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.ack.decode.jmh.include=^io\.helidon\.quic\.packet\.QuicAckDecodeJmhBenchmark\.(configuredLimit|unboundedControl)$' \
    -Dquic.ack.decode.jmh.forks=5 \
    -Dquic.ack.decode.jmh.threads=1 \
    -Dquic.ack.decode.jmh.warmupIterations=5 \
    -Dquic.ack.decode.jmh.warmupMillis=1000 \
    -Dquic.ack.decode.jmh.measurementIterations=10 \
    -Dquic.ack.decode.jmh.measurementMillis=1000 \
    -Dquic.ack.decode.jmh.result=__SUPPLEMENTAL_DIR__/quic-ack-decode-unprofiled-__ATTEMPT_ID__.json \
    -Dquic.ack.decode.jmh.output=__SUPPLEMENTAL_DIR__/quic-ack-decode-unprofiled-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-ack-decode-unprofiled-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat the command with exactly the same matrix and timings, add
`-Dquic.ack.decode.jmh.gcProfiler=true`, and use fresh
`quic-ack-decode-allocation-__ATTEMPT_ID__.json` and matching live-output and
Maven-log names.
Use the unprofiled result for throughput and the GC-profiled result for
normalized allocation.

The configured excess path should reject without allocation proportional to the
peer-selected range count or repeated excess frames. Accepted multi-range
decoding still allocates retained range data. Do not reconstruct or quote an
old/new percentage unless an exact historical source and benchmark identity is
separately supplied and rerun in this same environment stratum.

## Required matched endpoint-ingress campaign

Endpoint ingress compares the QUIC endpoint receive, heap copy, connection-ID
lookup, and route callback with a matched raw-UDP receive lane. It excludes
packet protection, streams, TLS, HTTP/3, application work, a physical NIC, and
real path latency.

Run unprofiled capacity first. Use the same packet sizes, target rates, sender
shape, offer window, and drain window for raw and endpoint cases.

### Raw-UDP capacity

```shell
mvn -Ptests,jmh \
    -pl :helidon-quic,:helidon-webclient-http3,:helidon-tests-benchmark-jmh \
    -Dtest=QuicEndpointIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.endpoint.ingress.jmh.include=^io\.helidon\.quic\.QuicEndpointIngressJmhBenchmark\.rawUdpPacedWindow$' \
    -Dquic.endpoint.ingress.jmh.forks=5 \
    -Dquic.endpoint.ingress.jmh.threads=1 \
    -Dquic.endpoint.ingress.jmh.warmupIterations=3 \
    -Dquic.endpoint.ingress.jmh.warmupMillis=1000 \
    -Dquic.endpoint.ingress.jmh.measurementIterations=5 \
    -Dquic.endpoint.ingress.jmh.measurementMillis=1000 \
    -Dquic.endpoint.ingress.jmh.timeoutMillis=60000 \
    '-Dquic.endpoint.ingress.jmh.packetSize=64,1452' \
    '-Dquic.endpoint.ingress.jmh.targetPps=50000,75000,100000,125000,150000' \
    -Dquic.endpoint.ingress.jmh.senderCount=1 \
    -Dquic.endpoint.ingress.jmh.senderWorkingSet=1 \
    -Dquic.endpoint.ingress.jmh.offerMillis=1000 \
    -Dquic.endpoint.ingress.jmh.drainMillis=250 \
    -Dquic.endpoint.ingress.jmh.lateMicros=1000 \
    -Dquic.endpoint.ingress.jmh.processCpuProfiler=false \
    -Dquic.endpoint.ingress.jmh.result=__SUPPLEMENTAL_DIR__/quic-ingress-capacity-raw-__ATTEMPT_ID__.json \
    -Dquic.endpoint.ingress.jmh.output=__SUPPLEMENTAL_DIR__/quic-ingress-capacity-raw-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-ingress-capacity-raw-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

### QUIC endpoint capacity

```shell
mvn -Ptests,jmh \
    -pl :helidon-quic,:helidon-webclient-http3,:helidon-tests-benchmark-jmh \
    -Dtest=QuicEndpointIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.endpoint.ingress.jmh.include=^io\.helidon\.quic\.QuicEndpointIngressJmhBenchmark\.pacedMatchedWindow$' \
    -Dquic.endpoint.ingress.jmh.forks=5 \
    -Dquic.endpoint.ingress.jmh.threads=1 \
    -Dquic.endpoint.ingress.jmh.warmupIterations=3 \
    -Dquic.endpoint.ingress.jmh.warmupMillis=1000 \
    -Dquic.endpoint.ingress.jmh.measurementIterations=5 \
    -Dquic.endpoint.ingress.jmh.measurementMillis=1000 \
    -Dquic.endpoint.ingress.jmh.timeoutMillis=60000 \
    '-Dquic.endpoint.ingress.jmh.strategy=NIO_SELECTOR,VIRTUAL_THREAD' \
    -Dquic.endpoint.ingress.jmh.executorMode=VIRTUAL_PER_TASK \
    -Dquic.endpoint.ingress.jmh.routeCount=2048 \
    '-Dquic.endpoint.ingress.jmh.packetSize=64,1452' \
    '-Dquic.endpoint.ingress.jmh.targetPps=50000,75000,100000,125000,150000' \
    -Dquic.endpoint.ingress.jmh.senderCount=1 \
    -Dquic.endpoint.ingress.jmh.senderWorkingSet=1 \
    -Dquic.endpoint.ingress.jmh.offerMillis=1000 \
    -Dquic.endpoint.ingress.jmh.drainMillis=250 \
    -Dquic.endpoint.ingress.jmh.lateMicros=1000 \
    -Dquic.endpoint.ingress.jmh.processCpuProfiler=false \
    -Dquic.endpoint.ingress.jmh.result=__SUPPLEMENTAL_DIR__/quic-ingress-capacity-endpoint-__ATTEMPT_ID__.json \
    -Dquic.endpoint.ingress.jmh.output=__SUPPLEMENTAL_DIR__/quic-ingress-capacity-endpoint-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-ingress-capacity-endpoint-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

If raw UDP and both endpoint strategies remain below 1% median missing at
150,000 packets/s, report that the ceiling was not reached. Do not call
150,000 packets/s the host limit. A higher-rate extension may be run under a
new result name, but predeclare one common rate grid and apply it unchanged to
the raw control and both endpoint strategies.

### Endpoint CPU support

Repeat the preceding raw and endpoint capacity commands after replacing
`processCpuProfiler=false` with `processCpuProfiler=true`. Replace each result,
output, and Maven-log name with a fresh name containing `-cpu-__ATTEMPT_ID__`;
do not append a second copy of the property and do not reuse an unprofiled
path. CPU evidence is supporting whole-process measurement and includes senders
and harness work.

### Endpoint allocation support

Use burst methods for process-wide JFR allocation. Disable process CPU
profiling. Allocation `B/op` is per 32-packet burst; divide by 32 for a
per-packet estimate.

Raw allocation:

```shell
mvn -Ptests,jmh \
    -pl :helidon-quic,:helidon-webclient-http3,:helidon-tests-benchmark-jmh \
    -Dtest=QuicEndpointIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.endpoint.ingress.jmh.include=^io\.helidon\.quic\.QuicEndpointIngressJmhBenchmark\.rawUdpBurstThroughput$' \
    -Dquic.endpoint.ingress.jmh.forks=5 \
    -Dquic.endpoint.ingress.jmh.threads=1 \
    -Dquic.endpoint.ingress.jmh.warmupIterations=2 \
    -Dquic.endpoint.ingress.jmh.warmupMillis=1000 \
    -Dquic.endpoint.ingress.jmh.measurementIterations=3 \
    -Dquic.endpoint.ingress.jmh.measurementMillis=1000 \
    '-Dquic.endpoint.ingress.jmh.packetSize=64,1200,1452' \
    -Dquic.endpoint.ingress.jmh.batchSize=32 \
    -Dquic.endpoint.ingress.jmh.processAllocationProfiler=true \
    -Dquic.endpoint.ingress.jmh.processCpuProfiler=false \
    -Dquic.endpoint.ingress.jmh.result=__SUPPLEMENTAL_DIR__/quic-ingress-allocation-raw-__ATTEMPT_ID__.json \
    -Dquic.endpoint.ingress.jmh.output=__SUPPLEMENTAL_DIR__/quic-ingress-allocation-raw-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-ingress-allocation-raw-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Endpoint allocation:

```shell
mvn -Ptests,jmh \
    -pl :helidon-quic,:helidon-webclient-http3,:helidon-tests-benchmark-jmh \
    -Dtest=QuicEndpointIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.endpoint.ingress.jmh.include=^io\.helidon\.quic\.QuicEndpointIngressJmhBenchmark\.matchedBurstThroughput$' \
    -Dquic.endpoint.ingress.jmh.forks=5 \
    -Dquic.endpoint.ingress.jmh.threads=1 \
    -Dquic.endpoint.ingress.jmh.warmupIterations=2 \
    -Dquic.endpoint.ingress.jmh.warmupMillis=1000 \
    -Dquic.endpoint.ingress.jmh.measurementIterations=3 \
    -Dquic.endpoint.ingress.jmh.measurementMillis=1000 \
    '-Dquic.endpoint.ingress.jmh.strategy=NIO_SELECTOR,VIRTUAL_THREAD' \
    -Dquic.endpoint.ingress.jmh.executorMode=VIRTUAL_PER_TASK \
    -Dquic.endpoint.ingress.jmh.routeCount=2048 \
    -Dquic.endpoint.ingress.jmh.routeAccess=ROTATING \
    '-Dquic.endpoint.ingress.jmh.packetSize=64,1200,1452' \
    -Dquic.endpoint.ingress.jmh.batchSize=32 \
    -Dquic.endpoint.ingress.jmh.processAllocationProfiler=true \
    -Dquic.endpoint.ingress.jmh.processCpuProfiler=false \
    -Dquic.endpoint.ingress.jmh.result=__SUPPLEMENTAL_DIR__/quic-ingress-allocation-endpoint-__ATTEMPT_ID__.json \
    -Dquic.endpoint.ingress.jmh.output=__SUPPLEMENTAL_DIR__/quic-ingress-allocation-endpoint-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-ingress-allocation-endpoint-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

For every open-loop row, verify:

```text
planned = attempted + schedulerSkipped
attempted = accepted + socketRejected
missingAtDrainDeadline = accepted - routed
```

Duplicate, cross-window, outside-window, and received-without-acceptance counts
must be zero. Delivered latency is conditional on delivery. When more than 1%
of accepted datagrams are missing, p99 is censored and is not an all-accepted
tail-latency result.

Compare endpoint routed rate with matched raw routed rate only where the raw
control has clean headroom. A consistent 20-25% endpoint gap in such a region
is the predeclared trigger for investigating receive topology. It is not an
automatic instruction to add listener sharding.

## Maintained supporting hot-path suite

Run this suite after the required product-level evidence unless the user
explicitly narrows the campaign. These measurements explain costs and scaling;
they are not substitutes for the complete lifecycle, adverse-network, or
endpoint results.

### Deadline batch complexity

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.dueBatchExpiry$' \
    '-Dquic.server.admission.jmh.expiringEvents=256,1024,4096' \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-deadline-batch-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-deadline-batch-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-deadline-batch-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Treat this as a complexity-shape measurement. It measures captured due-event
processing, not complete timeout cleanup or real executor handoff.

### Listener-rejection reservation allocation

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.listenerRejectedReservationRelease$' \
    -Dquic.server.admission.jmh.pendingLimit=256 \
    -Dquic.server.admission.jmh.forks=5 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.gcProfiler=true \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-listener-rejection-reservation-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-listener-rejection-reservation-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-listener-rejection-reservation-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

This isolates reservation ownership. It excludes packet ingress, the listener
callback, TLS, and shared-runtime contention.

### Synthetic successful establishment

Run the following with one worker:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicServerAdmissionJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.server.admission.jmh.include=^io\.helidon\.quic\.QuicServerAdmissionJmhBenchmark\.serverEstablishedConnectionLifecycle$' \
    -Dquic.server.admission.jmh.pendingLimit=256 \
    -Dquic.server.admission.jmh.forks=3 \
    -Dquic.server.admission.jmh.threads=1 \
    -Dquic.server.admission.jmh.warmupIterations=5 \
    -Dquic.server.admission.jmh.warmupMillis=500 \
    -Dquic.server.admission.jmh.measurementIterations=15 \
    -Dquic.server.admission.jmh.measurementMillis=1000 \
    -Dquic.server.admission.jmh.gcProfiler=true \
    -Dquic.server.admission.jmh.result=__SUPPLEMENTAL_DIR__/quic-synthetic-establishment-t1-__ATTEMPT_ID__.json \
    -Dquic.server.admission.jmh.output=__SUPPLEMENTAL_DIR__/quic-synthetic-establishment-t1-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-synthetic-establishment-t1-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

Repeat with four workers and distinct `t4` result, output, and Maven-log names.
This synthetic lifecycle validates cancellation and allocation scaling but does
not perform a valid cryptographic handshake; use the complete lifecycle and
valid-handshake results for product conclusions.

### Route-lifecycle allocation

Run the complete five-method route-lifecycle matrix once with one worker and
once with four:

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicRouteLifecycleJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.route.lifecycle.jmh.include=^io\.helidon\.quic\.QuicRouteLifecycleJmhBenchmark\..*$' \
    -Dquic.route.lifecycle.jmh.forks=3 \
    -Dquic.route.lifecycle.jmh.threads=1 \
    -Dquic.route.lifecycle.jmh.warmupIterations=5 \
    -Dquic.route.lifecycle.jmh.warmupMillis=1000 \
    -Dquic.route.lifecycle.jmh.measurementIterations=10 \
    -Dquic.route.lifecycle.jmh.measurementMillis=1000 \
    -Dquic.route.lifecycle.jmh.gcProfiler=true \
    -Dquic.route.lifecycle.jmh.result=__SUPPLEMENTAL_DIR__/quic-route-lifecycle-t1-__ATTEMPT_ID__.json \
    -Dquic.route.lifecycle.jmh.output=__SUPPLEMENTAL_DIR__/quic-route-lifecycle-t1-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-route-lifecycle-t1-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

For the four-worker run, change `threads` to `4` and use `t4` filenames. This
microbenchmark calls lifecycle transitions directly and intentionally excludes
route locks, endpoint maps, timers, and server admission.

### QUIC established-path matrix

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=QuicPathJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dquic.path.jmh.include=^io\.helidon\.quic\.QuicPathJmhBenchmark\..*$' \
    -Dquic.path.jmh.forks=3 \
    -Dquic.path.jmh.threads=4 \
    -Dquic.path.jmh.warmupIterations=5 \
    -Dquic.path.jmh.warmupMillis=1000 \
    -Dquic.path.jmh.measurementIterations=10 \
    -Dquic.path.jmh.measurementMillis=1000 \
    -Dquic.path.jmh.gcProfiler=true \
    -Dquic.path.jmh.result=__SUPPLEMENTAL_DIR__/quic-established-path-__ATTEMPT_ID__.json \
    -Dquic.path.jmh.output=__SUPPLEMENTAL_DIR__/quic-established-path-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/quic-established-path-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

This covers one and sixteen connections, synchronous and asynchronous datagram
send, 64 and 4096 in-flight packets, and one and 32 ACK ranges where applicable.

### HTTP/3 eligibility and shared-cache cardinality

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3EligibilityJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dhttp3.eligibility.jmh.include=^io\.helidon\.webclient\.http3\.Http3EligibilityJmhBenchmark\..*$' \
    -Dhttp3.eligibility.jmh.forks=3 \
    -Dhttp3.eligibility.jmh.threads=4 \
    -Dhttp3.eligibility.jmh.warmupIterations=5 \
    -Dhttp3.eligibility.jmh.warmupMillis=1000 \
    -Dhttp3.eligibility.jmh.measurementIterations=10 \
    -Dhttp3.eligibility.jmh.measurementMillis=1000 \
    -Dhttp3.eligibility.jmh.gcProfiler=true \
    -Dhttp3.eligibility.jmh.result=__SUPPLEMENTAL_DIR__/http3-eligibility-__ATTEMPT_ID__.json \
    -Dhttp3.eligibility.jmh.output=__SUPPLEMENTAL_DIR__/http3-eligibility-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/http3-eligibility-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

The maintained matrix covers 1000 and 10000 routes, one and eight clients, and
one and eight request-specific TLS identities per client.

### HTTP/3 DATA ingress

```shell
mvn -Ptests,jmh \
    -pl :helidon-tests-benchmark-jmh \
    -Dtest=Http3DataIngressJmhRunnerTest \
    -Dsurefire.failIfNoSpecifiedTests=false \
    -Daether.enhancedLocalRepository.split=true \
    -Daether.enhancedLocalRepository.localPrefix=__LOCAL_PREFIX__ \
    -Daether.enhancedLocalRepository.remotePrefix=__REMOTE_PREFIX__ \
    '-Dhttp3.data.ingress.jmh.include=^io\.helidon\.http\.http3\.Http3DataIngressJmhBenchmark\..*$' \
    -Dhttp3.data.ingress.jmh.forks=3 \
    -Dhttp3.data.ingress.jmh.threads=1 \
    -Dhttp3.data.ingress.jmh.warmupIterations=5 \
    -Dhttp3.data.ingress.jmh.warmupMillis=1000 \
    -Dhttp3.data.ingress.jmh.measurementIterations=10 \
    -Dhttp3.data.ingress.jmh.measurementMillis=1000 \
    -Dhttp3.data.ingress.jmh.gcProfiler=true \
    -Dhttp3.data.ingress.jmh.result=__SUPPLEMENTAL_DIR__/http3-data-ingress-__ATTEMPT_ID__.json \
    -Dhttp3.data.ingress.jmh.output=__SUPPLEMENTAL_DIR__/http3-data-ingress-__ATTEMPT_ID__.log \
    -l __OPERATOR_DIR__/http3-data-ingress-__ATTEMPT_ID__-maven.log \
    clean test -ntp
```

This covers ten heap/slice/materialization/fallback methods at 1 KiB, 16 KiB,
and 1 MiB.

Three maintained benchmark classes currently have no dedicated safe runner:
`QuicNullContractJmhBenchmark`, `QuicServerIngressJmhBenchmark`, and
`QuicStreamIngressJmhBenchmark`. The legacy runner does not select them. Do not
claim that they ran, and do not add a runner during this campaign. Record them
as uncovered benchmark sources; their functional behavior remains covered by
tests.

The repository also has no source-bound HTTP/3 load harness for multi-connection
or highly concurrent stream throughput, no apples-to-apples HTTP/1.1 or HTTP/2
end-to-end control for the small-write paths, and no retained-live-heap
measurement. Record those as coverage gaps. Do not substitute an unreviewed
external load generator or infer retained heap from allocation counters during
this campaign.

## Run ordering and host stability

Use this order:

1. operator preflight and initial reactor build;
2. controlled manifest generation;
3. functional smoke;
4. lifecycle latency;
5. small-write latency;
6. adverse-network evidence;
7. unprofiled endpoint raw and endpoint capacity;
8. lifecycle and small-write CPU;
9. lifecycle and small-write allocation;
10. server admission and cap lifecycle;
11. packet protection and ACK decoding;
12. endpoint CPU and allocation; and
13. the maintained supporting hot-path suite.

Let the host return to its predeclared idle and thermal state between long
invocations. Do not run an unrelated CPU burner or use arbitrary warmup results;
the maintained JMH warmups are the workload warmup.

If comparing two source identities in a future campaign, use separate
controlled roots and immutable prefixes, then alternate complete invocations in
an ABBA order for each mode. Do not put two source identities into one
controlled root.

## Environment evolution and future comparisons

Kernel, JDK, microcode, firmware, OS-library, Maven, and dependency security
updates are expected. The baseline strategy must accommodate them rather than
delay them.

Use these comparison classes:

1. **Same-stratum source comparison** — build and measure reference source A
   and candidate source B on the same currently patched host environment. This
   is the only class that supports a source-regression percentage.
2. **Environment bridge** — measure the same source on environment X and
   environment Y. This describes the observed environment shift, but it is not
   a correction coefficient and must not be subtracted from later results.
3. **Cross-stratum history** — retain absolute historical results, annotated
   with their environment IDs. Use them for trend discovery only; do not
   calculate a source-regression percentage across strata.

For every future source comparison:

1. install all required security updates first;
2. select and record reference commit A and candidate commit B;
3. verify that both sources and both resolved dependency graphs satisfy the
   current security policy and build unmodified on the current supported
   toolchain;
4. give each source a clean checkout, controlled root, immutable Maven prefix,
   manifest set, and source ID;
5. use one shared environment-stratum ID and the same boot, host controls,
   affinity, NUMA policy, JDK, and Maven;
6. prove that the benchmark harness and per-mode parameters are identical as
   described below;
7. run functional smoke for both sources;
8. alternate complete measured invocations A-B-B-A for each decision-bearing
   mode, using a fresh attempt and run ID every time; and
9. report the predeclared block ratios, intervals, and raw distributions within
   that stratum.

For each compared mode, create a relative-path, content-digest harness manifest
for both sources. It must cover:

- the benchmark class and dedicated runner;
- every custom profiler, process fixture, result parser, evidence publisher,
  and helper that affects selection, execution, metrics, or validation;
- the relevant benchmark-module POM and effective JMH artifact/version;
- the exact include expression, benchmark registry entries, mode, parameters,
  profilers, fork count, thread count, iteration timing, and fork JVM
  arguments.

The A and B harness manifests must be byte-for-byte identical. Production
source and its controlled dependency closure may differ; harness source,
semantics, and settings may not. If the candidate changes any covered harness
input, classify that mode `no_direct_comparison` and establish a new baseline.
Do not patch either measurement checkout to manufacture equality.

### ABBA estimator

One independent block is four complete invocations in this order:
`A1-B1-B2-A2`. A failed or rejected invocation rejects the whole block; retain
it, then start a fresh block. Collect at least three complete blocks for a
comparison claim and prefer five. One or two blocks are descriptive only and
must be classified `insufficient_comparison_repeats`.

For each benchmark cell and each predeclared positive-valued metric, take the
reported per-invocation value from the JSON. JMH forks and measurement
iterations are nested evidence within that invocation; do not count them as
independent blocks. For latency, CPU, and allocation, compute the block log
cost ratio:

```text
L[i] = (ln(B1) + ln(B2) - ln(A1) - ln(A2)) / 2
```

For higher-is-better throughput, reverse A and B in that formula so positive
values still mean regression. Across `n` complete blocks, report:

```text
cost ratio = exp(mean(L))
regression percent = 100 * (cost ratio - 1)
95% interval = exp(mean(L) +/- t[n-1,0.975] * sampleStdDev(L) / sqrt(n))
```

Convert both interval endpoints to percentages using the same formula. Apply
this independently to every scenario, parameter cell, percentile, and
secondary metric; do not pool cells or select a favorable percentile after
measurement.

For a metric that can be zero or signed, including an
adverse-minus-control delta, use the within-block arithmetic difference between
the mean of B1/B2 and the mean of A1/A2. Report the mean block difference and
its Student-t 95% interval, not a percentage or log ratio. Always retain and
show the four raw invocation values and fork distributions behind each block.

The reference should normally be the last accepted release or performance
checkpoint relevant to the candidate. Rebuild it from source on the current
secure environment; never reuse old binaries or old numbers as the comparison
side. If A no longer builds unmodified on the current supported toolchain,
or if its source-controlled dependency graph no longer meets the security
policy, select and justify the newest relevant secure, buildable reference or
establish B as a new baseline. Report `no_direct_comparison` rather than
running vulnerable software, patching only the benchmark copy of the
reference, or deriving a percentage from another environment.

An environment bridge is optional. Run one only when both environments are
currently approved and secure, such as immediately around a planned update.
Never reinstall or boot a superseded vulnerable environment to fill a missing
bridge. If a security update, reboot, package change, microcode change, or
effective configuration change occurs after measurements begin, preserve the
partial campaign, classify it `environment_changed`, update the machine, create
a new campaign and environment ID, and rerun both A and B there.

For this first campaign there is no earlier authoritative Linux reference.
Classify it `baseline_establishment`. Its results become historical evidence,
but a later candidate must still rerun the chosen reference source on the
later, fully patched environment.

## Validity and abort rules

Correctness and evidence-integrity gates are hard failures. Reject the complete
affected run if any of the following occurs:

- source, Git status, manifest, build log, controlled repository, artifact, or
  classpath identity changes;
- a controlled result lacks one of its six files;
- an accepted or unclassified attempt has a surviving reservation marker;
- JSON is missing, truncated, or unparsable;
- a protocol, benchmark assertion, accounting, cleanup, or publication
  invariant fails;
- a child JVM or UDP proxy survives its bounded cleanup;
- a timeout or profiler failure occurs;
- endpoint duplicate, cross-window, outside-window, or acceptance accounting
  is nonzero;
- adverse-network proxy overflow is nonzero or a requested impairment is not
  observed;
- CPU affinity, NUMA placement, governor, turbo, SMT, socket limits, or the
  static inherited JVM environment changes during the campaign;
- a run's profiler, fork JVM arguments, or benchmark settings differ from its
  recorded predeclared mode;
- the environment manifest or its kernel, JDK, package, firmware, provider, or
  controlled-build inputs change during the campaign;
- thermal throttling or a material concurrent host workload is observed; or
- the operator cannot explain a host interruption that overlaps a run.

Do not remove individual outlier samples. One-PTO lifecycle samples are real
protocol observations unless the whole run is proven environmentally invalid.
If an environmental disturbance invalidates a run, preserve its artifacts,
mark the entire run ID rejected, and use a new run ID.

Do not broadly kill Java or Maven processes. Identify exact descendant PIDs and
preserve any retained source snapshot or Maven staging prefix when cleanup was
not proved. Never overwrite a complete or quarantined bundle.

Any source or build-input change requires a new campaign directory, controlled
identity, and immutable prefix. A profiler-only failure does not invalidate an
already complete unprofiled run, but the failed profiler run remains rejected.

## Analysis rules

### Controlled product evidence

For QUIC lifecycle latency, report each scenario's fork distribution, p50, p95,
p99, maximum, and count of approximately one-PTO recovery samples. Keep cold
and resumed results separate and retain all Retry scenarios.

For HTTP/3 small writes, report p50, p95, p99, and maximum by path and shape.
Report process CPU and allocation separately. Do not compare the primary score
from a profiled run with the unprofiled SampleTime score.

For adverse network, report the paired adverse-minus-control exchange deltas,
bootstrap 95% intervals, proxy counters, completion counts, and stalled-close
results. Do not establish a portable network SLA from loopback impairments.

The following predeclared thresholds are investigation triggers for a future
same-stratum, same-host, same-harness source comparison:

- lifecycle or small-write median or p95 increase above 20%;
- process CPU ns/op or allocation-capacity B/op increase above 20%; and
- adverse-network paired p95 delta increase above 25%.

They are not automatic product failures. Evaluate absolute magnitude, fork
distributions, confidence intervals, profiler overhead, protocol events, and
host context.

### Endpoint ingress

The open-loop primary score is offer-plus-drain duration, not capacity. Use the
secondary planned, accepted, routed, missing, late, scheduler, socket-buffer,
CPU, and delivered-latency metrics.

For each packet size and target rate, report:

- accepted and routed packets/s;
- routed payload Gbit/s;
- median and maximum missing-at-drain percentage;
- scheduler-skipped and socket-rejected percentages;
- late percentage;
- delivered p50 and p99, with censoring clearly marked;
- effective receive and send socket buffers;
- process CPU cores from the separate CPU run; and
- raw-versus-endpoint routed-rate gap only in a clean raw region.

If the highest tested row stays clean, report a lower bound on tested capacity,
not a ceiling.

### Packet protection and ACK decoding

Report header-protection latency and normalized allocation by cipher,
legacy/packed mask, and thread-local/shared state. Each header result file must
contain exactly 8 cells. Report packet/body latency and normalized allocation
by cipher, datagram size, operation, and thread-local/shared state. Each
packet/body result file must contain exactly 48 cells. The corresponding
profiled files have the same cell counts; profiler secondary metrics do not add
cells. A missing or failed family makes the required packet-protection matrix
incomplete.

Report buffer-shape results separately. Compare safe heap/direct shapes within
the same execution. Keep authentication-failure cost separate from successful
decryption.

For ACK decoding, report throughput and normalized allocation by scenario for
configured and unbounded modes. Verify that configured excess rejection does
not allocate in proportion to excess range count or repeated excess frames.

### Supporting microbenchmarks

Use supporting results to explain scaling, allocation, and contention. Preserve
their scope:

- deadline batches exclude complete connection cleanup;
- listener reservation excludes ingress, TLS, and listener work;
- synthetic establishment is not a valid handshake;
- route lifecycle excludes route locks, maps, timers, and admission;
- path benchmarks are isolated established-path operations;
- eligibility is shared-cache lookup/cardinality; and
- DATA ingress is framing/buffer consumption, not an end-to-end exchange.

Do not turn an isolated microbenchmark improvement into an end-to-end product
claim.

## Final report

Create `__OPERATOR_DIR__/REPORT.md` with these sections:

1. **Verdict** — whether the campaign is complete and suitable as the first
   authoritative Linux baseline, plus one of `baseline_establishment`,
   `same_stratum_comparison`, `environment_bridge`, `environment_changed`, or
   `no_direct_comparison`.
2. **Source identity** — HEAD, status digest, source/build manifest digests,
   controlled repository digest, immutable and remote prefixes, plus compared
   harness-manifest digests when applicable.
3. **Environment identity and controls** — environment-stratum digest, Linux,
   kernel, package-inventory digest, CPU, microcode, firmware, NUMA, CPU set,
   SMT/turbo/governor, memory, JDK/runtime-image digests, effective Java
   security settings and initialized packet-protection providers, Maven,
   socket limits, and thermals.
4. **Run inventory** — every run ID, exact command, start/end time, duration,
   exit status, accepted/rejected classification, environment/source/build/run
   identity, ABBA block position when applicable, and artifact paths.
5. **Validity gates** — bundle counts, reservation checks, identity checks,
   JSON checks, accounting, cleanup, and host-stability result.
6. **QUIC lifecycle** — scenario latency, CPU, allocation, protocol outliers,
   admission, rejection, and cap-wide results.
7. **HTTP/3** — small-write and adverse-network tables.
8. **QUIC hot paths** — endpoint ingress, packet protection, ACK decode, route,
   path, and other supporting tables.
9. **Investigation triggers** — any threshold or qualitative concern, without
   making an automatic product-failure claim.
10. **Anomalies and rejected runs** — include all discarded run IDs and the
    complete reason.
11. **Comparability and limitations** — comparison class, reference and
    candidate IDs where applicable, environment boundaries, loopback, one
    Linux host/JDK/provider/CPU topology, no physical network, no concurrent
    HTTP/3 load or cross-protocol control, no retained-live-heap measurement,
    benchmark classes without a dedicated runner, and the fact that actual TLS
    service-provider selections are not independently traced.
12. **Archive plan** — the exact campaign root and intended detached archive,
    checksum, and size-file paths. The final archive digest and size cannot be
    embedded in a report that the archive itself contains.

Attach or retain the raw fork data and percentile distributions. Do not publish
only aggregate point estimates.

## Archive and handoff

After all runs:

1. set the environment tokens to their `environment-after` values and repeat
   every operator, package-inventory, Java properties/security, runtime-image,
   packet-protection-provider, firmware, and static-control capture into new
   sidecars;
2. verify HEAD and Git status are unchanged;
3. build and sort `environment-after.manifest`, compare it byte-for-byte with
   `environment-before.manifest`, and verify their identity digests match;
4. verify exactly seven designated accepted controlled six-file bundles and
   hash and classify every additional complete or partial attempt;
5. verify no accepted or unclassified attempt has a reservation marker or
   staged file; retain and inventory markers belonging to rejected attempts;
6. parse every JSON file;
7. record SHA-256 for all manifests and result files;
8. finish `REPORT.md`;
9. archive exactly the campaign root; and
10. create detached SHA-256 and byte-size files beside the archive.

Create the archive outside `__CAMPAIGN_DIR__` so it does not include itself.
For example, after replacing the tokens:

```shell
tar --create --gzip --file="__CAMPAIGN_DIR__.tar.gz" --directory="__CAMPAIGN_PARENT__" "__CAMPAIGN_BASENAME__"
sha256sum "__CAMPAIGN_DIR__.tar.gz" > "__CAMPAIGN_DIR__.tar.gz.sha256"
stat --format='%s' "__CAMPAIGN_DIR__.tar.gz" > "__CAMPAIGN_DIR__.tar.gz.size"
```

Retain the uncompressed campaign directory and exact immutable Maven prefix
until another reviewer has verified the archive, source/build manifests,
bundle inventory, report, detached checksum, and detached size. Cleanup, if
later authorized, must target only the manifest-recorded immutable prefix and
exact campaign root.

Do not make a code change, production recommendation, or publication from the
benchmark session itself. Return the report and archive digest for review.
