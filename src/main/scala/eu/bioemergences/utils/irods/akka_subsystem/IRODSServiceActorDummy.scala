package eu.bioemergences.utils.irods.akka_subsystem

import java.io.OutputStream

import akka.actor.{Actor, ReceiveTimeout}
import akka.event.Logging
import eu.bioemergences.utils.irods.IRODSService._
import eu.bioemergences.utils.irods.IRODSServicePool.KillMe
import eu.bioemergences.utils.irods.ServiceKey.Sender
import eu.bioemergences.utils.irods._

import scala.concurrent.duration.Duration
import scala.util.Try

class IRODSServiceActorDummy extends Actor {
  val log = Logging(context.system, this)

  private var maybeOS: Option[OutputStream] = None

  private val timeout = 2 //two minutes until death

  override def preStart() = {
    context.setReceiveTimeout(Duration.create(timeout, "minutes"))
    super.preStart()
  }

  private def writeData(array: Array[Byte]): Either[String, Unit] = {
    Try{
      maybeOS match {
        case Some(os) => Right(os.write(array))
        case None => Left("No file open to write!!")
      }
    }.recover{
      case t: Throwable => Left(s"Unknown exception occured during writing: ${t.getMessage}")
    }.getOrElse(Left("I have no idea how i reached this error. Check recovery exhaustivity!"))
  }

  private def closeFile(): Unit = {
    maybeOS.foreach{os => os.flush(); os.close()}
    maybeOS = None
  }

  override def receive: PartialFunction[Any, Unit] = {
    implicit def send: Sender = sender().asInstanceOf[Sender]
    PartialFunction{
      case key @ IsFile(path) => key.checkedResult{
        log.debug(s"received $path")
        true
      }

      case key @ MakeFolder(path) => key.checkedResult {
        log.debug(s"making directory $path")
        true
      }

      case key @ MakeFolders(path) => key.checkedResult {
        log.debug(s"making directories for $path")
        true
      }

      case key @ CreateFileWithData(path, data) => key.checkedResult {
        log.debug(s"uploading data to $path")
        Right(())
      }

      case key @ AppendFileWithData(path, data) => key.checkedResult {
        log.debug(s"appending data to ${key.iRODSPath.value}")
        Right(())
      }

      case key @ DeleteFile(path) => key.checkedResult{
        log.debug(s"deleting file $path")
        true
      }

      case key @ IsDirectory(path) => key.checkedResult{
        log.debug(s"checking if path $path points to directory")
        true
      }

      case key @ ReadDataFromFile(path, offset, amount) => key.checkedResult {
        log.debug(s"reading data from file...")
        Right(Array.fill[Byte](amount)(0))
      }

      case key @ GetFileSize(path) => key.checkedResult {
        log.debug(s"grabbing file size")
        Right(0)
      }

      case key @ GetChecksum(path) => key.checkedResult{
        log.debug(s"getting checksum for file $path")
        log.debug(s"got checksum checksum")
        "checksum"
      }

      case key @ GetAbsolutePath(path) => key.checkedResult {
        log.debug(s"getting absolute path")
        path.value
      }

      case key @ ListContents(path) => key.checkedResult {
        log.debug(s"getting files from folder")
        Right(Seq.empty)
      }

      case key @ OpenNewFileForWriting(path) => key.checkedResult {
        Right(())
      }

      case key @ WriteData(data) => key.checkedResult {
        Right(())
      }

      case key @ CloseFile => key.checkedResult {
        Right(())
      }

      case key @ Done => key.checkedResult {
        log.debug(s"shutting down...")
        context.stop(self)
      }

      case resp: ReceiveTimeout =>
        log.error("dying for bush's oil profits!")
        context.parent ! KillMe(self)

      case msg => log.error(s"unknown message received... $msg")
    }
  }

  override def postStop(): Unit = {
    log.debug(s"shutting down iRODS service... byebye!")
    super.postStop()
  }
}
