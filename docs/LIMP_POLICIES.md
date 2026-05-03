# LIMP Policies

LIMP means Lightweight Implementation Modularity Policies. These rules keep Time Tracker simple, modular, and easy to verify.

The project should prefer standard Kotlin and Android tooling before custom checks:

- Spotless with ktlint owns Kotlin formatting.
- Detekt owns Kotlin complexity and maintainability rules.
- Android lint owns Android resource, manifest, and platform checks.
- LIMP custom checks only cover Time Tracker-specific architecture boundaries and file budgets.

## Architecture Boundaries

- `:app` wires startup, navigation, DI, app-level reset behavior, and Android entry points.
- `:feature:*` owns Compose routes, ViewModels, feature UI state, and feature events.
- `:core:common` owns pure Kotlin domain models, report math, repository contracts, and shared business interfaces.
- `:core:database` owns Room entities, DAOs, migrations, and repository implementations.
- `:core:datastore` owns DataStore-backed settings implementations.
- `:core:logging` owns structured logging, sanitization, Logcat/file sinks, and debug log-drain transport.
- `:core:location` owns location, geofence, and activity-recognition adapters behind interfaces.
- `:core:notifications` owns notification channels and notification policy helpers.
- `:core:testing` owns fakes and test utilities only.

Dependency rules:

- Feature modules may depend on `:core:common` and small adapter APIs such as `:core:location`.
- Feature modules must not depend on `:core:database`, `:core:datastore`, or `:core:testing` in production code.
- Core modules must not depend on feature modules.
- `:core:common` must stay plain Kotlin/JVM and must not import Android, Compose, Hilt, Room, DataStore, or Play services APIs.
- Cross-cutting logging should flow through `:core:logging`, not ad hoc `Log.*` calls spread through features.
- `android.util.Log` is allowed only inside `:core:logging`.
- Persistence and platform implementation details stay behind interfaces before they reach ViewModels.

## Kotlin Structure

- Kotlin files follow normal package directory structure under `src/main`, `src/test`, and `src/androidTest`.
- File names describe their contents. Avoid vague names such as `Utils.kt`.
- Related top-level declarations can share a file only while the file remains small and cohesive.
- Prefer `internal` for module-private implementation details.
- Use plain Kotlin for domain logic unless Android integration is the point of the class.

## Size And Complexity Budgets

Hard custom file budgets:

- Production Kotlin file: 260 lines.
- Feature route/screen file: 320 lines.
- Test Kotlin file: 360 lines.
- Production imports: 28 imports.
- Feature route/screen imports: 36 imports.
- Test imports: 32 imports.

Detekt budgets:

- Method length: 90 lines.
- Class length: 180 lines.
- Cyclomatic complexity: 12.
- Cognitive complexity: 15.
- Nested block depth: 4.
- Function parameters: 7.
- Constructor parameters: 8.
- Functions per file/class: 18/14.

## Escape Hatch

Do not bypass a policy silently. If a policy is too strict for a real case, update this document and the matching tool configuration in the same commit with a short reason.
