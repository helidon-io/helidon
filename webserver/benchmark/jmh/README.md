Benchmarks
---

# JMH

JMH tests are being executed with maven profile `mvn clean install -Pjmh`, baseline result is created if
file `benchmarks/jmh/jmh-baseline.json` doesn't exist. Baseline is used for current result comparison, if regression larger than
error margin(15% by default)
is detected, build fails.

```shell
[ERROR] Failures: 
[ERROR]   JunitJMHRunnerTest.renderResult:69 
==================== HttpJMH.http2 (-31.41%)
     Baseline ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ 44877.472 ops/s
      Current ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ 30781.420 ops/s
HttpJMH.http2 regression detected. Error margin 10%(3078.14)
Expected: a value equal to or greater than <44877.471663551245>
     but: <33859.562325823645> was less than <44877.471663551245>
[INFO] 
[ERROR] Tests run: 2, Failures: 1, Errors: 0, Skipped: 0
```

## Adding JMH tests

New JMH tests classes should be created in `benchmarks/jmh/src/main/java/io/helidon/tests/benchmark/jmh/`
with simple name ending with `JMHTest`.

## Troubleshooting

When tests fails repeatedly without any code change, try regenerating baseline file
with `mvn clean install -Pjmh -Dwebserver.jmh.resetBaseline=true`. If that doesn't help, you can also set different error margin (in
percents) with
`mvn clean install -Pjmh -Dwebserver.jmh.errorMargin=15`.