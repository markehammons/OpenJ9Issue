package eu.bioemergences.utils.irods.akka_subsystem

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{Actor, ActorRef, Props, Terminated}
import akka.event.Logging
import eu.bioemergences.utils.irods.IRODSServicePool._
import eu.bioemergences.utils.irods.ServiceKey.Sender
import eu.bioemergences.utils.irods.{IRODSPath, IRODSService}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

class IRODSServicePoolActor extends Actor {
  implicit private val executionContext: ExecutionContext = context.dispatcher
  private val log = Logging(context.system, this)
  private var servicePool: List[ActorRef] = Nil

  private var savedActors: Map[IRODSPath, ActorRef] = Map.empty

  private var savedActorsReverse: Map[ActorRef, IRODSPath] = Map.empty


  private def getActorFromServicePool(): Option[ActorRef] = synchronized {
    val ref = servicePool.headOption
    if (servicePool.nonEmpty) servicePool = servicePool.tail
    ref
  }

  private def returnActor(iRODSService: IRODSService): Unit = {
    savedActorsReverse.get(iRODSService.actorRef).map(p => savedActors += p -> iRODSService.actorRef).getOrElse(servicePool ::= iRODSService.actorRef)
  }

  private def relinquishIRODSService(iRODSService: IRODSService): Unit = {
    returnActor(iRODSService)
  }

  private def gcActorRef(ref: ActorRef) = {
    val maybePath = savedActorsReverse.get(ref)
    maybePath.foreach(path => savedActors -= path)
    savedActorsReverse -= ref
    servicePool = servicePool.filter(_ == ref)
  }

  private def reserveActorForFile(path: IRODSPath) = {
    val service = acquireIRODSService()
    savedActors += path -> service.actorRef
    savedActorsReverse += service.actorRef -> path
  }

  private def retrieveSavedActor(path: IRODSPath): Either[String, IRODSService] = {
    val ref = savedActors.get(path).map(tup => Right(tup)).getOrElse(Left(s"No actor available for the file $path!!"))
    savedActors -= path

    ref.map(r => new IRODSService(r))
  }

  private def killIfInPool(ref: ActorRef) = {
    val actor = servicePool.find(_ == ref).orElse(savedActorsReverse.get(ref).flatMap(savedActors.get))
    actor.foreach(gcActorRef)
    actor.foreach(context.stop)
  }

  def unreserveActorForFile(path: IRODSPath) = {
    val actorRef = savedActors.get(path)
    savedActors -= path
    for(ref <- actorRef) {
      savedActorsReverse -= ref
      servicePool ::= ref
    }
  }

  private def acquireIRODSService(): IRODSService = {
    val service: ActorRef = getActorFromServicePool().getOrElse {
      log.debug("acquiring new actor!!")
      //todo: add and configure pinned dispatcher to improve performance!! https://doc.akka.io/docs/akka/2.5/scala/dispatchers.html#types-of-dispatchers
      val actor = context.system.actorOf(Props[IRODSServiceActorDummy].withDispatcher("irods-dispatcher"), s"IRODSServiceActor-${UUID.randomUUID().toString}")
      context.watch(actor)
      actor
    }

    new IRODSService(service)
  }

  override def receive = {
    implicit def send: Sender = sender().asInstanceOf[Sender]
    PartialFunction {
      case key @ AcquireService => key.checkedResult {
        log.debug("received service acquisition request")
        acquireIRODSService()
      }
      case key @ RelinquishService(irodsService) => key.checkedResult {
        log.debug(s"service $irodsService returned")
        relinquishIRODSService(irodsService)
      }

      case key @ ReserveActorForFile(path) => key.checkedResult {
        reserveActorForFile(path)
      }

      case key @ EndReservationForFile(path) => key.checkedResult {
        unreserveActorForFile(path)
      }

      case key @ RetrieveSavedActor(path) => key.checkedResult {
        retrieveSavedActor(path)
      }

      case key @ Stop => key.checkedResult {
        context.stop(self)
      }
      case Terminated(ref) =>
        log.info(s"actor died! $ref")
        gcActorRef(ref)
      case key@KillMe(ref) => key.checkedResult {
        killIfInPool(ref)
      }
      case m: Any =>
        log.error(s"received unknown message!! $m")
    }
  }

  override def postStop(): Unit = {
    (savedActors.values ++ servicePool).foreach { case actor => context.stop(actor) }
  }
}
