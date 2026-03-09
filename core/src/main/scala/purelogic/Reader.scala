package purelogic

sealed trait Reader[R] {
  def ask: R
}

object Reader {
  def ask[R](using r: Reader[R]): R = r.ask

  def inquire[R, B](using r: Reader[R])(f: R => B): B = f(r.ask)

  private[purelogic] def make[R](value: R): Reader[R] = new Reader[R] {
    def ask: R = value
  }
}
