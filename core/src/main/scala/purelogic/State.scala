package purelogic

/**
  * Mutable state scoped to a computation.
  *
  * Allows reading and updating a value of type `S` without passing it around explicitly.
  *
  * @tparam S
  *   the type of the state
  */
trait State[S] {

  /**
    * Returns the current state.
    */
  def get: S

  /**
    * Replaces the state with a new value.
    */
  def set(s: S): Unit

  /**
    * Applies a projection function to the state and returns the result.
    */
  def get[A](f: S => A): A = f(get)

  /**
    * Modifies the state using a function.
    */
  def update(f: S => S): Unit = set(f(get))

  /**
    * Updates the state using a function and returns the new state.
    */
  def updateAndGet(f: S => S): S = { update(f); get }

  /**
    * Computes a return value and a new state from the current state in one step.
    */
  def modify[A](f: S => (A, S)): A = { val (a, newState) = f(get); set(newState); a }

  /**
    * Returns the old state and replaces it with the given value.
    */
  def getAndSet(v: S): S = { val old = get; set(v); old }

  /**
    * Returns the old state and modifies it using a function.
    */
  def getAndUpdate(f: S => S): S = { val old = get; update(f); old }

  /**
    * Runs a block with a modified state. The original state is restored after the block completes.
    */
  def local[A](f: S => S)(body: State[S] ?=> A): A = {
    val state    = this
    val previous = get
    set(f(previous))
    try body(using state)
    finally set(previous)
  }

  /**
    * Runs a block that operates on a subset of the state. Reads and writes to the focused state are reflected in the
    * outer state.
    */
  def focus[A, B](getFocus: S => A)(setFocus: (S, A) => S)(body: State[A] ?=> B): B = {
    val outer = this
    val state = new State[A] {
      def get: A          = getFocus(outer.get)
      def set(a: A): Unit = outer.set(setFocus(outer.get, a))
    }
    body(using state)
  }
}

object State {

  /**
    * Provides a `State[S]` with the given initial value and runs the body, returning a tuple of the final state and the
    * result.
    */
  def apply[S, A](initial: S)(body: State[S] ?=> A): (S, A) = {
    var current: S = initial
    val state      = new State[S] {
      def get: S          = current
      def set(s: S): Unit = current = s
    }
    val result     = body(using state)
    (current, result)
  }

  /**
    * Default `State[Unit]` instance that does nothing.
    */
  given State[Unit] = new State[Unit] {
    def get: Unit          = ()
    def set(s: Unit): Unit = ()
  }

  /**
    * Returns the current state.
    */
  inline def get[S](using s: State[S]): S = s.get

  /**
    * Applies a projection function to the state and returns the result.
    */
  inline def get[S, A](using s: State[S])(f: S => A): A = s.get(f)

  /**
    * Replaces the state with a new value.
    */
  inline def set[S](v: S)(using s: State[S]): Unit = s.set(v)

  /**
    * Modifies the state using a function.
    */
  inline def update[S](using s: State[S])(f: S => S): Unit = s.update(f)

  /**
    * Updates the state using a function and returns the new state.
    */
  inline def updateAndGet[S](using s: State[S])(f: S => S): S = s.updateAndGet(f)

  /**
    * Computes a return value and a new state from the current state in one step.
    */
  inline def modify[S, A](using s: State[S])(f: S => (A, S)): A = s.modify(f)

  /**
    * Returns the old state and replaces it with the given value.
    */
  inline def getAndSet[S](v: S)(using s: State[S]): S = s.getAndSet(v)

  /**
    * Returns the old state and modifies it using a function.
    */
  inline def getAndUpdate[S](using s: State[S])(f: S => S): S = s.getAndUpdate(f)

  /**
    * Runs a block with a modified state. The original state is restored after the block completes.
    */
  inline def localState[S, A](using s: State[S])(f: S => S)(body: State[S] ?=> A): A = s.local(f)(body)

  /**
    * Runs a block that operates on a subset of the state. Reads and writes to the focused state are reflected in the
    * outer state.
    */
  inline def focusState[S, A, B](using s: State[S])(getFocus: S => A)(setFocus: (S, A) => S)(body: State[A] ?=> B): B =
    s.focus(getFocus)(setFocus)(body)
}
