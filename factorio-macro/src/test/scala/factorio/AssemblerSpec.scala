package factorio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AssemblerSpec extends AnyFlatSpec with Matchers {

  trait CommonRecipe {
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

    val assembler = assemble[App](new Recipe with CommonRecipe)

    val app = assembler()
    app.service.repository shouldBe app.otherService.repository
  }

  it should "assemble an app from a recipe with a simple @provides method" in {

    val database = new Database

    class AppRecipe extends Recipe with CommonRecipe {
      @Provides
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

    class AppRecipe extends Recipe with CommonRecipe {
      @Provides
      def createApp(otherService: OtherService): App =
        new App(service, otherService)
    }

    val assembler = assemble[App](new AppRecipe)

    val app = assembler()

    app.service shouldBe service
    app.otherService.repository should not be (repository)
  }

  it should "assemble an app from a recipe with labels" in {

    val database = new Database
    val otherDatabase = new Database

    class MultiDatabaseRepositoryRecipe extends Recipe {

      @Provides
      @Named("database")
      def getDatabase =
        database

      @Provides
      @Named("otherDatabase")
      def getOtherDatabase =
        otherDatabase
    }

    val assembler = assemble[MultiDatabaseRepository](new MultiDatabaseRepositoryRecipe)

    val repository = assembler()

    repository.database shouldBe database
    repository.otherDatabase shouldBe otherDatabase
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
