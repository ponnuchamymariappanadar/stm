# Contributing

Thanks for your interest in improving STM. Bug reports, documentation fixes and pull requests are all welcome.

## Reporting a bug

Open an issue with:

- what you expected and what happened instead,
- the smallest configuration (XML/YAML) and actor code that shows the problem,
- your Java version and how you build the machine (plain builder or Spring).

A failing JUnit test is the best possible bug report.

## Development setup

Requirements: Java 17+ and Maven 3.9+.

```bash
git clone https://github.com/ponnuchamymariappanadar/stm.git
cd stm
mvn verify
```

`mvn verify` compiles, runs all tests and writes a coverage report to `target/site/jacoco/index.html`.

### Importing into an IDE

- **Eclipse**: *File → Import → Existing Maven Projects*. Install Lombok into Eclipse
  (`java -jar lombok.jar`) so the generated getters and setters are visible.
- **IntelliJ IDEA**: open `pom.xml` as a project and enable annotation processing.

## Project layout

| Path | Contents |
|---|---|
| `src/main/java/com/easysoft/stm` | The engine: states, events, transitions, contexts, tracing |
| `src/main/java/com/easysoft/stm/config` | XML/YAML readers, the builder, Spring integration |
| `src/main/java/com/easysoft/stm/actors` | Built-in actors (Call/Return, no-op) |
| `src/test/java/...` | JUnit 5 tests; `TestContext` and `FlowActions` are the shared fixtures |
| `src/test/resources/stm` | Example flows used by the tests and the README |

## Pull requests

1. Create a branch from `main`.
2. Add or update tests. Bug fixes should come with a test that failed before the fix.
3. Run `mvn verify` and make sure it passes.
4. Keep changes focused. Separate refactoring from behaviour changes where you can.
5. Update the README if you change configuration syntax or public behaviour, and add an entry to
   [CHANGELOG.md](CHANGELOG.md).

## Code style

- Follow the style of the surrounding code (tabs for indentation in Java sources).
- Prefer clear names over comments; add a comment when the *why* is not obvious.
- Builder errors should throw `STMException` with a message that names the file, state and event involved.

## License

By contributing you agree that your contributions are licensed under the [Apache License 2.0](LICENSE).
