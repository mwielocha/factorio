package factorio.internal

case class Settings(
  debug: Boolean,
  verbose: Boolean
)

object Settings {

  private final val `factorio-debug` = "factorio-debug"
  private final val `factorio-verbose` = "factorio-verbose"

  def apply(settings: List[String]): Settings =
    Settings(
      settings.contains(`factorio-debug`),
      settings.contains(`factorio-verbose`)
    )

}
