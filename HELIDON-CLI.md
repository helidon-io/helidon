# Helidon CLI

The Helidon CLI lets you easily create a Helidon project by picking from
a set of archetypes.

It also supports a developer loop that performs continuous compilation and
 application restart, so you can easily iterate over source code changes.

## Prerequisites

Helidon requires Java 21 (or newer) and Maven 3.6.1 or newer.

* [Java SE](https://www.oracle.com/technetwork/java/javase/downloads)
* [Maven](https://maven.apache.org/download.cgi)

You should make sure `java` and `mvn` are in your path.

```bash
java -version
mvn --version
```

## Install

MacOS:
```bash
curl -O https://helidon.io/cli/latest/darwin/helidon
chmod +x ./helidon
sudo mv ./helidon /usr/local/bin/
```

Linux:
```bash
curl -O https://helidon.io/cli/latest/linux/helidon
chmod +x ./helidon
sudo mv ./helidon /usr/local/bin/
```

Windows:
```bat
PowerShell -Command Invoke-WebRequest -Uri "https://helidon.io/cli/latest/windows/helidon.exe" -OutFile "C:\Windows\system32\helidon.exe"
```

## Create a New Project

```
helidon init
```

Then answer the questions.

## Developer Loop

```
cd myproject
helidon dev
```

As you make source code changes the project will automatically recompile and
restart your application.

## Demo

<p align="center">
    <img src="etc/images/Helidon_cli.gif">
</p>
