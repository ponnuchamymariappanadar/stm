# Known issues

There are no open known issues. Fixed issues are listed in [CHANGELOG.md](CHANGELOG.md).
Please report new problems on the [issue tracker](https://github.com/ponnuchamymariappanadar/stm/issues).

## Behaviour worth knowing

- When `<STM>` has no `start`/`fire`, the default start event is the first `<Transition>` of the
  first state. `<Call>` and `<Return>` elements are skipped, so a flow whose first step is a
  `<Call>` needs explicit `start`/`fire`.
- A transition with no `<OnError>` route at all (possible only when building transitions by hand)
  continues to its normal `fire` after an exception.
- `STMFactory` keeps machines in a static, JVM-wide map keyed by name.
- Actors with the same `beanType` and `<Property>` values are registered as one Spring bean, even
  when they are declared in different files.
