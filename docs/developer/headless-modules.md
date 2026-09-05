# Headless module split

This document is the migration contract for the independent `easyyapi-headless`
repository. The goal is to preserve EasyYapi's PSI-based API extraction while
removing GUI-only dependencies from the headless build surface.

## Target modules

```text
:headless-core
  API models, PSI/type resolution, rule engine, framework recognizers,
  endpoint scanning, export pipeline, formatters, and headless channel SPI.

:headless-cli
  IntelliJ Platform bootstrap, Maven import, temporary SDK handling,
  YApi/OpenAPI command execution, and static API Preview rendering.

:idea-plugin
  Settings UI, API Dashboard UI, actions, tool windows, gutter providers,
  editor integration, and IDE-only request interaction.
```

`headless-core` is an IntelliJ Platform-backed module rather than a plain JVM
library because API scanning intentionally uses the PSI/index APIs. It must,
however, be independent of Swing,
IDEA actions, tool windows, Settings panels, dialogs, clipboard access, and
Dashboard state.

The first implementation-owned contracts are now in `headless-core`: strict
standalone options, preview artifacts/models, export result/metadata/extension
carriers, `ChannelConfig`, `ExportContext`, `PathSelector`, IntelliJ coroutine
dispatchers, the IDEA logger contract, `RuleKey`/`RuleModes`, PSI source
resolution, and PSI type resolution. The main plugin imports these contracts
but no longer owns their implementation classes. The Standalone launcher,
temporary SDK handling, Preview renderer, and their tests physically belong to
`headless-cli`; project configuration and export execution are supplied by the
IDEA plugin's `HeadlessRuntimeProvider`. The old root `sourceSets` bridge has
been removed.

## Phase-two checkpoint

The repository is ready to continue the second-stage module split. The current
checkpoint is intentionally incremental:

| Boundary | Current state | Verification |
|---|---|---|
| `headless-core` contracts | Independently compiled contract module | `:headless-core:compileKotlin` |
| Core export primitives | `ChannelConfig`, `ExportContext`, `PathSelector`, `HeadlessChannel`, API models, exporter SPI | `:headless-core:compileKotlin` |
| `headless-core` PSI/type base | Compiled with IntelliJ Java/Kotlin PSI dependencies and consumed by both modules | `:headless-core:compileKotlin` |
| `headless-cli` launcher/Preview | Independently compiled IntelliJ Platform plugin module | `:headless-cli:compileKotlin :headless-cli:compileTestKotlin` |
| Main plugin JAR | Does not contain the CLI launcher/Preview implementation; provides the single bundled Core runtime | inspect `build/libs/*.jar` |
| Standalone distribution | Bundles the main plugin plus the CLI plugin | `packageStandalone` |
| Real Spring Boot Maven project | Preview generated with 59 endpoints, including URL search entries | smoke run against `panther-agent-hub` |

`headless-cli` no longer has a Gradle dependency on `idea-plugin`. Its packaged
plugin still declares a runtime dependency on the IDEA plugin, which registers
the `HeadlessRuntimeProvider` implementation through the Core extension point.
The PSI scanner, rule engine, framework exporters, and channel implementations
remain provider-side until their own extraction is complete.

## Dependency direction

```text
headless-core  <---  headless-cli
headless-core  <---  idea-plugin
```

The CLI has no compile-time dependency on `idea-plugin`; the runtime dependency
is intentional for the provider registration described above. Shared
contracts belong in `headless-core`, while concrete PSI/settings/channel
implementations remain on the provider side during migration.

## Remaining extraction blockers

The physical Gradle modules and CLI distribution now exist, and the CLI has no
Gradle dependency on the IDEA plugin. The following items remain before
`headless-core` can become the implementation owner rather than a contract and
infrastructure boundary:

| Current location | Problem | Target boundary |
|---|---|---|
| `core.dashboard.ApiScanner` | Combines endpoint scanning, selection filtering, `ApiIndex`, and IDE progress | `core.scan.ApiScanner` plus an IDEA selection/cache adapter |
| `core.ide.DumbModeHelper` | Contains both silent readiness waits and user notifications | Headless readiness helper plus IDEA notification adapter |
| `core.psi.DefaultPsiClassHelper` | Uses the migrated type system but remains in the plugin because it also owns IDE/project integration | Headless PSI class model plus an IDEA project adapter |
| `channel.spi.Channel` | Channel contract inherits `SettingsPanelProvider` | GUI-free `Channel` contract plus IDEA settings extension |
| `core.logging.console` | Uses project console and IDEA logging | Logger contract with CLI and IDEA implementations |
| `core.settings.SettingBinder` | Configuration model and persistence are coupled to project services | Headless configuration reader plus IDEA state adapter |
| `core.dashboard.RequestExecutor` | Request execution and Dashboard editing are coupled | Optional IDEA-only request module |

## Migration order

1. Add GUI-free contracts for scanning, logging, progress, and channels.
2. Keep compatibility adapters in the current packages so the IDEA plugin
   remains behavior-compatible during migration.
3. Introduce `:headless-core` as the implementation owner and move the
   extracted PSI/rules/framework/export packages into it incrementally.
4. Keep `:headless-cli` as the standalone owner; its launcher and tests are
   physically isolated from the main plugin JAR and invoke the Core runtime
   provider through the EP.
5. Move the remaining plugin source/resources to the IDEA plugin boundary.
6. Remove compatibility adapters only after the plugin and Standalone smoke
   tests pass from a clean checkout.

## Acceptance criteria

- `./gradlew test` remains green for the IDEA plugin.
- `./gradlew packageStandalone` produces a runnable distribution.
- A clean Headless invocation scans Java Spring Boot Maven multi-module projects
  without opening an IDEA window.
- Preview output remains a self-contained static `index.html`.
- Headless modules contain no imports from `core.settings.ui`, IDEA dialogs,
  Swing UI, actions, tool windows, or Dashboard presentation classes.
- Standalone does not persist its temporary JBR into the source project's IDEA
  metadata.
