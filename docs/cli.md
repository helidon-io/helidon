<!--@frontmatter
description: "Create and manage Helidon projects"
navigation:
  icon: i-lucide-terminal
-->
# Helidon CLI

The Helidon command-line interface (CLI) helps you create and manage Helidon
projects. You can use it to generate a new project, get project details, and
build your application.

The Helidon CLI also includes a development loop feature that automatically
recompiles and restarts your application, so you can iterate quickly while you
edit your code.


### Prerequisites

| Requirement                                              | Description                                        |
|----------------------------------------------------------|----------------------------------------------------|
| [Java 21][java-21] ([OpenJDK 21][openjdk-21])            | Helidon requires Java 21+ (25+ recommended).       |
| [Maven 3.8+][maven-3-8]                                  | Helidon requires Maven 3.8+.                       |
| [Microsoft Visual C++ Redistributable][microsoft-visual] | Required on Windows. Latest version is recommended |

## Install the Helidon CLI

The Helidon CLI is a standalone executable for Linux, macOS, and Windows
systems. Download the executable and install it in a location that is accessible
from your PATH.

<!--@mdc ::steps -->

### Verify your PATH

```shell [Terminal]
java --version
mvn --version
```

### Download and install

<!--@mdc ::code-group -->

```shell [Linux] <!-- @icon i-logos-linux-tux -->
curl -L -O https://helidon.io/cli/latest/linux/helidon
chmod +x ./helidon
sudo mv ./helidon /usr/local/bin/
```

```shell [macOS] <!-- @icon i-simple-icons-apple -->
curl -L -O https://helidon.io/cli/latest/darwin/helidon
chmod +x ./helidon
xattr -d com.apple.quarantine helidon
sudo mv ./helidon /usr/local/bin/
```

```cmd [Windows] <!-- @icon i-logos-microsoft-windows-icon -->
PowerShell -Command Invoke-WebRequest `
    -Uri "https://helidon.io/cli/latest/windows/helidon.exe" `
    -OutFile "C:\Windows\system32\helidon.exe"
```

<!--@mdc :: -->

### Confirm the installation

```shell [Terminal]
helidon version
```

<!--@mdc :: -->

## Usage

After you install the Helidon CLI, you can use it to manage your Helidon
projects. Some examples are provided below.

Get a list of the available commands:
```shell [Terminal]
helidon help
```

## Create a New Project

You can use the Helidon CLI to quickly create a new Helidon project.
The command is interactive by default and will prompt for various choices.

```shell [Terminal]
helidon init
```

It will create a new project folder in your current directory.

## Development Loop

You can use the Helidon CLI development loop feature to test changes to your
application as you make them.

When the development loop is active, the Helidon CLI will automatically
recompile and restart your application so you can see the effects of your
changes immediately.

```shell [Terminal]
cd myproject
helidon dev
```

To stop the development loop, enter `Ctrl+C`.

## Demo

Watch the following demo to see some of the functionality of the Helidon CLI.

![CLI Demo](images/cli/Helidon_cli.gif)

[java-21]: https://www.oracle.com/technetwork/java/javase/downloads
[openjdk-21]: http://jdk.java.net
[maven-3-8]: https://maven.apache.org/download.cgi
[microsoft-visual]: https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist?view=msvc-170#visual-c-v14-redistributable
