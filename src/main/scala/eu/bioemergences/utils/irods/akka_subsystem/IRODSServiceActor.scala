package eu.bioemergences.utils.irods.akka_subsystem

import java.io.OutputStream

import akka.actor.{Actor, ActorRef, ReceiveTimeout}
import akka.event.Logging
import eu.bioemergences.utils.irods.IRODSService._
import eu.bioemergences.utils.irods.IRODSServicePool.KillMe
import eu.bioemergences.utils.irods.ServiceKey.Sender
import eu.bioemergences.utils.irods._

import scala.concurrent.duration.Duration
import scala.util.Try

class IRODSServiceActor extends Actor {
  val log = Logging(context.system, this)

  private val iRODSAPI = new IRODSAPI()

  private var maybeOS: Option[OutputStream] = None

  private val timeout = 2 //two minutes until death

  override def preStart() = {
    context.setReceiveTimeout(Duration.create(timeout, "minutes"))
    super.preStart()
  }

  private def openNewFileForWriting(path: IRODSPath): Either[String, Unit] = {
    maybeOS match {
      case Some(_) => Left("This service already has a file open! Close it if you wish to write to another file!!")
      case None =>
        iRODSAPI.getFile(path).delete()
        maybeOS = Some(iRODSAPI.getOutputStream(path))
        Right(())
    }
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

  private def createFileWithData(path: IRODSPath, array: Array[Byte]): Either[String, Unit] = {
    Try {
      val f = iRODSAPI.getFile(path)
      if(f.exists())
        f.delete()

      val os = iRODSAPI.getOutputStream(path)
      os.write(array)
      os.close()
      Right(())
    }.recover {
      case t: Throwable =>
        log.error("An error occurred while writing!", t)
        Left(t.getMessage)
    }.get
  }

  private def appendFileWithData(path: IRODSPath, array: Array[Byte]): Either[String, Unit] = {
    Try {
      val os = iRODSAPI.getAppendOutputStream(path)
      os.write(array)
      os.close()
      Right(())
    }.recover {
      case t: Throwable =>
        log.error("An error occurred while appending!", t)
        Left(t.getMessage)
    }.get
  }

  private def readFromFile(path: IRODSPath, offset: Long, length: Int): IRODSTry[Array[Byte]] = {
    Try {
      val is = iRODSAPI.getInputStream(path)
      val data = Array.fill[Byte](length)(0)
      is.skip(offset)
      is.read(data)
      is.close()
      Right(data)
    }.recover {
      case t: Throwable =>
        log.error("An error occurred while reading!", t)
        Left(t.getMessage)
    }.get
  }

  override def receive: PartialFunction[Any, Unit] = {
    implicit def send: Sender = sender().asInstanceOf[Sender]
    PartialFunction{
      case key @ IsFile(path) => key.checkedResult{
        log.debug(s"received $path")
        iRODSAPI.getFile(path).isFile
      }

      case key @ MakeFolder(path) => key.checkedResult {
        log.debug(s"making directory $path")
        iRODSAPI.getFile(path).mkdir()
      }

      case key @ MakeFolders(path) => key.checkedResult {
        log.debug(s"making directories for $path")
        iRODSAPI.getFile(path).mkdirs()
      }

      case key @ CreateFileWithData(path, data) => key.checkedResult {
        log.debug(s"uploading data to $path")
        createFileWithData(path, data)
      }

      case key @ AppendFileWithData(path, data) => key.checkedResult {
        log.debug(s"appending data to ${key.iRODSPath.value}")
        appendFileWithData(path, data)
      }

      case key @ DeleteFile(path) => key.checkedResult{
        log.debug(s"deleting file $path")
        iRODSAPI.getFile(path).delete()
      }

      case key @ IsDirectory(path) => key.checkedResult{
        log.debug(s"checking if path $path points to directory")
        iRODSAPI.getFile(path).isDirectory
      }

      case key @ ReadDataFromFile(path, offset, amount) => key.checkedResult {
        log.debug(s"reading data from file...")
        readFromFile(path, offset, amount)
      }

      case key @ GetFileSize(path) => key.checkedResult {
        log.debug(s"grabbing file size")
        val irodsFile = iRODSAPI.getFile(path)
        val res: IRODSTry[Long] = if (irodsFile.isDirectory) {
          log.error(s"$path is a directory, not a file!!")
          Left(s"$path is a directory, not a file!!")
        } else {
          Right(irodsFile.length())
        }
        res
      }

      case key @ GetChecksum(path) => key.checkedResult{
        log.debug(s"getting checksum for file $path")
        val checksum = iRODSAPI.getChecksum(path)
        log.debug(s"got checksum $checksum")
        checksum
      }

      case key @ GetAbsolutePath(path) => key.checkedResult {
        log.debug(s"getting absolute path")
        iRODSAPI.getFile(path).getAbsolutePath
      }

      case key @ ListContents(path) => key.checkedResult {
        log.debug(s"getting files from folder")
        val irodsFile = iRODSAPI.getFile(path)
        val res: IRODSTry[Traversable[IRODSPath]] = if (irodsFile.isFile) {
          log.error(s"$path is a file, not a directory!!")
          Left(s"$path is a file, not a directory!!")
        } else {
          Right(iRODSAPI.listContents(path))
        }
        res
      }

      case key @ OpenNewFileForWriting(path) => key.checkedResult {
        openNewFileForWriting(path)
      }

      case key @ WriteData(data) => key.checkedResult {
        writeData(data)
      }

      case key @ CloseFile => key.checkedResult {
        closeFile()
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
    iRODSAPI.shutdown()
    super.postStop()
  }
}
