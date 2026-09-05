# Standalone EasyYapi (Platform 252)

This distribution runs EasyYapi on a bundled IntelliJ Platform and JetBrains Runtime,
without an IDE window or a separately installed IDEA. The initial supported scope
is **Java + Maven + Spring MVC**, with OpenAPI JSON/YAML export and YApi upload.
It retains the platform core JARs, Java, Maven and their selected supporting plugins.
It does not include Kotlin, Scala, Gradle import, a desktop UI or an MCP server yet.
This is a version-pinned runtime adapter, not a portable standalone PSI library.

## Build

```sh
./gradlew packageStandalone
```

The build requires Python 3; the packaged command needs only its bundled JBR and a
POSIX shell. The distribution matches the build host (macOS or Linux and CPU
architecture). Windows packaging is not implemented. The destination defaults to
`build/standalone/easyyapi`; use `-PstandaloneOutput=/absolute/new/directory` for a
different destination. Existing destinations are refused instead of overwritten.

## Export

```sh
build/standalone/easyyapi/bin/easyyapi --help
build/standalone/easyyapi/bin/easyyapi \
  --project /absolute/path/to/maven-project \
  --jdk /absolute/path/to/project-jdk \
  --class com.example.UserController \
  --channel openapi --format json \
  --output /absolute/path/to/new-openapi.json
```

Omit `--class` to scan the project; repeat it to select several classes. The command
imports `pom.xml`, temporarily configures the project SDK, resolves dependencies,
waits for indexing, and scans with the existing rule/export pipeline. A previously
unopened project is supported. Maven import may create or update `.idea` project
metadata, but the original project SDK is restored before Standalone exits. The
project JDK defaults to the bundled runtime; pass `--jdk` for projects requiring
another version. Maven uses its normal settings/repositories, including `~/.m2`.
Pass `--maven-settings /absolute/path/to/settings.xml` to use a different Maven
configuration without editing the global one. Selecting a channel enables it in
the isolated standalone settings unless that channel was explicitly disabled.
Pass `--idea-config /absolute/path/to/IDEA/config` to read only the Yapi settings
from `options/easyapi_app.xml` in an existing IDEA installation. The source file
is read-only; imported values are stored only in the isolated standalone state.
Only use project directories whose Maven configuration you intend to import.

Each invocation starts a process and closes its project on completion. Platform
cache/config/log directories are isolated from IDEA under
`~/.easyyapi/standalone-252`. Set `EASYYAPI_STATE_HOME` to isolate concurrent runs.
Persistent caches are reused; this version is not a resident daemon. `--timeout`
defaults to 600 seconds and covers import, indexing and export. Exit code 0 means
success; nonzero means failure. Diagnostics go to stderr and the state's
`log/idea.log`; exported content goes to `--output`. Existing outputs are refused.

## YApi

Set `EASYYAPI_YAPI_SERVER` and `EASYYAPI_YAPI_TOKEN` in the process environment, then
use `--channel yapi --output /absolute/path/to/new-result.json`. Credentials are
not accepted as command-line arguments or persisted by this adapter. Existing
EasyYapi configuration may also supply the server and module tokens. The output
file contains the upload result, not an OpenAPI document. Upload uses the existing
YApi update policy (default: update matching APIs); `ALWAYS_ASK` is rejected before
upload because headless execution cannot ask about conflicts. An unsuccessful
upload may have partially changed YApi; inspect diagnostics before retrying.

## Smoke test

```sh
python3 script/smoke-standalone.py build/standalone/easyyapi/bin/easyyapi
```

The test creates a fresh multi-module Maven project, configuration directory and
local Maven repository. It downloads Spring from Maven Central, verifies a generic
response wrapping a DTO from another module, edits that DTO outside the runtime,
and verifies the next export sees the new field. It also exercises class-not-found
and missing-server failures, and uploads to a loopback-only mock YApi server. No
real YApi instance is modified. Logs and a verification report are retained in the
printed temporary directory. This test is intentionally separate from unit tests
because it starts the packaged JBR and requires repository access.

## Architecture and validation

`StandaloneStarter` owns process/project lifecycle and Maven import.
`StandaloneExportService` resolves the channel through `ChannelRegistry`, respects
channel enablement, and uses `ApiScanner` without relying on dashboard caches.
`HeadlessChannel` is an explicit SPI contract: unsupported channels cannot
accidentally open dialogs. OpenAPI chooses a concrete serialization format and
returns text. YApi resolves credentials noninteractively and returns its result.

To extend the runtime, first add a real headless integration scenario; do not
assume adding a plugin directory establishes project import readiness. Platform
upgrades must revalidate the starter, Maven APIs, required plugin list and startup.
The runtime manifest records the selected version, architecture and plugins.
Core runtime files still contain IDE infrastructure; this is not a minimal-JAR
build and makes no fixed memory or package-size guarantee.

Preserve upstream license/notice files. This build produces a local experimental
distribution from the pinned Community runtime; public redistribution, branding,
signing and installers require a separate release review of the actual components.

## Verified baseline (2026-09-05)

On macOS ARM64, the packaged Platform 2025.2.1 / build 252.25557.131 passed all
five smoke scenarios above and 33 targeted tests (options, headless OpenAPI,
YApi settings, console routing, notifications and logging gates). The fresh smoke
project took 33.49 seconds including Maven downloads and first indexing; the
second invocation with an edited DTO took 7.88 seconds. These numbers describe the
small smoke reactor, not a performance guarantee for production projects.
The uncompressed runtime directory was approximately 1.5 GB. Linux packaging is
implemented but was not executed in this validation; Windows is unsupported.
