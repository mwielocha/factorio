package io.mwielocha.factorio

import javax.inject.Named

import scala.concurrent.ExecutionContext

class Component

class Repository

class SuperComponent(val component: Component, val repository: Repository)

trait Interface

class DefaultComponent(component: Component, repository: Repository) extends SuperComponent(component, repository) with Interface

class TestApp(val superComponent: SuperComponent, val repository: Repository)

class CircularComponent(val component: OtherCircularComponent)

class OtherCircularComponent(component: CircularComponent)

class MultiParamListComponent(val component: SuperComponent)(implicit val repository: Repository)

class LabeledApp(@Named("that") val that: Component, @Named("other") val other: Component)
