# Capabilities

**PureLogic** provides 4 basic capabilities that can be used to compose your pure domain logic:

- `Reader[R]`: read a value of type `R` using the `read` function
- `Writer[W]`: accumulate values of type `W` using the `write` function
- `State[S]`: read and write a value of type `S` using the `get` and `set` functions
- `Abort[E]`: abort the computation with an error of type `E` using the `fail` function

You can then run a computation that uses these capabilities using the `Logic.run` function.

- Login.run and variants, Logic alias