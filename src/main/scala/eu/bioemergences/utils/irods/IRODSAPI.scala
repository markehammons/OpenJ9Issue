package eu.bioemergences.utils.irods

import com.typesafe.config.ConfigFactory
import org.irods.jargon.core.connection.{IRODSAccount, IRODSSession, IRODSSimpleProtocolManager}
import org.irods.jargon.core.exception.FileNotFoundException
import org.irods.jargon.core.packinstr.DataObjInp.OpenFlags.READ_WRITE
import org.irods.jargon.core.protovalues.ChecksumEncodingEnum
import org.irods.jargon.core.pub.IRODSAccessObjectFactoryImpl
import org.irods.jargon.core.pub.io.{IRODSFile, IRODSFileInputStream, IRODSFileOutputStream}
import org.irods.jargon.core.query.CollectionAndDataObjectListingEntry

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * A Shim over the jargon api, to simplify interaction with iRODS and to simplify singlethreaded usage of jargon.
 */

class IRODSAPI {
  private val irodsProtocolManager = IRODSSimpleProtocolManager.instance()

  private val config = ConfigFactory.load("irods")
  private val iRODSUsername = config.getString("irods.username")
  private val iRODSPassword = config.getString("irods.password")
  require(iRODSUsername != null, "iRODS password not set. Please define via -DIRODSPassword=\"password\" on the command line")
  require(iRODSPassword != null, "iRODS username not set. Please define via -DIRODSUsername=\"username\" on the command line")
  private val irodsAccount = new IRODSAccount("irods.bioemergences.eu", 5531, iRODSUsername, iRODSPassword, "/bioemerg/groups/", "bioemerg", "inaf-disk-1")

  private val iRODSSession = IRODSSession.instance(irodsProtocolManager)
  private val iRODSAOFactory = IRODSAccessObjectFactoryImpl.instance(iRODSSession)
  private val iRODSFileFactory = iRODSAOFactory.getIRODSFileFactory(irodsAccount)
  private val iRODSDataObjectAO = iRODSAOFactory.getDataObjectAO(irodsAccount)

  private val iRODSFileSystemAO = iRODSAOFactory.getIRODSFileSystemAO(irodsAccount)

  private val collectionAndDataObjectListAndSearchAO = iRODSAOFactory.getCollectionAndDataObjectListAndSearchAO(irodsAccount)

  irodsAccount.setDefaultStorageResource("inaf-disk-1")

  def getFile(path: IRODSPath): IRODSFile = {
    iRODSFileFactory.instanceIRODSFile(path.value)
  }

  def getChecksum(path: IRODSPath): String = {
    val checkSummer = iRODSDataObjectAO.computeChecksumOnDataObject(getFile(path))
    checkSummer.setChecksumEncoding(ChecksumEncodingEnum.SHA256)
    checkSummer.getChecksumStringValue
  }

  def getInputStream(path: IRODSPath): IRODSFileInputStream = {
    iRODSFileFactory.instanceIRODSFileInputStream(getFile(path))
  }

  def getOutputStream(path: IRODSPath): IRODSFileOutputStream = {
    iRODSFileFactory.instanceIRODSFileOutputStream(getFile(path))
  }

  def getAppendOutputStream(path: IRODSPath): IRODSFileOutputStream = {
    iRODSFileFactory.instanceIRODSFileOutputStream(getFile(path), READ_WRITE)
  }

  def listContents(path: IRODSPath): Traversable[IRODSPath] = {
    val tryCollectionsAndDataObjects = Try(collectionAndDataObjectListAndSearchAO.listDataObjectsAndCollectionsUnderPath(path.value).asScala)
    tryCollectionsAndDataObjects.recover{
      case fnf: FileNotFoundException => Traversable.empty[CollectionAndDataObjectListingEntry]
      case t: Throwable => throw t
    }.getOrElse(Traversable.empty).map(cAD => IRODSPath(cAD.getFormattedAbsolutePath))
  }

  def shutdown() = {
    iRODSSession.currentConnection(irodsAccount).obliterateConnectionAndDiscardErrors()
    iRODSSession.closeSession(irodsAccount)
    iRODSAOFactory.closeSession(irodsAccount)
  }
}
