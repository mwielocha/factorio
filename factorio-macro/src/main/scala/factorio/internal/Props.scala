package factorio.internal

private[internal] case class Props(
  name: Option[String] = None,
  repl: Boolean = false,
  root: Boolean = false
) {

  def ||(other: Props) =
    Props(
      name.orElse(other.name),
      repl || other.repl,
      root || other.root
    )

}
