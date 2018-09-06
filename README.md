<p align="center">
    <img src="./etc/images/Primary_logo_blue.png" height="250">
</p>
<p align="center">
    <a href="https://github.com/oracle/helidon/tags">
        <img src="https://img.shields.io/github/tag/oracle/helidon.svg" alt="latest version">
    </a>
    <a href="https://github.com/oracle/helidon/issues">
        <img src="https://img.shields.io/github/issues/oracle/helidon.svg" alt="latest version">
    </a>
    <a href="https://app.wercker.com/project/byKey/de00e8ec6178ba9a2db8ee863d5c568a">
        <img src="https://app.wercker.com/status/de00e8ec6178ba9a2db8ee863d5c568a/s/master" alt="build status">
    </a>
    <a href="https://twitter.com/intent/follow?screen_name=helidon_project">
        <img src="https://img.shields.io/twitter/follow/helidon_project.svg?style=social&logo=twitter" alt="follow on Twitter">
    </a>
</p>

# Helidon: Java Libraries for Microservices

Project Helidon is a set of Java Libraries for writing microservices.
Helidon supports two programming models:

* Helidon MP: MicroProfile 1.1 plus Health Check and Metrics
* Helidon SE: a small, functional style API

In either case your application is just a Java SE program.

## Documentation

Latest documentation and javadocs are available at <https://helidon.io/docs/latest>.

## Get Started

See Getting Started at <http://helidon.io>.

## Bugs and Feedback

Issues are currently tracked in GitHub, see <https://github.com/oracle/helidon/issues>

## Communication

* Slack: Coming soon
* Twitter: [@helidon_project](https://twitter.com/helidon_project)

## Downloads / Accessing Binaries

There are no Helidon downloads. Just use our Maven releases (GroupID `io.helidon`).
See Getting Started at <http://helidon.io>. 

## Build

You will need Java 9 and Maven 3.5 or newer.

**Full build**
```bash
$ mvn install
```

**Checkstyle**
```bash
# Cd to the component you want to check
$ mvn validate  -Pcheckstyle
```

**Copyright**

```bash
# Cd to the component you want to check
$ mvn validate  -Pcopyright
```

**Spotbugs**

```bash
# Cd to the component you want to check
$ mvn verify  -Pspotbugs
```
