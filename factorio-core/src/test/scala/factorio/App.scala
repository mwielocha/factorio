package factorio

import javax.inject.Named

class Database

class Repository(val database: Database)

trait Service {
  def repository: Repository
}

class ServiceImpl(val repository: Repository) extends Service

trait OtherService {
  def repository: Repository
}

class OtherServiceImpl(val repository: Repository) extends OtherService

class App(val service: Service, val otherService: OtherService)

class MultiDatabaseRepository(
  @Named("database") val database: Database,
  @Named("otherDatabase") val otherDatabase: Database
)
