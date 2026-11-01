# Known issues

Open issues found while writing the test suite. Fixed issues are listed in [CHANGELOG.md](CHANGELOG.md).

## 1. `PassCondition` and `FailCondition` are inverted

`PassCondition` returns `false` and `FailCondition` returns `true`. Validate-only mode uses
`PassCondition` for every condition. Renaming or swapping them changes behaviour for any
configuration that references them by class name, so this is left for a deliberate decision.

## 2. Shared static state in the builder

`STMOptimizedBuilder.parameterBeanNameToBeanNameMap` and `stmIndex` are `static` and not
thread-safe. Two builders running at the same time (or in the same JVM across tests) share
bean-name mappings and ids.

## Behaviour worth knowing (not bugs)

- When `<STM>` has no `start`/`fire`, the default start event is the first `<Transition>` of the
  first state. `<Call>` and `<Return>` elements are skipped, so a flow whose first step is a
  `<Call>` needs explicit `start`/`fire`.
- A transition with no `<OnError>` route at all (possible only when building transitions by hand)
  continues to its normal `fire` after an exception.
- `STMFactory` keeps machines in a static, JVM-wide map keyed by name.
