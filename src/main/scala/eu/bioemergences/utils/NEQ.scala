package eu.bioemergences.utils

import scala.annotation.implicitAmbiguous
import scala.reflect.ClassTag

/**
  * Not Equals Typeclass. Check that a generic A does not equal a generic B
  */

trait NEQ[A,B]

object NEQ {
  @implicitAmbiguous("Cannot prove that ${A} != ${B}")
  implicit def AisneqB[A:ClassTag, B: ClassTag]: NEQ[A,B] = null
  implicit def AiseqB[A: ClassTag, B: ClassTag](implicit eq: A =:= B): NEQ[A,B] = null
}
