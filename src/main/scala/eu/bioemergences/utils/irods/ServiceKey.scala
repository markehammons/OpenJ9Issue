package eu.bioemergences.utils.irods

import akka.actor.ActorRef
import eu.bioemergences.utils.irods.ServiceKey.Sender

import scala.concurrent.Future

trait ServiceKey[T] {
  def checkedResult(res: T)(implicit sender: Sender): Unit = sender ! res
}

object ServiceKey {
  //Abstract type, for tagging the sender ActorRef
  type SenderTag

  type Sender = ActorRef with SenderTag
}