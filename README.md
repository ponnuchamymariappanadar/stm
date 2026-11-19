# STM: a configurable state machine for Java

[![CI](https://github.com/ponnuchamymariappanadar/stm/actions/workflows/ci.yml/badge.svg)](https://github.com/ponnuchamymariappanadar/stm/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

STM runs business flows that you describe in **YAML** and implement as **plain Java methods**.
The configuration says *what happens next*; your code only does the work for each step.

```yaml
stages:
  - name: order
    steps:
      - step: received
        then: validate
        action: { class: com.example.OrderActions, method: receive }
      - step: validate
        then: pay
        check: { class: com.example.OrderActions, method: isValid }
```

## Features

- **Flows as configuration**: stages and steps in YAML, split across files if you like.
- **Plain Java actions**: any public method `Object name(YourContext ctx)` can be a step, bound at startup through `LambdaMetafactory` (no reflection per call).
- **Checks** with a route for when they fail (`onFalse`), **outcomes** that route on a method's return value, and **sub-flows** (`runStage` steps and `returns`) you can reuse.
- **Error handling**: exceptions go to the step's own `onError` route or the one under `defaults`, with the exception available on the context.
- **Validation at startup**: missing targets, duplicate events, missing handlers, infinite placeholder loops, and unknown methods are all reported before anything runs.
- **Placeholder reduction**: steps that only forward to another step are collapsed away at build time.
- **Decision rules**: conditions, decisions and named outcomes in their own YAML files, used as checks and actions (see [Decision rules](#decision-rules)).
- **Tracing**: a debug context records every step with its result and timing in one log line.
- **Spring integration** (optional): actions referenced by `beanType` are registered as Spring beans automatically.

## Contents

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
- [Configuration reference](#configuration-reference)
- [Writing actors](#writing-actors)
- [Decision rules](#decision-rules)
- [Using Spring](#using-spring)
- [Tracing and debugging](#tracing-and-debugging)
- [Driving the machine from code](#driving-the-machine-from-code)
- [Building and testing](#building-and-testing)
- [Known issues](#known-issues)
- [Contributing](#contributing) and [License](#license)

## Requirements

- Java 17 or newer
- Maven 3.9+
- Built on Spring Boot 4.0 dependency management (Jackson YAML, SLF4J, Commons Lang, Guava, Lombok)

Add the dependency (available on Maven Central once version 0.1.0 is released; until then, build it
locally with `mvn install`):

```xml
<dependency>
    <groupId>io.github.ponnuchamymariappanadar</groupId>
    <artifactId>stm</artifactId>
    <version>0.1.0</version>
</dependency>
```

The Java packages are `com.easysoft.stm.*`.

## Quick start

The complete example below lives in the test sources and runs as part of the build
([`order-flow.yaml`](src/test/resources/stm/order-flow.yaml),
[`OrderActions`](src/test/java/com/easysoft/stm/example/OrderActions.java),
[`OrderFlowExampleTest`](src/test/java/com/easysoft/stm/example/OrderFlowExampleTest.java)).

### 1. A context for your data

Every flow runs against a context object. It carries your business data plus the engine's
`StatefulContext`:

```java
public class OrderContext implements STMContext {
    private StatefulContext stmContext;
    private final String orderId;
    private final int amount;
    private final boolean express;
    private final List<String> history = new ArrayList<>();

    public OrderContext(StatefulContext stmContext, String orderId, int amount, boolean express) { ... }

    @Override public StatefulContext getSTMContext() { return stmContext; }
    @Override public void setSTMContext(StatefulContext c) { this.stmContext = c; }

    public void record(String step) { history.add(step); }
    // getters ...
}
```

### 2. Actors: the work for each step

```java
public class OrderActions implements Actor<OrderContext> {
    public enum Shipping { STANDARD, EXPRESS }

    public Object receive(OrderContext order) {
        if (order.getOrderId() == null) throw new IllegalArgumentException("order id is required");
        order.record("received");
        return order.getOrderId();
    }

    public boolean isValid(OrderContext order) { return order.getAmount() > 0; }   // a check

    public boolean charge(OrderContext order, Map<String, String> input) {        // a check with input
        boolean approved = order.getAmount() <= Integer.parseInt(input.get("creditLimit"));
        order.record(approved ? "charged" : "charge refused");
        return approved;
    }

    public Shipping shippingMethod(OrderContext order) {                           // drives outcomes
        return order.isExpress() ? Shipping.EXPRESS : Shipping.STANDARD;
    }

    public Object shipStandard(OrderContext order) { order.record("shipped standard"); return null; }
    public Object shipExpress(OrderContext order)  { order.record("shipped express");  return null; }
    public Object reject(OrderContext order)       { order.record("rejected");         return null; }
    public Object decline(OrderContext order)      { order.record("declined");         return null; }
    public Object recordError(OrderContext order) {
        order.record("error: " + order.getSTMContext().getException().getMessage());
        return null;
    }
}
```

### 3. The flow

```yaml
name: order.flow
startStage: order
startStep: received

# Routes for every step that does not declare its own
defaults:
  onError: { nextStage: failed, then: error }
  onFalse: { nextStage: failed, then: rejected }

stages:
  - name: order
    steps:
      - step: received
        then: validate
        action: { class: com.example.OrderActions, method: receive }
      - step: validate
        then: pay
        check: { class: com.example.OrderActions, method: isValid }
      # run the payment stage as a sub-flow, then route on the step it finished with
      - step: pay
        runStage: payment
        then: start
        outcomes:
          - { when: approved, nextStage: shipping, then: choose }
        otherwise: { nextStage: failed, then: declined }

  - name: payment
    steps:
      - step: start
        then: approved
        # input passes named texts to the method: charge(order, Map<String, String> input)
        check: { class: com.example.OrderActions, method: charge, input: { creditLimit: 1000 } }
        onFalse: { nextStage: this, then: declined }
    returns:
      - step: approved
      - step: declined

  - name: shipping
    steps:
      - step: choose
        then: standard
        action: { class: com.example.OrderActions, method: shippingMethod }
        outcomes:
          - { when: EXPRESS, then: express }
      - step: standard
        then: complete
        action: { class: com.example.OrderActions, method: shipStandard }
      - step: express
        then: complete
        action: { class: com.example.OrderActions, method: shipExpress }
      - step: complete
        control: stop

  - name: failed
    steps:
      - step: error
        then: done
        action: { class: com.example.OrderActions, method: recordError }
      - step: rejected
        then: done
        action: { class: com.example.OrderActions, method: reject }
      - step: declined
        then: done
        action: { class: com.example.OrderActions, method: decline }
      - step: done
        control: stop
```

### 4. Build once, run many times

```java
STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
builder.addFile("classpath:stm/order-flow.yaml");    // or a file path; .yaml or .yml
STMStateful<STMContext> stm = builder.build();       // validates the whole flow

OrderContext order = new OrderContext(stm.createContext(), "A-1", 100, false);
order.getSTMContext().start(order);                  // runs until a step stops

order.getHistory();   // [received, charged, shipped standard]
```

The built `STMStateful` is reusable. Create a new context for every run.

| Input | History |
|---|---|
| amount 100 | `received, charged, shipped standard` |
| amount 100, express | `received, charged, shipped express` |
| amount 0 | `received, rejected` (`onFalse` under `defaults`) |
| amount 5000 | `received, charge refused, declined` (payment finishes with `declined`, caught by `otherwise`) |
| no order id | `error: order id is required` (`onError` under `defaults`) |

## Core concepts

A step reads as: *in stage **S**, at step **W**, run the action, then continue with step **F** in stage **T***.

```yaml
stages:
  - name: S
    steps:
      - { step: W, nextStage: T, then: F, action: { ... } }
```

| Concept | Meaning |
|---|---|
| **Stage** | A named group of steps. |
| **Step** | One entry in `steps`, named by `step`: optionally a check, optionally an action, then the next `(nextStage, then)`. `then` says which step runs next. |
| **Action** | Java code run by a step. Its return value becomes the context's *last result*. |
| **Check** | A `boolean` method. `true` continues to `then`; `false` goes to `onFalse`. |
| **Outcome** | Routes on the action's return value (`toString()`); `otherwise` catches any other value, and with no match the step goes on to `then`. |
| **Run / Return** | A step with `runStage` runs that stage as a sub-flow and routes its `outcomes` on the `step` of the `returns` entry the sub-flow ended at. |
| **onError** | Where to go when an action or check throws. The exception is on `getSTMContext().getException()`. |
| **control** | `next` (default) continues; `stop` ends the run after this step. |

### How steps behave

- **`nextStage` omitted** (or `nextStage: this`) means the current stage.
- **Both `nextStage` and `then` omitted** makes the step terminal (`control: stop`).
- **`nextStage` without `then`** is an error.
- **Default start**: the top-level `startStage`/`startStep`. Without them, it is the first stage in the first file and its first entry in **`steps`** that is not a call (`runStage`); `returns` are not considered either, so set `startStage`/`startStep` when a flow begins with a call.
- **Error handlers are mandatory.** Every step with an action needs an `onError`, and every step with a check needs an `onFalse`, either on the step itself or under `defaults`.
- **Placeholders**: a step without action or check just forwards. Chains of these are collapsed at build time. Cycles are rejected.
- **Loop guard**: a single run stops after 100 stage changes (`setMaxStateChangeAllowed` to change it), in case a flow loops forever.
- An error raised *while already handling an error* stops the run.

## Configuration reference

Flows are YAML files ending in `.yaml` or `.yml`; any other file is rejected. Unknown keys are
errors, so a misspelled key is reported when the flow is built.

### Top level

| Key | Description |
|---|---|
| `name` | Machine name (from the first file loaded). |
| `startStage`, `startStep` | Where a run begins: the stage, and the step in it. |
| `defaults` | `onError` and `onFalse` (each `{ nextStage, then }`) for every step that does not set its own. |
| `stages` | The list of stages. |
| `include` | More flow files to load: a list (`[ a.yaml, b.yaml ]`) or comma-separated text. Paths or `classpath:` locations. |
| `postProcessor` | Comma-separated Spring bean types implementing `STMOptimizedBuilder.STMPostProcessor`, called after build. |

### A stage

| Key | Description |
|---|---|
| `name` | **Required.** The stage name. |
| `steps` | The steps. A step with `runStage` runs another stage as a sub-flow. |
| `returns` | Steps that end a sub-flow and return to the caller. |

### Entries in `steps` and `returns`

| Key | Description |
|---|---|
| `step` | **Required.** The name of this step. Unique within the stage. |
| `nextStage` | Target stage. Defaults to the current stage. `this` also means the current stage. |
| `runStage` | Runs this stage as a sub-flow, then comes back (instead of `nextStage`; not both). |
| `then` | The step to continue with in the target stage. |
| `control` | `next` (default) or `stop`. |
| `action` | The step's code. |
| `check` | A guard. Same keys as `action`; the method must return `boolean`/`Boolean`. |
| `onError: { nextStage, then }` | Overrides the error route under `defaults`. |
| `onFalse: { nextStage, then }` | Overrides the failed-check route under `defaults`. |
| `outcomes: [ { when, nextStage, then } ]` | Value-based routing: each outcome names a result with `when`. An outcome's `nextStage` defaults to the current stage. |
| `otherwise: { nextStage, then }` | Where to go when no outcome matches. A `runStage` step **must** have one. |

A step with `runStage` pushes a return point, then continues at `runStage`/`then`. A `returns`
entry pops it and routes the calling step's `outcomes` on that `step` value:

```yaml
steps:
  - step: pay
    runStage: payment          # run the payment stage as a sub-flow
    then: start
    outcomes:
      - { when: approved, nextStage: shipping, then: choose }
    otherwise: { nextStage: failed, then: declined }
```

### `action` / `check`

| Key | Description |
|---|---|
| `class` | Class to instantiate with its no-argument constructor (no Spring needed). |
| `beanType` | Spring bean type. It is registered as a bean automatically when using `STMBeanDefinitionRegistry`. |
| `beanName` | Existing Spring bean name. If none of the three is given, the `step` name is used as the bean name. |
| `method` | Method to call. If omitted, `Actor.handle(ctx)` is called. |
| `input` | A fixed value passed as the second argument: text calls `method(ctx, String)`; a map such as `{ limit: 10000, currency: USD }` calls `method(ctx, Map<String, String>)`. Values are always text, exactly as written. |
| `lookupMethod` | A method whose result is passed as the second argument: `method(ctx, lookupMethod(ctx))`. Use `bean.method` to call a method on another bean, or `lookupBeanType` to name its type. |
| `scope` | `prototype` for a new bean per lookup; singleton by default. |
| `properties: [ { name, value } ]` | Bean properties. Numeric names (`0`..`4`) become constructor arguments. |

### Placeholders in any value

- `e:com.example.MyEnum.CONSTANT` resolves to `CONSTANT` (validated against the enum).
- Other values pass through the builder's `StringValueResolver`, if one is supplied
  (`new STMOptimizedBuilder<>(appContext, false, resolver)`).

## Writing actors

An actor class implements `Actor<YourContext>`. Methods are matched by name and parameter types:

| Configuration | Method signature |
|---|---|
| `action: { method: m }` | `Object m(YourContext ctx)` (any return type) |
| `check: { method: m }` | `boolean m(YourContext ctx)` or `Boolean` |
| `{ method: m, input: x }` | `Object m(YourContext ctx, String arg)` |
| `{ method: m, input: { a: 1, b: x } }` | `Object m(YourContext ctx, Map<String, String> input)` |
| `{ method: m, lookupMethod: l }` | `Object m(YourContext ctx, T value)` with `T l(YourContext ctx)` |
| no `method` | override `Object handle(YourContext ctx)` |

The return value is stored as the *last result* (`ctx.getLastSTMResult()`) and is what `outcomes` route on.
An actor may also return an `STMEvent` (for example `STMEvent.STOP`) to control the engine directly.

### Passing settings with `input`

`input` gives a step fixed values from the flow file, so one method can serve several steps. The
order example sets its credit limit this way:

```yaml
- step: start
  then: approved
  check: { class: com.example.OrderActions, method: charge, input: { creditLimit: 1000 } }
```

```java
public boolean charge(OrderContext order, Map<String, String> input) {
    return order.getAmount() <= Integer.parseInt(input.get("creditLimit"));
}
```

Every value is text, exactly as written in the file (`1000` is `"1000"`, `1.50` is `"1.50"`), so
there is nothing to cast; the map is read-only. A value must be a single text: a nested map, a list
or an empty value (`limit:`) fails the build; write `''` for an empty text. If the method's second
parameter does not fit the input (a `String` for a map, say), the build fails and names the method
it expected.

## Decision rules

Instead of writing a condition method for every check, you can describe conditions in a YAML rule file
and combine them into named decisions. The package is `com.easysoft.decision`; it builds on the engine
and does not change it.

A **condition** reads a bean property path from the flow context and applies one operator.
A **decision** combines conditions and other decisions with `and`, `or`, `not` and parentheses.
An **outcome** picks a named result, such as `APPROVE` or `REVIEW`, for a flow to route on.

```yaml
# decision/loan-rules.yaml
name: loan.rules
type: com.easysoft.decision.LoanContext   # optional: check every path and value when loading
conditions:
  - name: smallAmount
    path: amount                     # ctx.getAmount()
    lessOrEqual: 10000
  - name: vip
    path: customer.vip               # ctx.getCustomer().isVip()
    equal: true
  - name: domestic
    path: customer.address.country
    in: [US, CA]
  - name: blocked
    path: customer.status            # enums compare by name
    equal: BLOCKED
  - name: lowRisk
    path: attributes[risk]           # map key; tags[0] reads a list element
    in: [low, medium]
decisions:
  - name: autoApprove
    when: not blocked and smallAmount and domestic and (vip or lowRisk)
  - name: needsReview
    when: not autoApprove and not blocked
```

| Operator | True when the value at `path`... |
|---|---|
| `equal` / `notEqual` | equals / does not equal the value (numbers by value, enums by name) |
| `in` / `notIn` | is / is not one of the listed values |
| `greaterThan`, `greaterOrEqual`, `lessThan`, `lessOrEqual` | compares as stated; numbers numerically, `Comparable` types such as `LocalDate` via their `parse`/`valueOf` |
| `isNull` | is `null` (`true`) or not `null` (`false`) |
| `matches` | as text, matches the regular expression |

A `null` along the path (no customer, no address, a missing map key, an index out of range) gives
`null`; the ordering operators and `matches` are then false. A property that does not exist on the
class is an error, so a typo is reported instead of quietly evaluating to false. Unknown names,
cycles between decisions and malformed expressions fail when the file is loaded. A decision may refer
to a rule set loaded earlier as `otherSet/name`, and one file may hold several rule sets separated by `---`.

Load the rule file once at startup, then use a decision as a step's `check` through
`DecisionActor`, naming it `ruleSet/name` in `input`:

```java
DecisionRegistry.getDefault().load("classpath:decision/loan-rules.yaml");
```

```yaml
- step: assess
  then: approve
  check: { class: com.easysoft.decision.DecisionActor, method: evaluate, input: loan.rules/autoApprove }
  onFalse: { nextStage: this, then: triage }
```

Rules can also be used from code: `registry.evaluate("loan.rules/autoApprove", ctx)`, or
`registry.getDecision("loan.rules/autoApprove")`, which is itself an `Actor` returning `Boolean`.
The full example is [`loan-rules.yaml`](src/test/resources/decision/loan-rules.yaml) with
[`loan-flow.yaml`](src/test/resources/stm/loan-flow.yaml).

### Inline comparisons

A `when` can compare a path directly, so a named condition is only needed when you want to reuse it:

```yaml
decisions:
  - name: largeForeign
    when: amount > 50000 and customer.address.country not in [US, CA]
  - name: fastTrack
    when: vip and (customer.tier == GOLD or attributes[risk] == low)
```

| Inline form | Same as |
|---|---|
| `path == v`, `path != v` | `equal`, `notEqual` |
| `path > v`, `>=`, `<`, `<=` | `greaterThan`, `greaterOrEqual`, `lessThan`, `lessOrEqual` |
| `path in [a, b]`, `path not in [a, b]` | `in`, `notIn` |
| `path matches 'regex'` | `matches` |
| `path is null`, `path is not null` | `isNull: true`, `isNull: false` |

Unquoted `true`, `false`, `null` and numbers are typed; any other unquoted word is text, so
`customer.status == BLOCKED` compares with the enum constant. Quote text that contains spaces or
punctuation (`'me@corp.com'`); a backslash escapes a quote. Comparisons bind tighter than `not`,
`and` and `or`, so `not amount > 5` means `not (amount > 5)`. In YAML, quote the whole `when` if it
starts with `!`, `[` or a quote, or contains `: ` or ` #`.

### Checking rules against your context class

With `type:` in the rule file, or `registry.load(location, LoanContext.class)`, every path and value
is checked when the file is loaded, and the file is rejected with all problems listed together:

```
rule set 'loan.rules' does not fit com.easysoft.decision.LoanContext:
  condition 'vip': path 'customer.vipp': ...LoanContext$Customer has no property 'vipp' ...
  decision 'fastTrack': 'customer.tier == GOLDD': path 'customer.tier' (Tier): 'GOLDD' is not one of SILVER, GOLD
```

It catches missing properties, enum values that are not constants, `true`/`false` and numbers that
are not, ordering on enums, booleans or non-`Comparable` types, `null` tests on primitives, and dates
that do not parse. Generic types are followed (`List<String>`, `Map<String, X>`); a path through a
property declared as `Object`, an interface or an abstract class is left to run time, since the
actual object may have more properties. `registry.validate("loan.rules", LoanContext.class)` checks a
rule set that is already loaded.

### Explaining a result

`registry.explain(key, ctx)` evaluates a condition, decision or outcome exactly as `evaluate` or
`decide` would, and returns an `Explanation` tree: every part's value, the bean values read, and the
parts skipped by short-circuiting. Its `toString()` is ready for a log or an audit record:

```
loan.rules/loanOutcome = REVIEW
  rule 1: blocked -> REJECT = false
    blocked = false  [customer.status is ACTIVE; equal 'BLOCKED']
  rule 2: autoApprove -> APPROVE = false
    autoApprove = false
      and = false
        not = true
          blocked = false  [customer.status is ACTIVE; equal 'BLOCKED']
        smallAmount = false  [amount is 50000; lessOrEqual 10000]
        domestic  (not evaluated)
        (trusted or lowRisk)  (not evaluated)
  rule 3: domestic -> REVIEW = true
    domestic = true  [customer.address.country is 'US'; in ['US', 'CA']]
```

The same information is available as data through `getValue()`, `getResult()`, `getDetail()` and
`getChildren()`.

### Outcomes: a named result instead of true/false

An **outcome** is an ordered list of `when`/`result` rules: the first rule whose `when` is true gives
the result, and `default` is the result when none is (without a `default`, no match gives `null`).
`when` takes the same expressions as a decision.

```yaml
outcomes:
  - name: loanOutcome
    rules:
      - { when: blocked, result: REJECT }        # checked first
      - { when: autoApprove, result: APPROVE }
      - { when: domestic, result: REVIEW }
    default: REJECT
```

The word appears in both files with matching meanings: in the rule file, an outcome *decides* the
result; in the flow, a step's `outcomes` list says *where each result goes*.

Use it as a step's **action** with `method: decide`; the result becomes the last result,
so the step's `outcomes` route on it. A result with no matching outcome, or `null`, takes the
step's own `then`:

```yaml
- step: assess
  nextStage: done
  then: reject                                   # REJECT, or anything else without an outcome
  action: { class: com.easysoft.decision.DecisionActor, method: decide, input: loan.rules/loanOutcome }
  outcomes:
    - { when: APPROVE, nextStage: done, then: approve }
    - { when: REVIEW, nextStage: done, then: review }
```

An outcome's `nextStage` defaults to the current stage, not to the step's `nextStage`, so give it when
the target is another stage. From code, `registry.decide("loan.rules/loanOutcome", ctx)` returns the
result as a `String`. An outcome cannot be used inside a `when`, since it is not true/false. The
full example is [`loan-outcome-flow.yaml`](src/test/resources/stm/loan-outcome-flow.yaml).

## Using Spring

Register the bean-definition registry and point it at your files:

```java
@Configuration
public class StmConfig {
    @Bean
    public static STMBeanDefinitionRegistry stmBeanDefinitionRegistry() {
        return new STMBeanDefinitionRegistry();   // or new STMBeanDefinitionRegistry("classpath:stm/order-flow.yaml")
    }
}
```

```properties
stm.config.locations=classpath:stm/order-flow.yaml,classpath:stm/other-flow.yaml
```

At startup the registry:

1. reads every file and registers each `beanType` actor as a Spring bean (so it can use `@Autowired`),
2. registers one `STMFactory` bean per machine, named after the machine, which builds and validates it.

Look machines up by name with `STMFactory.getFsmAllStateful().get("order.flow")`, or use a `StmStarter`:

```java
@Bean
public StmStarter orderStarter() {
    return new StmStarter("order.flow", "order", "received");
}

// later
OrderContext order = new OrderContext(orderStarter.createStmStateful(), "A-1", 100, false);
orderStarter.start(order);
```

## Tracing and debugging

Use a tracing context to log one line per run with every step, its (abbreviated) result and its duration:

```java
OrderContext order = new OrderContext(stm.createContextWithTrace(), "A-1", 100, false);
```

```
|received<-order(OrderActions@574caa3f#receive)->A-1(0)ms|validate<-order(OrderActions@6c629d6e#isValid)->true(0)ms
|pay<-order(CallState@27abe2cd)->true(0)ms|start<-payment(OrderActions@29444d75#charge)->true(0)ms
|approved<-payment(CallStateReturn@5479e3f)->approved(0)ms|pay@RESULT<-order(CallStateResult@6fdb1f78)->approved(0)ms
|choose<-shipping(OrderActions@7bfcd12c#shippingMethod)->STANDARD(0)ms|standard<-shipping(OrderActions@24273305#shipStandard)->null(0)ms
|complete<-shipping(PlaceHolder)->null(0)ms|Process is finished STOP
```

(Shown wrapped here; it is one line in the log.) Each entry reads `step<-stage(Actor#method)->result(duration)`.
Override `toString()` in your actor classes to replace the `ClassName@hash` part with something shorter.

Pass your own `STMTraceWriter` to `createContextWithTrace(writer)` to send traces elsewhere.
`STMTraceLogWriter.setRecordSeperator(...)` and `setStepInfoSeperator(...)` change the separators.
`builder.setDebug(true)` logs the decisions the builder makes (defaults it fills in, reductions).

## Driving the machine from code

```java
StatefulContext sc = ctx.getSTMContext();

sc.start(ctx);                          // default start state/event
sc.start(ctx, "payment", "start");

// inside an actor: jump somewhere else instead of following `then`
return ctx.getSTMContext().next(ctx, "shipping", "express");
return ctx.getSTMContext().next(ctx, "order.validate");   // event in the current state
```

Other hooks:

```java
sc.setEndTransition(cleanupTransition);   // runs once after every run of this context, e.g. for cleanup

// inside an actor: hand the flow over to another machine
return ctx.getSTMContext().getStm().startWithNewSTM(otherStm, ctx, otherStm.getState("s").getTransition(otherStm.getEvent("go")));
```

`start` throws `InvalidStateException`, `InvalidEventException` or `InvalidTransitionException` for
unknown names. Configuration problems throw `STMException` from `build()`.

## Building and testing

```bash
mvn verify
```

This compiles the project, runs the test suite, and writes a coverage report to
`target/site/jacoco/index.html`.

The tests cover the engine (`com.easysoft.stm`), the builder and configuration readers
(`com.easysoft.stm.config`), the built-in actors, and and the full order example.
`RegressionTest` and `ConfigRegressionTest` guard the bugs fixed so far (see [CHANGELOG.md](CHANGELOG.md)).

## Known issues

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for open issues and behaviour worth knowing about.

## Releasing

See [PUBLISHING.md](PUBLISHING.md) for publishing a version to Maven Central.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
