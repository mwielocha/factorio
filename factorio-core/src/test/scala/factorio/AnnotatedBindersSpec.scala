package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnnotatedBindersSpec extends AnyFlatSpec with Matchers {

  @blueprint
  @binds[Service to ServiceImpl]
  @binds[OtherService to OtherServiceImpl]
  trait ServiceBlueprint {}

  "Assembly macro" should "assemble a simple app" in {

    val assembler = Assembler[App](new ServiceBlueprint {})

    val app = assembler()
    app.service.repository shouldBe app.otherService.repository
  }

  it should "assemble an app from a blueprint with named, binded components" in {

    val thatClient: Client = new ThatClientImpl

    @blueprint
    @binds[Client to OtherClientImpl](
      named("other")
    )
    class ServicesBlueprint {

      @provides
      @named("that")
      def getThatClient =
        thatClient
    }

    val assembler = Assembler[Clients](new ServicesBlueprint)

    val clients = assembler()

    clients.thatClient shouldBe thatClient
    clients.otherClient should not be (thatClient)
    clients.otherClient.getClass shouldBe classOf[OtherClientImpl]
  }

  it should "assemble an app from a blueprint with replicated components" in {

    @blueprint
    @binds[Repository to ReplicatedRepository]
    class ReplicatedServiceBlueprint extends ServiceBlueprint

    val assembler = Assembler[App](new ReplicatedServiceBlueprint)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "honor `overrides` property when analyzing binders" in {

    @blueprint
    @binds[Member to MemberImpl](replicated, overrides)
    trait MemberBlueprint {}

    @blueprint
    @binds[Member to OtherMemberImpl]
    trait OtherMemberBlueprint

    val assembler = Assembler[Package](new MemberBlueprint with OtherMemberBlueprint {})

    val instance: Package = assembler()

    instance.member.getClass shouldBe classOf[MemberImpl]
  }

  it should "reuse a more specific class in a binding" in {

    @blueprint
    @binds[Service to ServiceImpl]
    class SingleServiceAppBlueprint

    val assembler = Assembler[SingleServiceApp](new SingleServiceAppBlueprint)

    val app = assembler()

    app.service shouldBe app.serviceImpl

  }

  it should "assemble an app with `@binds` annotation" in {

    @blueprint
    @binds[Service to ServiceImpl]
    @binds[OtherService to OtherServiceImpl]
    class AppBlueprint {}

    val assembler = Assembler[App](new AppBlueprint)

    val app = assembler()

    app.service.getClass() shouldBe classOf[ServiceImpl]
    app.otherService.getClass() shouldBe classOf[OtherServiceImpl]

  }

  it should "assemble an app with `@binds` annotation and name" in {

    @blueprint
    @binds[Client to ThatClientImpl](
      named(Clients.that)
    )
    @binds[Client to OtherClientImpl](
      named("other")
    )
    class ClientsBlueprint {}

    val assembler = Assembler[Clients](new ClientsBlueprint)

    val clients = assembler()

    clients.thatClient.getClass() shouldBe classOf[ThatClientImpl]
    clients.otherClient.getClass() shouldBe classOf[OtherClientImpl]

  }

  it should "honor `replicated` argument of `@binds` annotation" in {

    @blueprint
    @binds[Service to ServiceImpl](replicated)
    class SingleServiceAppBlueprint {}

    val assembler = Assembler[SingleServiceApp](new SingleServiceAppBlueprint)

    val app = assembler()

    app.service should not be (app.serviceImpl)

  }
}
