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

You can configure coupling with a recipe. Recipe is a class that extends `factorio.Recipe` and contains a set or rule on how to construct given components.
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



