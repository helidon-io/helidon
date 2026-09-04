
# Changelog

All notable changes to this project will be documented in this file.

For previous releases of Helidon please see:

* [Helidon 4.x CHANGELOG.md](https://github.com/helidon-io/helidon/blob/helidon-4.x/CHANGELOG.md)
* [Helidon 3.x CHANGELOG.md](https://github.com/helidon-io/helidon/blob/helidon-3.x/CHANGELOG.md)
* [Helidon 2.x CHANGELOG.md](https://github.com/helidon-io/helidon/blob/helidon-2.x/CHANGELOG.md)
* [Helidon 1.x CHANGELOG.md](https://github.com/helidon-io/helidon/blob/helidon-1.x/CHANGELOG.md)

## [27.0.0-M1]

This is a major release of Helidon. Helidon 27 is the first Helidon release to fully adopt the 
JDK [Tip & Tail](https://openjdk.org/jeps/14) software development model. 

1. Helidon releases will be aligned with JDK releases: For every JDK feature release there will be a corresponding Helidon feature release.
2. Helidon feature releases will bump the minimum Java requirement to match the corresponding JDK release.
3. Only bug fixes go into sustaining tails for feature releases. New features go into new feature releases.
4. Helidon LTS releases will align with Oracle JDK LTS releases. 
   - Helidon 4 is considered an LTS release.
   - Helidon 29 will be the next LTS release.

A minimum of Java 27 is required to use Helidon 27.

### NOTABLE CHANGES

1. MicroProfile support has been moved from the core Helidon repository into its own project. [Helidon MicroProfile](https://github.com/helidon-io/helidon-microprofile)
   is not part of the Helidon 27 release. It will release independently in the near future.
2. Extensions have been moved into the [Helidon Extensions](https://github.com/helidon-io/helidon-extensions) repository and each extension will have its own release lifecycle and be released independently.
3. Helidon Declarative support is largely feature complete. It is still a Preview feature but we expect the API to remain fairly stable.
4. Helidon 27 contains multiple third party dependency upgrades. Some that might impact backwards compatibility:
   * OpenTelemetry 1.65.0
   * Micrometer 1.17.1

[27.0.0-M1]: https://github.com/oracle/helidon/compare/main...27.0.0-M1

