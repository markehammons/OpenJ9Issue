package eu.bioemergences.utils.irods

import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import eu.bioemergences.utils.irods.IRODSServiceBench.IterableState
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import scala.annotation.meta.param

@BenchmarkMode(Array(Mode.AverageTime))
@Fork(3)
@Threads(1)
@Warmup(iterations = 10)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class IRODSServiceBench {

  var actorSystem: ActorSystem = null
  var iRODSServicePool: IRODSServicePool = null
  //var iRODSService: IRODSService = null
  val dirTestPath = IRODSPath("/bioemerg/groups/rawdata")
  val dataDir = IRODSPath("/bioemerg/groups/rawdata/testVTKS")

  @Setup(Level.Trial)
  def spinUp() = {
    actorSystem = ActorSystem("mySystem", config = ConfigFactory.load("test.conf"))
    iRODSServicePool = IRODSServicePool.apply(actorSystem)
    //iRODSService = iRODSServicePool.acquireService()
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit = {
    iRODSServicePool.stop()
    actorSystem.terminate()
  }

  @Benchmark
  def listContents(blackhole: Blackhole) = {
    iRODSServicePool.useService { iRODSService =>
      blackhole.consume(iRODSService.listContents(dirTestPath))
    }
  }

  @Benchmark
  def writeDataOldMethodSingleThreaded(blackhole: Blackhole, iterableState: IterableState) = {
    iRODSServicePool.useService { iRODSService =>
      for (j <- 0 until iterableState.files) {
        val path = dirTestPath / j.toString
        for (i <- 0 until iterableState.times) {
          if (i == 0) {
            iRODSService.writeToNewFile(path, iterableState.testData)
          } else {
            iRODSService.appendToFile(path, iterableState.testData)
          }
        }
      }
    }
  }

  @Benchmark
  def writeDataSingleThreaded(blackhole: Blackhole, iterableState: IterableState) = {
    for(j <- 0 until iterableState.files) {
      val path = dirTestPath / j.toString
      iRODSServicePool.useService { iRODSService =>
        iRODSService.openNewFileForWrite(path)
        for (i <- 0 until iterableState.times) {
          iRODSService.writeData(iterableState.testData)
        }
        iRODSService.closeFile()
      }
    }
  }

  @Benchmark
  def writeDataIntegrityChecked(iterableState: IterableState) = {
    for(j <- 0 until iterableState.files) {
      val path = dirTestPath / j.toString
      iRODSServicePool.useService { iRODSService =>
        val md = MessageDigest.getInstance("SHA-256")
        iRODSService.openNewFileForWrite(path)
        for(i <- 0 until iterableState.times) {
          iRODSService.writeData(iterableState.testData)
          md.update(iterableState.testData)
        }
        iRODSService.closeFile()
        val serverChksum = iRODSService.getChecksum(path)
        Base64.getEncoder.encodeToString(md.digest()) == serverChksum
      }
    }
  }

  @Benchmark
  def writeDataMulticonnectSimulation(iterableState: IterableState) = {
    for(j <- 0 until iterableState.files) {
      val path = dirTestPath / j.toString
      iRODSServicePool.saveActorForFile(path)
      for(i <- 0 until iterableState.times) {
        iRODSServicePool.useReservedService(path) { irodsService =>
          if (i == 0) {
            irodsService.openNewFileForWrite(path)
          }
          irodsService.writeData(iterableState.testData)
        }
      }
      iRODSServicePool.useReservedService(path)(_.closeFile())
      iRODSServicePool.endReservationForFile(path)
    }
  }
}

object IRODSServiceBench {
  @State(Scope.Benchmark)
  class IterableState{
    @Param(Array("1024", "524288", "1048576"))
    var size: Int = _

    @Param(Array("1", "5", "10"))
    var times: Int = _

    @Param(Array("1"))
    var files: Int = _

    var testData: Array[Byte] = _
    @Setup(Level.Trial)
    def setup() = {
      testData = Array.fill(size)(0)
    }
  }
}
