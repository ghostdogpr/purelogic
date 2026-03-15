# State

`State[S]` provides **mutable state** scoped to a computation. You can read and update a value of type `S` without passing it around explicitly.

## Basic usage

```scala
import purelogic.*

case class Counter(value: Int)

def increment(using State[Counter]): Unit = {
  val current = get(_.value)
  set(Counter(current + 1))
}

val (finalState, result) = State(Counter(0)) {
  increment
  increment
  increment
  get
}
// finalState: Counter(3)
// result: Counter(3)
```

## Functions

### `get`

Returns the **current state**:

```scala
val counter: Counter = get
```

You can also pass a **projection function** to extract a field directly:

```scala
val value: Int = get(_.value)
```

### `set`

**Replaces** the state with a new value:

```scala
set(Counter(10))
```

### `update`

**Modifies** the state using a function:

```scala
update(c => Counter(c.value + 1))
```

### `updateAndGet`

Updates the state and returns the **new** value:

```scala
val newState: Counter = updateAndGet(c => Counter(c.value + 1))
```

### `getAndSet`

Returns the **old** state and replaces it:

```scala
val oldState: Counter = getAndSet(Counter(0))
```

### `getAndUpdate`

Returns the **old** state and modifies it:

```scala
val oldState: Counter = getAndUpdate(c => Counter(c.value + 1))
```

### `modify`

Computes a **result and a new state** from the current state in one step:

```scala
val popped: Int = modify { stack =>
  (stack.items.head, Stack(stack.items.tail))
}
```

### `localState`

Runs a block with a **modified** state. The original state is **restored** after the block completes:

```scala
def process(using State[Counter]): Int = {
  set(Counter(5))
  localState(c => Counter(c.value * 10)) {
    get(_.value) // 50
  }
  get(_.value) // 5
}
```

### `focusState`

Runs a block that operates on a **subset** of the state. Reads and writes to the focused state are reflected in the outer state:

```scala
case class App(counter: Counter, name: String)

def increment(using State[Counter]): Unit = {
  update(c => Counter(c.value + 1))
}

def process(using State[App]): Unit = {
  focusState(_.counter)((app, c) => app.copy(counter = c)) {
    increment // operates on Counter, but updates App
  }
}
```

## Running

`State(initial)(body)` returns a tuple of the **final state** and the result:

```scala
val (finalState: S, result: A) = State(initialState) {
  myProgram
}
```
