package factorio.`macro`

case class Named[T](value: T, name: Option[String]) {

  override def toString: String = {
    name match {
      case Some(name) =>
        s"$value named $name"
      case None => s"$value"
    }
  }
}
