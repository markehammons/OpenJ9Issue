package eu.bioemergences.utils

import eu.bioemergences.utils.irods.IRODSError

package object irods {
  type IRODSTry[U] = Either[String, U]
}
