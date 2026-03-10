package purelogic

case class Env(max: Int)
case class UserState(count: Int)
enum Event {
  case CountIncremented
}
enum ValidationError extends Exception {
  case InvalidInput(message: String)
}

type Program[A] = (Reader[Env], Raise[ValidationError], Writer[Event], State[UserState]) ?=> A

object Test {
  // def fakeBusinessLogic(ount: Int): Program[Unit] =
  //   for {
  //     max           <- inquire(_.max)
  //     existingCount <- inspect(_.count)
  //     newCount       = existingCount + count
  //     ()            <- assertThat(newCount <= max, ValidationError.InvalidInput(s"Count is greater than max: $count > $max"))
  //     ()            <- (existingCount to newCount).toList.traverse_(liftEvent(Event.CountIncremented))
  //   } yield ()

  def fakeBusinessLogic(count: Int): Program[Unit] = {
    val max           = inquire(_.max)
    val existingCount = inspect(_.count)
    val newCount      = existingCount + count
    ensure(newCount <= max, ValidationError.InvalidInput(s"Count is greater than max: $count > $max"))
    (existingCount to newCount).foreach(_ => tell(Event.CountIncremented))
  }

  def mainLogic: Program[Unit] = {
    Logic.simulateWith(UserState(1), Env(100))(fakeBusinessLogic(10))
    fakeBusinessLogic(10)
  }

  @main
  def main =
    println(Logic.run(UserState(10), Env(100))(mainLogic))
}
