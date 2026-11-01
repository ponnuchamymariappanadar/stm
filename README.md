# STM: a configurable state machine for Java

[![CI](https://github.com/ponnuchamymariappanadar/stm/actions/workflows/ci.yml/badge.svg)](https://github.com/ponnuchamymariappanadar/stm/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

STM runs business flows that you describe in **XML or YAML** and implement as **plain Java methods**.
The configuration says *what happens next*; your code only does the work for each step.

```xml
<State name="order.s">
    <Transition when="order.received" fire="order.validate">
        <Actor className="com.example.OrderActions" methodName="receive"/>
    </Transition>
    <Transition when="order.validate" fire="order.pay">
        <Condition className="com.example.OrderActions" methodName="isValid"/>
    </Transition>
    ...
</State>
```

## Features

- **Flows as configuration**: states, events and transitions in XML or YAML, split across files if you like.
- **Plain Java actors**: any public method `Object name(YourContext ctx)` can be a step, bound at startup through `LambdaMetafactory` (no reflection per call).
- **Conditions** with a failure route, **Case** routing on a method's return value, and **Call/Return** sub-flows you can reuse.
- **Error handling**: exceptions go to a per-transition or global `OnError` route, with the exception available on the context.
- **Validation at startup**: missing targets, duplicate events, missing handlers, infinite placeholder loops, and unknown methods are all reported before anything runs.
- **Placeholder reduction**: transitions that only forward to another transition are collapsed away at build time.
- **Tracing**: a debug context records every step with its result and timing in one log line.
- **Spring integration** (optional): actors referenced by `beanType` are registered as Spring beans automatically.

## Contents

- [Requirements](#requirements)
- [Quick start](#quick-start)
- [Core concepts](#core-concepts)
- [Configuration reference](#configuration-reference)
- [Writing actors](#writing-actors)
- [Using Spring](#using-spring)
- [Tracing and debugging](#tracing-and-debugging)
- [Driving the machine from code](#driving-the-machine-from-code)
- [Building and testing](#building-and-testing)
- [Known issues](#known-issues)
- [Contributing](#contributing) and [License](#license)

## Requirements

- Java 17 or newer
- Maven 3.9+
- Built on Spring Boot 4.0 dependency management (Jackson XML/YAML, SLF4J, Commons Lang, Guava, Lombok)

The artifact is not yet published to Maven Central. Build it locally with `mvn install` and depend on:

```xml
<dependency>
    <groupId>com.easysoft.stm</groupId>
    <artifactId>stm</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Quick start

The complete example below lives in the test sources and runs as part of the build
([`order-flow.xml`](src/test/resources/stm/order-flow.xml),
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

    public boolean isValid(OrderContext order) { return order.getAmount() > 0; }   // a Condition

    public boolean charge(OrderContext order) { ... }                              // a Condition

    public Shipping shippingMethod(OrderContext order) {                           // drives <Case>
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

```xml
<STM name="order.flow" start="order.s" fire="order.received">

    <!-- Defaults for every transition that does not declare its own -->
    <OnError           to="failed.s" fire="order.error"/>
    <OnFailedCondition to="failed.s" fire="order.rejected"/>

    <State name="order.s">
        <Transition when="order.received" fire="order.validate">
            <Actor className="com.example.OrderActions" methodName="receive"/>
        </Transition>

        <Transition when="order.validate" fire="order.pay">
            <Condition className="com.example.OrderActions" methodName="isValid"/>
        </Transition>

        <!-- Run the payment sub-flow; route on the event it returns with -->
        <Call when="order.pay" to="payment.s" fire="payment.start">
            <Case when="payment.approved" to="shipping.s" fire="ship.choose"/>
            <Case when="default"          to="failed.s"   fire="order.declined"/>
        </Call>
    </State>

    <State name="payment.s">
        <Transition when="payment.start" fire="payment.approved">
            <Condition className="com.example.OrderActions" methodName="charge"/>
            <OnFailedCondition to="this" fire="payment.declined"/>
        </Transition>
        <Return when="payment.approved"/>
        <Return when="payment.declined"/>
    </State>

    <State name="shipping.s">
        <!-- shippingMethod() returns EXPRESS or STANDARD -->
        <Transition when="ship.choose" fire="ship.standard">
            <Actor className="com.example.OrderActions" methodName="shippingMethod"/>
            <Case when="EXPRESS" fire="ship.express"/>
        </Transition>
        <Transition when="ship.standard" fire="order.complete">
            <Actor className="com.example.OrderActions" methodName="shipStandard"/>
        </Transition>
        <Transition when="ship.express" fire="order.complete">
            <Actor className="com.example.OrderActions" methodName="shipExpress"/>
        </Transition>
        <Transition when="order.complete" stmevent="stop"/>
    </State>

    <State name="failed.s">
        <Transition when="order.error" fire="order.failed">
            <Actor className="com.example.OrderActions" methodName="recordError"/>
        </Transition>
        <Transition when="order.rejected" fire="order.failed">
            <Actor className="com.example.OrderActions" methodName="reject"/>
        </Transition>
        <Transition when="order.declined" fire="order.failed">
            <Actor className="com.example.OrderActions" methodName="decline"/>
        </Transition>
        <Transition when="order.failed" stmevent="stop"/>
    </State>
</STM>
```

### 4. Build once, run many times

```java
STMOptimizedBuilder<STMContext> builder = new STMOptimizedBuilder<>(null, false);
builder.addFile("classpath:stm/order-flow.xml");     // or a file path, or .yml/.yaml
STMStateful<STMContext> stm = builder.build();       // validates the whole flow

OrderContext order = new OrderContext(stm.createContext(), "A-1", 100, false);
order.getSTMContext().start(order);                  // runs until a transition stops

order.getHistory();   // [received, charged, shipped standard]
```

The built `STMStateful` is reusable. Create a new context for every run.

| Input | History |
|---|---|
| amount 100 | `received, charged, shipped standard` |
| amount 100, express | `received, charged, shipped express` |
| amount 0 | `received, rejected` (global `OnFailedCondition`) |
| amount 5000 | `received, charge refused, declined` (payment returns `payment.declined`, matched by `default`) |
| no order id | `error: order id is required` (global `OnError`) |

## Core concepts

A transition reads as: *in state **S**, when event **W** happens, run the actor, then fire event **F** in state **T***.

```
<State name="S">
    <Transition when="W" to="T" fire="F"> <Actor .../> </Transition>
</State>
```

| Concept | Meaning |
|---|---|
| **State** | A named group of transitions. |
| **Event** (`when` / `fire`) | Names a transition inside a state. `fire` says which transition runs next. |
| **Transition** | One step: optionally a condition, optionally an actor, then the next `(to, fire)`. |
| **Actor** | Java code run by a transition. Its return value becomes the context's *last result*. |
| **Condition** | A `boolean` method. `true` continues to `fire`; `false` goes to `OnFailedCondition`. |
| **Case** | Routes on the actor's return value (`toString()`), falling back to `default`, then to `fire`. |
| **Call / Return** | Runs another state as a sub-flow and routes on the `when` of the `Return` it ended at. |
| **OnError** | Where to go when an actor or condition throws. The exception is on `getSTMContext().getException()`. |
| **stmevent** | `next` (default) continues; `stop` ends the run after this transition. |

### Rules and defaults

- **`to` omitted** (or `to="this"`) means the current state.
- **Both `to` and `fire` omitted** makes the transition terminal (`stmevent="stop"`).
- **`to` without `fire`** is an error.
- **Default start**: the `start`/`fire` attributes on `<STM>`. Without them, it is the first `<State>` in the first file and its first **`<Transition>`**. `<Call>` and `<Return>` elements are not considered, so set `start`/`fire` when a flow begins with a `<Call>`.
- **Error handlers are mandatory.** Every transition with an actor needs a local or global `<OnError>`, and every transition with a condition needs a local or global `<OnFailedCondition>`.
- **Placeholders**: a transition without actor or condition just forwards. Chains of these are collapsed at build time. Cycles are rejected.
- **Loop guard**: a single run stops after 100 state changes (`setMaxStateChangeAllowed` to change it), in case a flow loops forever.
- An error raised *while already handling an error* stops the run.

## Configuration reference

### `<STM>` (root)

| Attribute | Description |
|---|---|
| `name` | Machine name (from the first file loaded). |
| `start`, `fire` | Default start state and event. |
| `stmFiles` | Comma-separated list of more files to load (paths or `classpath:` locations). |
| `postProcessor` | Comma-separated Spring bean types implementing `STMOptimizedBuilder.STMPostProcessor`, called after build. |

Children: `<OnError>`, `<OnFailedCondition>` (global defaults) and `<State>` elements.

### `<State name="...">`

Children: `<Transition>`, `<Call>`, `<Return>`.

### `<Transition>`, `<Call>`, `<Return>`

| Attribute | Description |
|---|---|
| `when` | **Required.** The event that selects this transition. Unique within the state. |
| `to` | Target state. Defaults to the current state. `this` also means the current state. |
| `fire` | Event to fire in the target state. |
| `stmevent` | `next` (default) or `stop`. |

| Child | Description |
|---|---|
| `<Actor>` | The step's code. |
| `<Condition>` | A guard. Same attributes as `<Actor>`; the method must return `boolean`/`Boolean`. |
| `<OnError to fire>` | Overrides the global error route. |
| `<OnFailedCondition to fire>` | Overrides the global failed-condition route. |
| `<Case when to fire>` | Value-based routing. `when="default"` catches unmatched values. A `<Call>` **must** have a `default` case. |

`<Call when to fire>` pushes a return point, then continues at `to`/`fire`. `<Return when>` pops it and routes the caller's `<Case>`s on that `when` value.

### `<Actor>` / `<Condition>`

| Attribute | Description |
|---|---|
| `className` | Class to instantiate with its no-argument constructor (no Spring needed). |
| `beanType` | Spring bean type. It is registered as a bean automatically when using `STMBeanDefinitionRegistry`. |
| `beanName` | Existing Spring bean name. If none of the three is given, the `when` event name is used as the bean name. |
| `methodName` | Method to call. If omitted, `Actor.handle(ctx)` is called. |
| `argParameter` | A fixed string passed as the second argument: `method(ctx, String)`. |
| `lookupMethod` | A method whose result is passed as the second argument: `method(ctx, lookupMethod(ctx))`. Use `bean.method` to call a method on another bean, or `lookupBeanType` to name its type. |
| `scope` | `prototype` for a new bean per lookup; singleton by default. |
| `<Property name value>` | Bean properties. Numeric names (`0`..`4`) become constructor arguments. |

### Placeholders in any value

- `e:com.example.MyEnum.CONSTANT` resolves to `CONSTANT` (validated against the enum).
- Other values pass through the builder's `StringValueResolver`, if one is supplied
  (`new STMOptimizedBuilder<>(appContext, false, resolver)`).

### YAML

YAML uses the Java property names (`states`, `transitions`, `calls`, `returns`, `actor`, `condition`,
`cases`, `onError`, `onFailedCondition`). A file ending in `.yml` or `.yaml` is read as YAML.

```yaml
name: order.flow
start: order.s
fire: order.received
onError: { to: failed.s, fire: order.error }
onFailedCondition: { to: failed.s, fire: order.rejected }
states:
  - name: order.s
    transitions:
      - when: order.received
        fire: order.validate
        actor: { className: com.example.OrderActions, methodName: receive }
    calls:
      - when: order.pay
        to: payment.s
        fire: payment.start
        cases:
          - { when: payment.approved, to: shipping.s, fire: ship.choose }
          - { when: default, to: failed.s, fire: order.declined }
  - name: payment.s
    returns:
      - when: payment.approved
```

The full example is in [`order-flow.yaml`](src/test/resources/stm/order-flow.yaml).

## Writing actors

An actor class implements `Actor<YourContext>`. Methods are matched by name and parameter types:

| Configuration | Method signature |
|---|---|
| `methodName="m"` | `Object m(YourContext ctx)` (any return type) |
| `<Condition methodName="m">` | `boolean m(YourContext ctx)` or `Boolean` |
| `methodName="m" argParameter="x"` | `Object m(YourContext ctx, String arg)` |
| `methodName="m" lookupMethod="l"` | `Object m(YourContext ctx, T value)` with `T l(YourContext ctx)` |
| no `methodName` | override `Object handle(YourContext ctx)` |

The return value is stored as the *last result* (`ctx.getLastSTMResult()`) and is what `<Case>` routes on.
An actor may also return an `STMEvent` (for example `STMEvent.STOP`) to control the engine directly.

## Using Spring

Register the bean-definition registry and point it at your files:

```java
@Configuration
public class StmConfig {
    @Bean
    public static STMBeanDefinitionRegistry stmBeanDefinitionRegistry() {
        return new STMBeanDefinitionRegistry();   // or new STMBeanDefinitionRegistry("classpath:stm/order-flow.xml")
    }
}
```

```properties
stm.config.locations=classpath:stm/order-flow.xml,classpath:stm/other-flow.xml
```

At startup the registry:

1. reads every file and registers each `beanType` actor as a Spring bean (so it can use `@Autowired`),
2. registers one `STMFactory` bean per machine, named after the machine, which builds and validates it.

Look machines up by name with `STMFactory.getFsmAllStateful().get("order.flow")`, or use a `StmStarter`:

```java
@Bean
public StmStarter orderStarter() {
    return new StmStarter("order.flow", "order.s", "order.received");
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
|order.received<-order.s(OrderActions@574caa3f#receive)->A-1(0)ms|order.validate<-order.s(OrderActions@6c629d6e#isValid)->true(0)ms
|order.pay<-order.s(CallState@27abe2cd)->true(0)ms|payment.start<-payment.s(OrderActions@29444d75#charge)->true(0)ms
|payment.approved<-payment.s(CallStateReturn@5479e3f)->payment...(0)ms|order.pay@RESULT<-order.s(CallStateResult@6fdb1f78)->payment...(0)ms
|ship.choose<-shipping.s(OrderActions@7bfcd12c#shippingMethod)->STANDARD(0)ms|ship.standard<-shipping.s(OrderActions@24273305#shipStandard)->null(0)ms
|order.complete<-shipping.s(PlaceHolder)->null(0)ms|Process is finished STOP
```

(Shown wrapped here; it is one line in the log.) Each step reads `event<-state(Actor#method)->result(duration)`.
Override `toString()` in your actor classes to replace the `ClassName@hash` part with something shorter.

Pass your own `STMTraceWriter` to `createContextWithTrace(writer)` to send traces elsewhere.
`STMTraceLogWriter.setRecordSeperator(...)` and `setStepInfoSeperator(...)` change the separators.
`builder.setDebug(true)` logs the decisions the builder makes (defaults it fills in, reductions).

## Driving the machine from code

```java
StatefulContext sc = ctx.getSTMContext();

sc.start(ctx);                          // default start state/event
sc.start(ctx, "payment.s", "payment.start");

// inside an actor: jump somewhere else instead of following `fire`
return ctx.getSTMContext().next(ctx, "shipping.s", "ship.express");
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
(`com.easysoft.stm.config`), the built-in actors, and the full order example in both XML and YAML.
`RegressionTest` and `ConfigRegressionTest` guard the bugs fixed so far (see [CHANGELOG.md](CHANGELOG.md)).

## Known issues

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for open issues and behaviour worth knowing about.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
