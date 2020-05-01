package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblerSpec extends AnyFlatSpec with Matchers {

  trait ServiceRecipe {
    requires: Recipe =>

    val serviceBinder = bind[Service].to[ServiceImpl]
    val otherServiceBinder = bind[OtherService].to[OtherServiceImpl]

  }

  "Assembly macro" should "assemble a component" in {

    val assembler = assemble[Repository](EmptyRecipe)

    assembler()
    succeed
  }

  it should "assemble a simple app" in {

    val assembler = assemble[App](new Recipe with ServiceRecipe)

    val app = assembler()
    app.service.repository shouldBe app.otherService.repository
  }

  it should "assemble an app from a recipe with a simple @provides method" in {

    val database = new Database

    class AppRecipe extends Recipe with ServiceRecipe {
      @provides
      def getDatabase: Database =
        database
    }

    val assembler = assemble[App](new AppRecipe)

    val app = assembler()

    app.service.repository.database shouldBe database
    app.otherService.repository.database shouldBe database
  }

  it should "assemble an app from a recipe with a complex @provides method" in {

    val database = new Database
    val repository = new Repository(database)
    val service = new ServiceImpl(repository)

    class AppRecipe extends Recipe with ServiceRecipe {
      @provides
      def createApp(otherService: OtherService): App =
        new App(service, otherService)
    }

    val assembler = assemble[App](new AppRecipe)

    val app = assembler()

    app.service shouldBe service
    app.otherService.repository should not be (repository)
  }

  it should "assemble an app from a recipe with named components" in {

    val database = new Database
    val otherDatabase = new Database

    class MultiDatabaseRepositoryRecipe extends Recipe {

      @provides
      @named("database")
      def getDatabase =
        database

      @provides
      @named("otherDatabase")
      def getOtherDatabase =
        otherDatabase
    }

    val assembler = assemble[MultiDatabaseRepository](new MultiDatabaseRepositoryRecipe)

    val repository = assembler()

    repository.database shouldBe database
    repository.otherDatabase shouldBe otherDatabase
  }

  it should "assemble an app from a recipe with named, binded components" in {

    val thatClient: Client = new ThatClientImpl

    class ServicesRecipe extends Recipe {

      @provides
      @named("that")
      def getThatClient =
        thatClient

      @named("other")
      val otherClientBinder = bind[Client].to[OtherClientImpl]
    }

    val assembler = assemble[Clients](new ServicesRecipe)

    val clients = assembler()

    clients.thatClient shouldBe thatClient
    clients.otherClient should not be (thatClient)
    clients.otherClient.getClass shouldBe classOf[OtherClientImpl]
  }

  it should "assemble an app from a recipe with replicated components" in {

    class ReplicatedServiceRecipe extends Recipe with ServiceRecipe {
      val bindRepository = bind[Repository].to[ReplicatedRepository]
    }

    val assembler = assemble[App](new ReplicatedServiceRecipe)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a recipe with replicated, provided component" in {

    class ReplicatedRepositoryRecipe extends Recipe with ServiceRecipe {

      @provides
      @replicated
      def newRepository(database: Database) =
        new Repository(database)
    }

    val assembler = assemble[App](new ReplicatedRepositoryRecipe)

    val app = assembler()

    app.service.repository shouldNot be(app.otherService.repository)
    app.service.repository.database shouldBe app.otherService.repository.database
  }

  it should "assemble an app from a recipe with multiple composed annotations" in {

    class AppRecipe extends Recipe with ServiceRecipe {
      @provides
      @replicated
      def getDatabase: Database =
        new Database
    }

    val assembler = assemble[App](new AppRecipe)

    val app = assembler()

    app.service.repository.database should not be (app.otherService.repository)
  }

  it should "not compile when circular dependency exists" in {
    //assemble[CircularDependency](EmptyRecipe)
    assertDoesNotCompile("assemble[CircularDependency](EmptyRecipe)")
  }

  it should "not compile when no binding was provided for an interface" in {
    //assemble[App](EmptyRecipe)
    assertDoesNotCompile("assemble[App](EmptyRecipe)")
  }
}
