# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [0.1.0] - 2026-11-01

### Added
- JUnit 5 test suite covering the engine, transition types, conditions, case routing, call/return
  sub-flows, error handling, tracing, the XML and YAML readers, multi-file configuration, builder
  validation, `STMFactory`/`StmStarter`, and Spring bean registration.
- Example order-processing flow in XML and YAML (`src/test/resources/stm/`), used by the README.
- README, CONTRIBUTING, KNOWN_ISSUES, Apache 2.0 LICENSE, GitHub Actions CI, JaCoCo coverage report.

### Fixed
- `setDebug(false)` now turns debug mode off (`getDebug()` was stuck at `true`).
- The end transition set with `setEndTransition` now runs once after the flow, instead of re-running the start transition.
- `startWithNewSTM` now continues in the new machine at the given transition, instead of re-running the current one.
- A global `<OnError>`/`<OnFailedCondition>` in a main file that only includes other files is now applied.
- `scope="prototype"` is honoured for registered actor beans.
- Result transitions are no longer replaced by the error transition during reduction.
- The build log now prints the real failed-condition route.
- `PassCondition` now returns `true` and `FailCondition` returns `false`, as their names say (they were swapped).
- Builders can run in parallel: shared counters and the bean-name map are now thread-safe.

### Removed
- Reactive (asynchronous) transitions: the `reactive` attribute, `<ReactiveResult>`, `ReactiveMethodActor`,
  `ReactiveMethodResultActor`, `ReactiveThreadLifeCycle` and `ReactiveSTMThreadLifeCycle`.
  Configuration that uses them is now rejected at build time.
- `spring-boot-starter-web` and `spring-boot-starter-jetty` dependencies (only the reactive servlet
  code used them); replaced by `spring-boot-starter`.

### Changed
- Maven coordinates are now `io.github.ponnuchamymariappanadar:stm` (Java packages unchanged).
- Release profile and workflow for publishing to Maven Central (see PUBLISHING.md).
- All packages consolidated under `com.easysoft.stm` (`actors`, `condition`, `config`). Update
  `className`/`beanType` values in existing configuration files.
- Author set to Ponnuchamy Mariappanadar.
- `pom.xml`: project metadata, `junit-jupiter` test dependency, explicit Lombok annotation
  processing, JaCoCo plugin, Java 17 baseline.
- Removed old tests that depended on classes not included in this repository.

## [0.0.1] - Initial version
