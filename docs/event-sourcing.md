# Event Sourcing

`EventSourcing[Ev, S, Err]` enforces the **event sourcing pattern**: every state change must go through a `Transition` triggered by an event. This guarantees that state and emitted events are always in sync.

It combines [`State`](state.md) and [`Writer`](writer.md) under the hood, but restricts access so that you can only modify state by writing events through transitions.

::: tip Blog post
For a detailed walkthrough with motivations and design rationale, see [Event Sourcing with PureLogic](https://blog.pierre-ricadat.com/event-sourcing-with-purelogic/).
:::

## Defining a transition

A `Transition[Ev, S, Err]` defines how an event modifies the state, potentially failing with an error:

```scala
import purelogic.*

case class Account(balance: Int)

enum AccountEvent {
  case Deposit(amount: Int)
  case Withdraw(amount: Int)
}

given EventSourcing.Transition[AccountEvent, Account, String] with {
  def run(ev: AccountEvent): (State[Account], Abort[String]) ?=> Unit =
    ev match {
      case AccountEvent.Deposit(amount) =>
        update(a => Account(a.balance + amount))
      case AccountEvent.Withdraw(amount) =>
        ensure(get.balance >= amount, "Insufficient balance")
        update(a => Account(a.balance - amount))
    }
}
```

## Writing events

`writeEvent` applies the transition to update the state, then records the event. If the transition fails via `Abort`, the event is **not** recorded:

```scala
type Program[A] = EventSourcingLogic[Config, AccountEvent, Account, String, A]

def deposit(amount: Int): Program[Unit] = {
  ensure(amount <= read(_.maxDeposit), "Amount exceeds maximum deposit")
  writeEvent(AccountEvent.Deposit(amount))
}

def withdraw(amount: Int): Program[Unit] = {
  ensure(amount <= read(_.maxWithdrawal), "Amount exceeds maximum withdrawal")
  writeEvent(AccountEvent.Withdraw(amount))
}
```

Notice how the domain logic receives exactly the capabilities it needs: configuration access via `Reader`, read-only state inspection via `StateReader`, error handling via `Abort`, and the `EventSourcing` capability for writing events. Direct state mutation is **not available**.

## Replaying events

`replayEvents` rebuilds state from a persisted event log by applying transitions **without** recording events in the writer. This is useful for rehydrating state from storage:

```scala
def loadAndProcess(savedEvents: Vector[AccountEvent]): Program[Unit] = {
  replayEvents(savedEvents)
  deposit(100)
}
```

## The `EventSourcingLogic` type alias

Similar to `Logic`, PureLogic provides a type alias for event-sourced programs:

```scala
type EventSourcingLogic[R, Ev, S, Err, A] =
  (EventSourcing[Ev, S, Err], Reader[R], StateReader[S], Abort[Err]) ?=> A
```

Note that only `StateReader` (not `State`) is available, since state can only be changed through events.

## Running

### `Logic.runEventSourcing`

Runs an event-sourced program. Returns `Right((events, finalState, result))` on success, or `Left(error)` on failure:

```scala
val result: Either[String, (Vector[AccountEvent], Account, Unit)] =
  Logic.runEventSourcing(Account(100), config) {
    deposit(50)
    withdraw(30)
  }
// Right((Vector(Deposit(50), Withdraw(30)), Account(120), ()))
```

### `Logic.runEventSourcingInfallible`

If your program cannot fail, use this to avoid the `Either` wrapper:

```scala
val (events, finalState, result) =
  Logic.runEventSourcingInfallible(Account(100), config) {
    myInfallibleProgram
  }
```

## Polymorphic transitions

You can define different transitions for **subtypes** of your event type. `writeEvent` accepts any `Ev1 <: Ev`, so each event subtype can have its own dedicated `Transition`. `replayEvents`, however, still requires a `Transition[Ev, S, Err]` for the base event type, so replaying a persisted `Vector[Ev]` also needs a base transition.

::: warning
This works with `sealed trait` hierarchies but not with `enum`, because Scala widens enum cases to the parent type by default.
:::

```scala
sealed trait AccountEvent
object AccountEvent {
  case class Deposit(amount: Int) extends AccountEvent
  case class Withdraw(amount: Int) extends AccountEvent
}

given EventSourcing.Transition[AccountEvent.Deposit, Account, String] with {
  def run(ev: AccountEvent.Deposit): (State[Account], Abort[String]) ?=> Unit =
    update(a => Account(a.balance + ev.amount))
}

given EventSourcing.Transition[AccountEvent.Withdraw, Account, String] with {
  def run(ev: AccountEvent.Withdraw): (State[Account], Abort[String]) ?=> Unit =
    ensure(get.balance >= ev.amount, "Insufficient balance")
    update(a => Account(a.balance - ev.amount))
}
```
