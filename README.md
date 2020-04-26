# factorio
Compile time dependency injection framework for Scala

# Basic assumptions
- everything is lazy by default
- everything is a singleton by default
- compile time checking for dependency graph completeness 

# Examples

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
