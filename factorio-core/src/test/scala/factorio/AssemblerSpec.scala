package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblerSpec extends AnyFlatSpec with Matchers {

  @blueprint
  trait ServiceBlueprint {

    val serviceBinder = bind[Service].to[ServiceImpl]
    val otherServiceBinder = bind[OtherService].to[OtherServiceImpl]

  }

  "Assembly macro" should "assemble a component" in {

    val assembler = assemble[Repository](Blank)

    assembler()
    succeed
  }

  it should "assemble a simple app" in {

    val assembler = assemble[App](new ServiceBlueprint {})

    val app = assembler()
    app.service.repository shouldBe app.otherService.repository
  }

  it should "assemble an app from a blueprint with a simple @provides method" in {

    val database = new Database

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      def getDatabase: Database =
        database
    }

    val assembler = assemble[App](new AppBlueprint)

    val app = assembler()

    app.service.repository.database shouldBe database
    app.otherService.repository.database shouldBe database
  }

  it should "assemble an app from a blueprint with a complex @provides method" in {

    val database = new Database
    val repository = new Repository(database)
    val service = new ServiceImpl(repository)

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      def createApp(otherService: OtherService): App =
        new App(service, otherService)
    }

    val assembler = assemble[App](new AppBlueprint)

    val app = assembler()

    app.service shouldBe service
    app.otherService.repository should not be (repository)
  }

  it should "assemble an app from a blueprint with named components" in {

    val database = new Database
    val otherDatabase = new Database

    @blueprint
    class MultiDatabaseRepositoryBlueprint {

      @provides
      @named("database")
      def getDatabase =
        database

      @provides
      @named("otherDatabase")
      def getOtherDatabase =
        otherDatabase
    }

    val assembler = assemble[MultiDatabaseRepository](new MultiDatabaseRepositoryBlueprint)

    val repository = assembler()

    repository.database shouldBe database
    repository.otherDatabase shouldBe otherDatabase
  }

  it should "assemble an app from a blueprint with named, binded components" in {

    val thatClient: Client = new ThatClientImpl

    @blueprint
    class ServicesBlueprint {

      @provides
      @named("that")
      def getThatClient =
        thatClient

      @named("other")
      val otherClientBinder = bind[Client].to[OtherClientImpl]
    }

    val assembler = assemble[Clients](new ServicesBlueprint)

    val clients = assembler()

    clients.thatClient shouldBe thatClient
    clients.otherClient should not be (thatClient)
    clients.otherClient.getClass shouldBe classOf[OtherClientImpl]
  }

  it should "assemble an app from a blueprint with replicated components" in {

    @blueprint
    class ReplicatedServiceBlueprint extends ServiceBlueprint {
      val bindRepository = bind[Repository].to[ReplicatedRepository]
    }

    val assembler = assemble[App](new ReplicatedServiceBlueprint)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a blueprint with replicated, provided component" in {

    @blueprint
    class ReplicatedRepositoryBlueprint extends ServiceBlueprint {

      @provides
      @replicated
      def newRepository(database: Database) =
        new Repository(database)
    }

    val assembler = assemble[App](new ReplicatedRepositoryBlueprint)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a blueprint with multiple composed annotations" in {

    @blueprint
    class AppBlueprint extends ServiceBlueprint {

      @provides
      @replicated
      def getDatabase: Database =
        new Database
    }

    val assembler = assemble[App](new AppBlueprint)

    val app = assembler()

    app.service.repository.database should not be (app.otherService.repository)
  }

  it should "not compile when circular dependency exists" in {
    //assemble[CircularDependency](Blank)
    assertDoesNotCompile("assemble[CircularDependency](EmptyBlueprint)")
  }

  it should "not compile when no binding was provided for an interface" in {
    //assemble[App](Blank)
    assertDoesNotCompile("assemble[App](EmptyBlueprint)")
  }
}
