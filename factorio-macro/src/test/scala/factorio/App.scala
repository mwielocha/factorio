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

class Clients(@named("that") val thatClient: Client, @javax.inject.Named("other") val otherClient: Client)
