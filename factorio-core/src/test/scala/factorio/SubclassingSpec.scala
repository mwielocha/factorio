package factorio

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SubclassingSpec extends AnyFlatSpec with Matchers {

  val counter = new AtomicInteger(1)

  trait Thing

  class RealThing extends Thing {
    private val id = counter.getAndIncrement()

    override def toString: String = s"RealThing($id)"

  }

  class WantSomething(
    val some: Thing,
    val real: RealThing
  )

  @blueprint
  class TestBlueprint {

    @provides
    def thing: Thing = new RealThing // kind of an equivalent for @binds[Thing to RealThing]
  }

  "Assembly macro" should "bind `some: Thing` and `real: RealThing` to the provided instance of `RealThing`" in {

    val assembler = Assembler[WantSomething](new TestBlueprint)
    val assembled = assembler()

    assert(assembled.some == assembled.real)
  }

}
