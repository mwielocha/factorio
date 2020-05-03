package factorio

class CircularDependency(val dependency: OuterCircularDependency)

class OuterCircularDependency(val dependency: CircularDependency)
