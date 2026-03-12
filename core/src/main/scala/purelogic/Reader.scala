package purelogic

trait Reader[+R] {
  def read: R

  def read[A](f: R => A): A                                      = f(read)
  def local[A, R1 >: R](f: R1 => R1)(body: Reader[R1] ?=> A): A  = Reader(f(read))(body)
  def focus[A, B, R1 >: R](f: R1 => A)(body: Reader[A] ?=> B): B = Reader(f(read))(body)
}

object Reader {
  def apply[R, A](value: R)(body: Reader[R] ?=> A): A = {
    given Reader[R] = new Reader[R] {
      def read: R = value
    }
    body
  }

  given Reader[Unit] = new Reader[Unit] {
    def read: Unit = ()
  }

  inline def read[R](using r: Reader[R]): R                                          = r.read
  inline def read[R, A](using r: Reader[R])(f: R => A): A                            = r.read(f)
  inline def local[R, A](using r: Reader[R])(f: R => R)(body: Reader[R] ?=> A): A    = r.local(f)(body)
  inline def focus[R, A, B](using r: Reader[R])(f: R => A)(body: Reader[A] ?=> B): B = r.focus(f)(body)
}
