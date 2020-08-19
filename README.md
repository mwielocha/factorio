Tiny compile time dependency injection framework for Scala
# factorio [![Build Status](https://travis-ci.com/mwielocha/factorio.svg?branch=master)](https://travis-ci.com/mwielocha/factorio) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.mwielocha/factorio-core_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.mwielocha/factorio-core_2.13)

# Basic assumptions
- everything is a singleton by default
- compile time checking for dependency graph correctness 

# Usage

### Installation

```scala
val factorioVersion = "0.2.0"

libraryDependencies ++= Seq(
  "io.mwielocha" %% "factorio-core" % factorioVersion,
  "io.mwielocha" %% "factorio-annotations" % factorioVersion,
  "io.mwielocha" %% "factorio-macro" % factorioVersion % "provided"
)

```

### Options

For debugging puproses it's possible to enable compiler log that will print out generated assembler code:

``` scala
// in project
scalacOptions ++= Seq(
    "-Xmacro-settings:factorio-verbose"
  )
```

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
class Blueprint {
  
  @provides
  def createService(repository: Repository): Service = {
    new Service(repository)
  }
}

val assembler = Assembler[App](new Blueprint)

val app = assembler()

// val blueprint = new Blueprint
// new App(blueprint.createService(new Repository)))

```

You can also simply `bind` implementations to super classes or interfaces:
```scala

import factorio._

class Repository
trait Service
class ServiceImpl(val repository: Repository) extends Service

class App(service: Service)

@blueprint
@binds[Service to ServiceImpl]
class Blueprint { }

val assembler = Assembler[App](new Blueprint)

val app = assembler()

// val blueprint = new Blueprint
// new App(new ServiceImpl(new Repository)))

```

An alternative `bind` syntax, more similar to existing runtime based di frameworks:
```scala

import factorio._

class Repository
trait Service
class ServiceImpl(val repository: Repository) extends Service

class App(service: Service)

@blueprint
class Blueprint {
  
  val serviceBinder = bind[Service].to[ServiceImpl]

}

val assembler = Assembler[App](new Blueprint)

val app = assembler()

// val blueprint = new Blueprint
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

class Blueprint extends ServiceBlueprint with RepositoryBlueprint

val assembler = Assembler[App](new Blueprint)

val app = assembler()

// val blueprint = new Blueprint
// new App(blueprint.createService(blueprint.createRepository)))

```

Blueprint traits will be searched in order of natural scala mixin linearization. This makes it tricky to override binders and providers by just the order of mixin-in traits.
To make an explicit override you can use `@overrides` annotation that will always make the selected binder or provider a priority one. 
Bear in mind that having multiple configurations for one type yields a warning but two configurations with `@overrides` annotation will yield a compile time error.
Note: it is advised to only use `@overrides` in tests, never in production code.
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

trait Blueprint extends ServiceBlueprint with RepositoryBlueprint

val assembler = Assembler[App](new Blueprint {})

val app = assembler()

// val blueprint = new AppBlueprint
// new App(blueprint.createService(blueprint.createRepository)))


class DummyRepository extends Repository

@blueprint
@binds[Repository to DummyRepository]
trait TestRepositoryBlueprint

// this will overwirte reposiry provider from `RepositoryBlueprint`
trait TestBlueprint extends TestRepositoryBlueprint with Blueprint 

val testAssembler = Assembler[App](new TestBlueprint {})

val testApp = testAssembler()

// val blueprint = new TestBlueprint
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
   
   @provides
   @named("that")
   def thatService(repository: Repository) =
     new ServiceImpl(repository) 
   
   @provides
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
### Dependency graph correctness
Factorio will validate the corectness of the dependency graph in compile time and will abort compilation on any given error:
```scala
import factorio._

class CircularDependency(val dependency: OuterCircularDependency)

class OuterCircularDependency(val dependency: CircularDependency)

Assembler[CircularDependency](Blank)

//[error] [Factorio]: Circular dependency detected: factorio.CircularDependency -> factorio.OuterCircularDependency -> factorio.CircularDependency
//[error]
//[error]     Assembler[CircularDependency](Blank)
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
//[error]     Assembler[App](Blank)
//[error]                  ^
//[error] one error found
```



