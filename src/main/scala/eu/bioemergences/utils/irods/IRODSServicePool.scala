package eu.bioemergences.utils.irods

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{ActorRef, ActorSystem, Cancellable, Props, TypedActor, TypedProps}
import akka.event.Logging
import eu.bioemergences.utils.NEQ
import eu.bioemergences.utils.irods.IRODSService.IRODSServiceKey
import eu.bioemergences.utils.irods.IRODSServicePool._
import eu.bioemergences.utils.irods.akka_subsystem.{IRODSServiceActor, IRODSServicePoolActor}

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

class IRODSServicePool(protected val actorRef: ActorRef) extends ActorWrapper[IRODSServicePoolKey] {
  type ServiceUser[T] = IRODSService => T

  def acquireService(): IRODSService = {
    mkReq(AcquireService)
  }

  def useService[T: ClassTag](fn: IRODSService => T)(implicit neq: NEQ[IRODSService, T]) = {
    val service = acquireService()
    handleServiceUser(service, fn, relinquishService)
  }


  private def handleServiceUser[T: ClassTag](service: IRODSService, fn: ServiceUser[T], relinquishFn: IRODSService => Unit): T = {
    val r = Try{
      fn(service)
    }
    relinquishFn(service)

    r match {
      case Success(s) => s
      case Failure(t: Exception) =>
        throw t
      case Failure(t: Error) =>
        this.stop()
        throw t
    }
  }


  def useReservedService[T: ClassTag](reservationName: IRODSPath)(fn: ServiceUser[T])(implicit nEQ: NEQ[IRODSService, T]) = {
    val service = retrieveSavedActor(reservationName)
    val collapsedEither = service.fold(cause => throw new NoReservationException(cause), identity)
    handleServiceUser(collapsedEither, fn, relinquishService)
  }

  def endReservationForFile(path: IRODSPath): Unit = {
    mkReq(EndReservationForFile(path))
  }

  def relinquishService(iRODSService: IRODSService): Unit = {
    mkReq(RelinquishService(iRODSService))
  }

  def saveActorForFile(path: IRODSPath): Unit = {
    mkReq(ReserveActorForFile(path))
  }

  def retrieveSavedActor(path: IRODSPath): IRODSTry[IRODSService] = {
    mkReq(RetrieveSavedActor(path))
  }

  def stop(): Unit = {
    mkReq(Stop)
  }

  override protected val timeoutDuration = Duration(20000, "milliseconds")
}

object IRODSServicePool {
  def apply(actorSystem: ActorSystem): IRODSServicePool = {
    val actorRef = actorSystem.actorOf(Props[IRODSServicePoolActor], s"IRODSServicePool")

    new IRODSServicePool(actorRef)
  }

  trait IRODSServicePoolKey[T] extends ServiceKey[T]
  case object AcquireService extends IRODSServicePoolKey[IRODSService]
  case class RelinquishService(iRODSService: IRODSService) extends IRODSServicePoolKey[Unit]
  case class ReserveActorForFile(iRODSPath: IRODSPath) extends IRODSServicePoolKey[Unit]
  case class RetrieveSavedActor(iRODSPath: IRODSPath) extends IRODSServicePoolKey[IRODSTry[IRODSService]]
  case class EndReservationForFile(iRODSPath: IRODSPath) extends IRODSServicePoolKey[Unit]
  case object Stop extends IRODSServicePoolKey[Unit]
  case class KillMe(ref: ActorRef) extends IRODSServicePoolKey[Unit]
}
