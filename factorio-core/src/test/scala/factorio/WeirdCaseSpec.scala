package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object WeirdCaseDomain {

  trait Thing1
  class RealThing1 extends Thing1

  class WantSomething(
    val some: Thing1
  )

}

class WeirdCaseSpec extends AnyFlatSpec with Matchers {

  import WeirdCaseDomain._

  @blueprint
  class TestBlueprint {

    @provides
    def thing: Thing1 = new RealThing1
  }

  "Assembly macro" should "assemble a component" in {

//    compilation fails if uncommented
//    val assembler = Assembler[WantSomething](new TestBlueprint)
//    assembler()

    succeed
  }

}
