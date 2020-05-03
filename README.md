# factorio
Tiny compile time dependency injection framework for Scala

# Basic assumptions
- everything is a singleton by default
- compile time checking for dependency graph correctness 

# Usage

### Basic constructor composition
```scala

import factorio._

class Repository
class Service(val repository: Repository)

class App(service: Service)

val assembler = Assembler[App](Blank)

val app = assembler()

// new App(new Service(new Repository)))

```

### Blueprints

You can configure couplings with a blueprint. 
A blueprint is in short a single class, or a chain of mixed traits 
marked by `@blueprint` annotation that holds provider methods or concrete type bindings:

```scala

import factorio._

class Repository
class Service(val repository: Repository)

class App(service: Service)

@blueprint
class AppBlueprint {
  
  @provides
  def createService(repository: Repository): Service = {
    new Service(repository)
  }
}

val assembler = Assembler[App](new AppRecipe)

val app = assembler()

// val recipe = new AppRecipe
// new App(recipe.createService(new Repository)))

```

You can also simply `bind` implementations to super classes or interfaces:
```scala

import factorio._

class Repository
trait Service
class ServiceImpl(val repository: Repository) extends Service

class App(service: Service)

@blueprint
class AppBlueprint {
  
  val serviceBinder = bind[Service].to[ServiceImpl]

}

val assembler = Assembler[App](new AppBlueprint)

val app = assembler()

// val blueprint = new AppBlueprint
// new App(new ServiceImpl(new Repository)))

```

Blueprints can be composed of various traits but only traits 
that are marked with `@blueprint` will be searched for binders and providers:

```scala

import factorio._

class Database
class Repository(val database: Database)
class Service(val repository: Repository)

class App(service: Service)

@blueprint
trait RepositoryBlueprint {

  @provides
    def createRepository: Repository = {
      new Repository(new Database)
    }
}

@blueprint
trait ServiceBlueprint {
  
  @provides
  def createService(repository: Repository): Service = {
    new Service(repository)
  }
}

class AppBlueprint extends ServiceBlueprint with RepositoryBlueprint

val assembler = Assembler[App](new AppBlueprint)

val app = assembler()

// val blueprint = new AppBlueprint
// new App(blueprint.createService(blueprint.createRepository)))

```

Blueprint traits will be searched in order of natural scala mixin folding. This means that earlier traits in mixin chain
will override binding of later ones if they define bindings for the same type. This is usefull if you want to rewrite bindings,
for example, for test scope, but you don't want to decompose the original blueprint chain:

```scala

import factorio._

class Database
trait Repository
class RepositoryImpl(val database: Database) extends Repository
class Service(val repository: Repository)

class App(service: Service)

@blueprint
trait RepositoryBlueprint {

  @provides
    def createRepository: Repository = {
      new RepositoryImpl(new Database)
    }
}

@blueprint
trait ServiceBlueprint {
  
  @provides
  def createService(repository: Repository): Service = {
    new Service(repository)
  }
}

class AppBlueprint extends ServiceBlueprint with RepositoryBlueprint

val assembler = Assembler[App](new AppBlueprint)

val app = assembler()

// val blueprint = new AppBlueprint
// new App(blueprint.createService(blueprint.createRepository)))

@blueprint
class DummyRepository extends Repository

trait TestBlueprint {
  
  val dummyRepositoryBinder = bind[Repository].to[DummyRepository]
}

// this will overwirte reposiry provider from `RepositoryBlueprint`
class TestAppBlueprint extends AppBlueprint with TestBlueprint

val testAssembler = Assembler[App](new TestAppBlueprint)

val testApp = testAssembler()

// val blueprint = new TestAppBlueprint
// new App(blueprint.createService(new DummyRepository)))
```
You can also provide multiple implementations for the same types with the `@named` discriminator:
 ```scala
 
 import factorio._
 
 class Repository

 trait Service
 class ServiceImpl(val repository: Repository) extends Service
 
 class App(
  @named("that") thatService: Service, 
  @named("other") otherService: Service
)
 
 @blueprint
 class AppBlueprint {
   
   @named("that")
   def thatService(repository: Repository) =
     new ServiceImpl(repository) 
   
   @named("other")
   def otherService(repository: Repository) =
     new ServiceImpl(repository)

 }
 
 val assembler = Assembler[App](new AppBlueprint)
 
 val app = assembler()
 
 // val blueprint = new AppBlueprint
 // val repository = new Repository
 // new App(
 //   blueprint.thatService(repository), 
 //   blueprint.otherService(repository))
 // )
 
 ```
Scala style `@named` annotation can be replaced with `javax.inject.Named` 
### Non-singleton components
Following on the assumption that everything is a `Singleton` we need to reverse `javax.inject` logic and introduce a non-singleton annotation `@replicated`:
```scala

import factorio._

@replicated
class Repository
class Service(val repository: Repository)

class App(service: Service)

val assembler = Assembler[App](Blank)

val app = assembler()

// repository is now a def so new instance is injected to every parent
// def repository = new Repository 
// new App(new Service(repository)))

```
### Dependency graph corectness
Factorio will validate the corectness of the dependency graph in compile time and will abort compilation on any given error:
```scala
import factorio._

class CircularDependency(val dependency: OuterCircularDependency)

class OuterCircularDependency(val dependency: CircularDependency)

Assembler[CircularDependency](Blank)

//[error] [Factorio]: Circular dependency detected: factorio.CircularDependency -> factorio.OuterCircularDependency -> factorio.CircularDependency
//[error]
//[error]     Assembler[CircularDependency](EmptyRecipe)
//[error]                                 ^
//[error] one error found

```
or if there is no constructor nor recipe for the given type:
```scala

import factorio._

class Repository
trait Service
class ServiceImpl(val repository: Repository) extends Service

class App(service: Service)

Assembler[App](Blank)

//[error] [Factorio]: Cannot construct an instance of [factorio.Service]
//[error]
//[error]     Assembler[App](EmptyRecipe)
//[error]                  ^
//[error] one error found
```



