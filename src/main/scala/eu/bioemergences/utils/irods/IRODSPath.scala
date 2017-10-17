package eu.bioemergences.utils.irods


import scala.util.{Failure, Success, Try}

/**
 * Created by mhammons on 8/20/16.
 */
case class IRODSPath(value: String) extends AnyVal {
  def /(subPath: String) = IRODSPath(s"$value/$subPath")
  def getParent = {
    val possibleRes = value.splitAt(value.lastIndexOf('/'))._1
    if (possibleRes.length == value.length - 1) {
      IRODSPath(possibleRes.splitAt(possibleRes.lastIndexOf('/'))._1)
    } else {
      IRODSPath(possibleRes)
    }
  }

  def getName = {
    val possibleRes = value.splitAt(value.lastIndexOf('/'))._2.filter(_ != '/')
    if (possibleRes.isEmpty) {
      val cleaned = value.splitAt(value.lastIndexOf('/'))._1
      cleaned.splitAt(value.lastIndexOf('/'))._2.filter(_ != '/')
    } else {
      possibleRes
    }
  }
}
