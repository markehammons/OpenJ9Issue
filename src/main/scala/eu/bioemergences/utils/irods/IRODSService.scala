package eu.bioemergences.utils.irods

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import eu.bioemergences.utils.irods.IRODSService._

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

class IRODSService(protected[irods] val actorRef: ActorRef) extends ActorWrapper[IRODSServiceKey] {
  protected val timeoutDuration = Duration.create(15000, "milliseconds")

  def isFile(path: IRODSPath): Boolean = {
    mkReq(IsFile(path))
  }

  def openNewFileForWrite(path: IRODSPath): Either[String, Unit] = {
    mkReq(OpenNewFileForWriting(path))
  }

  def writeData(data: Array[Byte]): Unit = {
    mkReq(WriteData(data)) match {
      case Left(v) => throw new Exception(v)
      case _ => ()
    }
  }

  def closeFile(): Unit = {
    mkReq(CloseFile)
  }

  def writeToNewFile(path: IRODSPath, data: Array[Byte]): Either[String, Unit] = {
    mkReq(CreateFileWithData(path, data))
  }

  def appendToFile(path: IRODSPath, data: Array[Byte]): Either[String, Unit] = {
    mkReq(AppendFileWithData(path, data))
  }

  def makeDirectory(path: IRODSPath): Boolean = {
    mkReq(MakeFolder(path))
  }

  def makeDirectories(path: IRODSPath): Boolean = {
    mkReq(MakeFolders(path))
  }

  def deleteFile(path: IRODSPath): Boolean = {
    mkReq(DeleteFile(path))
  }

  def isDirectory(path: IRODSPath): Boolean = {
    mkReq(IsDirectory(path))
  }

  def readFromFile(path: IRODSPath, offset: Long, amount: Int): IRODSTry[Array[Byte]] = {
    mkReq(ReadDataFromFile(path, offset, amount))
  }

  def getFileSize(path: IRODSPath): IRODSTry[Long] = {
    mkReq(GetFileSize(path))
  }

  def listContents(path: IRODSPath): IRODSTry[Traversable[IRODSPath]] = {
    mkReq(ListContents(path))
  }

  def getChecksum(path: IRODSPath): String = mkReq(GetChecksum(path))

  def stop(): Unit = {
    mkReq(Done)
  }
}

object IRODSService {
  trait IRODSServiceKey[U] extends ServiceKey[U]
  case object CloseFile extends IRODSServiceKey[Unit]
  case object Done extends IRODSServiceKey[Unit]

  //SIMPLE OPS
  case class IsFile(iRODSPath: IRODSPath) extends IRODSServiceKey[Boolean]
  case class MakeFolder(iRODSPath: IRODSPath) extends IRODSServiceKey[Boolean]
  case class MakeFolders(iRODSPath: IRODSPath) extends IRODSServiceKey[Boolean]
  case class DeleteFile(iRODSPath: IRODSPath) extends IRODSServiceKey[Boolean]
  case class IsDirectory(iRODSPath: IRODSPath) extends IRODSServiceKey[Boolean]
  case class GetFileSize(iRODSPath: IRODSPath) extends IRODSServiceKey[IRODSTry[Long]]
  case class GetAbsolutePath(iRODSPath: IRODSPath) extends IRODSServiceKey[String]
  case class ListContents(iRODSPath: IRODSPath) extends IRODSServiceKey[IRODSTry[Traversable[IRODSPath]]]
  case class GetChecksum(iRODSPath: IRODSPath) extends IRODSServiceKey[String]


  //I/O ops
  case class CreateFileWithData(iRODSPath: IRODSPath, data: Array[Byte]) extends IRODSServiceKey[IRODSTry[Unit]]
  case class AppendFileWithData(iRODSPath: IRODSPath, data: Array[Byte]) extends IRODSServiceKey[IRODSTry[Unit]]
  case class ReadDataFromFile(iRODSPath: IRODSPath, offset: Long, amount: Int) extends IRODSServiceKey[IRODSTry[Array[Byte]]]
  case class OpenNewFileForWriting(iRODSPath: IRODSPath) extends IRODSServiceKey[IRODSTry[Unit]]
  case class WriteData(data: Array[Byte]) extends IRODSServiceKey[IRODSTry[Unit]]
}