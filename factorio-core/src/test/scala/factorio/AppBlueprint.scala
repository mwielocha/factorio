package factorio

@blueprint
class AppBlueprint {

  @replicated
  val serviceBinder =
    bind[Service].to[ServiceImpl]

  @replicated
  def otherServiceBinder =
    bind[OtherService].to[OtherServiceImpl]

  val getDatabase: Database =
    new Database

}
