package factorio.internal

case class Settings(
  verbose: Boolean
)

object Settings {

  private final val `factorio-verbose` = "factorio-verbose"

  def apply(settings: List[String]): Settings =
    Settings(
      settings.contains(`factorio-verbose`)
    )

}
