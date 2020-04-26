package io.mwielocha.factorio

class Component

class Repository

class SuperComponent(val component: Component, val repository: Repository)

trait Interface

class DefaultComponent(component: Component, repository: Repository)
  extends SuperComponent(component, repository) with Interface

class TestApp(val superComponent: SuperComponent, val repository: Repository)

class CircularComponent(val component: CircularComponent)

class MultiParamListComponent(val component: SuperComponent)(implicit val repository: Repository)

class InterfaceComponent(val component: Interface)
