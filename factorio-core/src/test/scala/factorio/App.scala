package factorio

class Database

class Repository(val database: Database)

trait Service {
  def repository: Repository
}

class ServiceImpl(val repository: Repository) extends Service

@replicated
class ReplicatedRepository(database: Database) extends Repository(database)

trait OtherService {
  def repository: Repository
}

class OtherServiceImpl(val repository: Repository) extends OtherService

class App(val service: Service, val otherService: OtherService)

class MultiDatabaseRepository(
  @named("database") val database: Database,
  @named("otherDatabase") val otherDatabase: Database
)

trait Client

class ThatClientImpl extends Client
class OtherClientImpl(val repository: Repository) extends Client

object Clients {
  final val that = "that"
  final val other = "other"
}

class Clients(@named("that") val thatClient: Client, @javax.inject.Named("other") val otherClient: Client)

trait Member
class MemberImpl extends Member
class OtherMemberImpl extends Member

class Package(val member: Member)

class SingleServiceApp(val service: Service, val serviceImpl: ServiceImpl)

object StableIdentifierApp {

  final val thatClient = "thatClient"
  final val otherClient = "otherClient"

}

class StableIdentifierApp(@named(name = StableIdentifierApp.thatClient) val client: Client)

class Box[T](val value: T)

class BoxedApp(val serviceBox: Box[Service])

object BoxedApp {
  type AppService = Service
  type ServiceBox = Box[AppService]
}
