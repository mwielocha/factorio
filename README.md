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

val assemble = assembler[App](EmptyRecipe)

val app = assemble()

// new App(new Service(new Repository)))

```

### Recipies

You can configure coupling with a recipe. Recipe is a class that extends `factorio.Recipe` and contains a set or rules on how to construct given components.
There are two ways of provind coupling rules:

```scala

import factorio._

class Repository
class Service(val repository: Repository)

class App(service: Service)

class AppRecipe extends Recipe {
  
  @Provides
  def createService(repository: Repository): Service = {
    new Service(repository)
  }
}

val assemble = assembler[App](new AppRecipe)

val app = assemble()

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

class AppRecipe extends Recipe {
  
  val serviceBinder = bind[Service].to[ServiceImpl]

}

val assemble = assembler[App](new AppRecipe)

val app = assemble()

// val recipe = new AppRecipe
// new App(new ServiceImpl(new Repository)))

```
You can also provide multiple implementations with the `@Named` discriminator:
 ```scala
 
 import factorio._
 
 class Repository

 trait Service
 class ServiceImpl(val repository: Repository) extends Service
 
 class App(
  @Named("that") thatService: Service, 
  @Named("other") otherService: Service
)
 
 class AppRecipe extends Recipe {
   
   @Named("that")
   def thatService(repository: Repository) =
     new ServiceImpl(repository) 
   
   @Named("other")
   def otherService(repository: Repository) =
     new ServiceImpl(repository)

 }
 
 val assemble = assembler[App](new AppRecipe)
 
 val app = assemble()
 
 // val recipe = new AppRecipe
 // val repository = new Repository
 // new App(
 //   recipe.thatService(repository), 
 //   recipe.otherService(repository))
 // )
 
 ```
### Dependency graph corectness
Factorio will validate the corectness of the dependency graph in compile time and will abort compilation on any given error:
```scala
import factorio._

class CircularDependency(val dependency: OuterCircularDependency)

class OuterCircularDependency(val dependency: CircularDependency)

val assemble = assembler[CircularDependency](EmptyRecipe)

//[error] [Factorio]: Circular dependency detected: factorio.CircularDependency -> factorio.OuterCircularDependency -> factorio.CircularDependency
//[error]
//[error]     assemble[CircularDependency](EmptyRecipe)
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

assembler[App](EmptyRecipe)

//[error] [Factorio]: Cannot construct an instance of [factorio.Service]
//[error]
//[error]     assemble[App](EmptyRecipe)
//[error]                  ^
//[error] one error found
```



