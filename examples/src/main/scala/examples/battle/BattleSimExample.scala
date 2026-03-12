package examples.battle

import purelogic.*

// --- Domain ---

case class Stats(attack: Int, defense: Int)
case class Rules(hero: Stats, monster: Stats, heroMaxHp: Int, potionHeal: Int)
case class BattleState(heroHp: Int, monsterHp: Int, potions: Int, turn: Int)

enum Outcome {
  case Victory(turn: Int)
  case Defeat(turn: Int)
}

type Battle[A] = Logic[Rules, String, BattleState, Outcome, A]

// --- Logic ---

/**
  * A turn-based battle simulation.
  *
  * Uses Abort not for errors but as a control-flow mechanism: the battle loop runs until one side falls, at which point
  * the outcome short-circuits the entire computation. Writer accumulates a turn-by-turn narrative.
  */
object BattleSimExample {

  def heroAttack: Battle[Unit] = {
    val damage = (read(_.hero.attack) - read(_.monster.defense)).max(1)
    update(s => s.copy(monsterHp = s.monsterHp - damage))
    val hp     = get(_.monsterHp)
    write(s"  Hero attacks for $damage damage! (Monster: $hp HP)")
    ensureNot(hp <= 0, Outcome.Victory(get(_.turn)))
  }

  def monsterAttack: Battle[Unit] = {
    val damage = (read(_.monster.attack) - read(_.hero.defense)).max(1)
    update(s => s.copy(heroHp = s.heroHp - damage))
    val hp     = get(_.heroHp)
    write(s"  Goblin attacks for $damage damage! (Hero: $hp HP)")
    ensureNot(hp <= 0, Outcome.Defeat(get(_.turn)))
  }

  def usePotion: Battle[Unit] = {
    val heal  = read(_.potionHeal)
    val maxHp = read(_.heroMaxHp)
    update(s => s.copy(heroHp = (s.heroHp + heal).min(maxHp), potions = s.potions - 1))
    write(s"  Hero uses a potion! (+$heal HP, ${get(_.potions)} remaining)")
  }

  def playTurn: Battle[Unit] = {
    update(s => s.copy(turn = s.turn + 1))
    val state = get
    write(s"--- Turn ${state.turn} (Hero: ${state.heroHp} HP | Goblin: ${state.monsterHp} HP) ---")

    if (state.heroHp < 15 && state.potions > 0) usePotion else heroAttack
    monsterAttack
  }

  def battle: Battle[Unit] = {
    write("A wild Goblin appears!")
    while (true) playTurn
  }

  @main
  def runBattleExample(): Unit = {
    val rules   = Rules(
      hero = Stats(attack = 12, defense = 4),
      monster = Stats(attack = 10, defense = 3),
      heroMaxHp = 50,
      potionHeal = 20
    )
    val initial = BattleState(heroHp = 50, monsterHp = 60, potions = 2, turn = 0)

    val (log, result) = Logic.run(initial, rules)(battle)

    log.foreach(println)
    println()
    result match {
      case Left(Outcome.Victory(turn)) => println(s"Victory on turn $turn!")
      case Left(Outcome.Defeat(turn))  => println(s"Defeat on turn $turn...")
      case Right(_)                    => ()
    }
  }
}
