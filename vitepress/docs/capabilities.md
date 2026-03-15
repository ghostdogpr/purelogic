# Capabilities

**PureLogic** provides 4 basic capabilities that can be used to compose your pure domain logic:

- [`Reader[R]`](reader.md): **read** a value of type `R`
- [`Writer[W]`](writer.md): **accumulate** values of type `W`
- [`State[S]`](state.md): **read and update** a value of type `S`
- [`Abort[E]`](abort.md): **abort** the computation with an error of type `E`

Each capability is described in detail on its own page. This page covers how to **use** and **combine** them.

## Calling capability functions

Every capability exposes its functions in **3 different ways**. Let's use `State` as an example:

### 1. Root-level functions

The simplest way. Functions like `get`, `set`, `read`, `write`, `fail` are available directly after `import purelogic.*`:

```scala
import purelogic.*

def program(using State[Account]): Unit = {
  val balance = get(_.balance)
  set(Account(balance + 100))
}
```

This is the **recommended way** for most code. It is concise and reads naturally.

### 2. Companion object functions

You can also call functions on the capability's companion object:

```scala
def program(using State[Account]): Unit = {
  val balance = State.get(_.balance)
  State.set(Account(balance + 100))
}
```

This is equivalent to the root-level version and can be useful for **clarity** when multiple capabilities are in scope.

### 3. Named capability instances

You can **name** the capability instance in the `using` clause and call methods on it directly:

```scala
def program(using account: State[Account], cart: State[Cart]): Unit = {
  val balance = account.get(_.balance)
  val items   = cart.get(_.items)
  account.set(Account(balance - 100))
  cart.set(Cart(items :+ newItem))
}
```

This is essential when you have **two capabilities with the same type** (e.g. two `State[Int]`), where the compiler can't disambiguate on its own. It's also useful for **readability** when you have several capabilities with different type parameters.

## The `Logic` type alias

When your function uses all 4 capabilities, the signature can get verbose:

```scala
def process(order: Order)(using Reader[Config], Writer[Event], State[Account], Abort[AppError]): Result = ???
```

PureLogic provides a **type alias** to simplify this:

```scala
type Logic[R, W, S, E, A] = (Reader[R], Writer[W], State[S], Abort[E]) ?=> A
```

So you can write:

```scala
def process(order: Order): Logic[Config, Event, Account, AppError, Result] = ???
```

Of course, you can also define your **own type aliases** tailored to your application:

```scala
type MyProgram[A] = (Reader[Config], State[Account], Abort[AppError]) ?=> A

def process(order: Order): MyProgram[Result] = ???
```

## Running a program

Each capability has an `apply` method that provides the capability and returns the result:

- `Reader(value)(body)` returns the result `A`
- `Writer(body)` returns `(Vector[W], A)`
- `State(initial)(body)` returns `(S, A)`
- `Abort(body)` returns `Either[E, A]`

These can be **nested** in any order:

```scala
val (logs, result) =
  Reader(config) {
    Writer {
      State(initialAccount) {
        Abort {
          myProgram
        }
      }
    }
  }
// result: Either[AppError, (Account, Unit)]
// logs: Vector[Event]
```

### `Logic.run`

For the common case of using all 4 capabilities together, `Logic.run` is a **convenience function**:

```scala
val (logs, result) = Logic.run(state = initialAccount, reader = config) {
  myProgram
}
// result: Either[AppError, (Account, Unit)]
// logs: Vector[Event]
```

This is equivalent to `Reader(reader)(Writer(Abort(State(initial)(body))))`.

### `Logic.runInfallible`

If your program does **not use `Abort`** (i.e. it cannot fail), you can use `Logic.runInfallible` to avoid the `Either` wrapper:

```scala
val (logs, finalState, result) = Logic.runInfallible(state = initialAccount, reader = config) {
  myInfallibleProgram
}
```

### `Logic.simulate` and `Logic.simulateWith`

These let you **run a sub-program in isolation** without impacting the current state or accumulated writes. This is useful when your logic needs to see what the result of a program **would be** without committing its side effects. Errors are still propagated to the outer program via `Abort`.

```scala
// Run with custom state and reader, without affecting the outer state or writes
val result = Logic.simulateWith(mockState = Account(100), mockEnv = Config(10)) {
  myProgram
}
```

`Logic.simulate` does the same but **reuses** the `Reader` and `State` from the outer scope instead of providing new values.
