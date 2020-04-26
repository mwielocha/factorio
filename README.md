# factorio
Compile time dependency injection framework for Scala

# Basic assumptions
- everything is lazy by default
- everything is a singleton by default
- compile time checking for dependency graph completeness 

# Examples

### Given an app of following components
```scala
class Component

class Repository

class SuperComponent(val component: Component, val repository: Repository)

trait Interface

class DefaultComponent(component: Component, repository: Repository)
  extends SuperComponent(component, repository) with Interface
```

### The boring part (manual assemblers)

```scala
import io.mwielocha.factorio._

// we need an implicit assembly in scope
implicit val assemble: Assembly = Assembly()

// now we can contruct assemblers by hand:

implicit val componentAssembler: Assembler[Component] = 
  Assembler[Component](() => new Component)
implicit val repositoryAssembler: Assembler[Repository] = 
  Assembler[Repository](() => new Repository)
implicit def appAssembler(
  implicit
    componentAssembler: Assembler[Component],
    repositoryAssembler: Assembler[Repository]
): Assembler[SuperComponent] = Assembler[SuperComponent](
  () => new SuperComponent(componentAssembler.assemble, repositoryAssembler.assemble)
)

// the app is ready
val component = assemble[SuperComponent]
```

### The exciting part (auto assemblers)
```scala
import io.mwielocha.factorio.auto._

// mandatory assembly in scope
implicit val assemble: Assembly = Assembly()

// magic
val component = assemble[SuperComponent]
val interface = assemble[Interface]
```
### Compile time checking for missing bindings:
```
[info] Compiling 2 Scala sources to /Users/mwielocha/workspace/factorio/factorio-macro/target/scala-2.13/test-classes ...
[error] /Users/mwielocha/workspace/factorio/factorio-macro/src/test/scala/io/mwielocha/factorio/auto/AutoAssemblySpec.scala:49:25: Cannot construct an instance of [io.mwielocha.factorio.Interface], create custom assembler or provide a public constructor.
[error]     val interface = make[Interface]
[error]
```

### Custom assemblers with auto assembly
```scala
import io.mwielocha.factorio.auto._

// mandatory assembly in scope
implicit val assemble: Assembly = Assembly(

// we can mix both approached for example to create factory methods
val defaultComponent = new DefaultComponent(
  implicitly[Assembler[Component]].assemble,
  implicitly[Assembler[Repository]].assemble
)

implicit val interfaceAssembler: Assembler[Interface] =
  Assembler(() => defaultComponent)
  
val interface = assemble[Interface] // this will yield defaultComponent
```

### Smelting syntax to simplify inteface binding

```scala
implicit val assemble: Assembly = Assembly()

implicit val interfaceAssembler: Assembler[Interface] =
  smelt[Interface].`with`[DefaultComponent]

implicit val componentAssembler: Assembler[SuperComponent] =
  smelt[SuperComponent].`with`[DefaultComponent]
  
val interface = asseble[Interface]
val superComponent = asseble[SuperComponent]
val defaultComponent = asseble[DefaultComponent]

superComponent shouldBe interface
defaultComponent shouldBe interface
```

### Group assemblers into recipes

```scala
trait ComponentRecipe {
  requires: Recipe =>

  implicit val interfaceAssembler: Assembler[Interface] =
    smelt[Interface].`with`[DefaultComponent]

  implicit val componentAssembler: Assembler[SuperComponent] =
    smelt[SuperComponent].`with`[DefaultComponent]

}
```
```scala

implicit val assemble: Assembly = Assembly()

val recipes = new Recipes with ComponentRecipe
import recipes._

val interface = assemble[Interface]
val superComponent = assemble[SuperComponent]
val defaultComponent = assemble[DefaultComponent]
val interfaceComponent = assemble[InterfaceComponent]
```
