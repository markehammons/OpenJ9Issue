package eu.bioemergences.utils.irods

import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.reflect.ClassTag

trait ActorWrapper[KeyType[_] <: ServiceKey[_]] {
  protected def timeoutDuration: FiniteDuration
  implicit protected def timeout: Timeout = Timeout(timeoutDuration)
  protected def actorRef: ActorRef

//  protected def mkReq[T, U: ClassTag](t: T, uClazz: Class[U])(implicit timeout: Timeout): U = {
//    Await.result(ask(actorRef, t).mapTo[U], timeoutDuration)
//  }

  protected def mkReq[U : ClassTag](key: KeyType[U])(implicit timeout: Timeout): U = {
    Await.result(ask(actorRef, key).mapTo[U], timeoutDuration)
  }
}
