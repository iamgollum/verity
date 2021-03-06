package com.evernym.verity.actor

import akka.actor.{ActorRef, Props}
import akka.persistence.PersistentRepr
import akka.serialization.SerializationExtension
import com.evernym.verity.Exceptions.{BadRequestErrorException, InternalServerErrorException}
import com.evernym.verity.actor.persistence.BasePersistentActor
import com.evernym.verity.actor.testkit.{AkkaTestBasic, PersistentActorSpec, TestAppConfig}
import com.evernym.verity.Status._
import com.evernym.verity.logging.LoggingUtil.getLoggerByClass
import com.evernym.verity.config.AppConfig
import com.evernym.verity.protocol.engine.DID
import com.evernym.verity.protocol.protocols.walletBackup.BackupStored
import com.evernym.verity.testkit.BasicSpec
import com.google.protobuf.ByteString
import com.typesafe.config.Config
import com.typesafe.scalalogging.Logger

class AkkaPersistenceFailureSpec extends PersistentActorSpec with BasicSpec {

  val pa: ActorRef = system.actorOf(Props(new DummyActor))

  "A persisting actor" - {
    "fails on large event" in {
      val tooLargeData: Array[Byte] = Array.fill(700000){'a'}
      pa ! BadPersistenceData(tooLargeData)
      expectMsg("persistence failure handled")
    }
  }

  override def overrideConfig: Option[Config] = Option {
      AkkaTestBasic.journalFailingOnLargeEvents
  }
}

class FailsOnLargeEventTestJournal extends TestJournal {

  override def asyncWriteMessages(messages: _root_.scala.collection.immutable.Seq[_root_.akka.persistence.AtomicWrite]):
  _root_.scala.concurrent.Future[_root_.scala.collection.immutable.Seq[_root_.scala.util.Try[Unit]]] = {

    /**
     * This is dynamodb's event size restriction
     * Wallet Backup first exposed this as being an issue but will eventually use S3 rather than dynamodb
     */
    val MAX_SIZE = 399999

    val payloads = messages.flatMap(_.payload)

    if (payloads.exists(p => toEventSize(p) > MAX_SIZE)) {
      throw new Exception("can't persist an event of size more than 400 KB")
    } else {
      super.asyncWriteMessages(messages)
    }
  }

  def toEventSize(repr: PersistentRepr): Int = {
    import java.nio.ByteBuffer
    val serialization = SerializationExtension(context.system)
    val reprPayload: AnyRef = repr.payload.asInstanceOf[AnyRef]
    val serialized = ByteBuffer.wrap(serialization.serialize(reprPayload).get).array
    serialized.length
  }

}

case class AddData(did: DID, data: String) extends ActorMessageClass

case class GetData(did: DID) extends ActorMessageClass

case class BadPersistenceData(data: Array[Byte]) extends ActorMessageClass

case object RestartNow extends ActorMessageObject

class DummyActor extends BasePersistentActor {

  lazy val appConfig: AppConfig = new TestAppConfig
  var didData: Map[String, String] = Map.empty
  val logger: Logger = getLoggerByClass(getClass)

  override def persistenceEncryptionKey: String = "test-key"

  val receiveEvent: Receive = {
    case e: MockEvent3 => didData += e.did -> e.data
  }

  lazy val receiveCmd: Receive = {
    case ad: AddData if didData.contains(ad.did) =>
      throw new BadRequestErrorException(ALREADY_EXISTS.statusCode)

    case ad: AddData =>
      writeApplyAndSendItBack(MockEvent3(ad.did, ad.data))

    case gd: GetData =>
      sender ! didData.get(gd.did)

    case bd: BadPersistenceData =>
      persistExt(BackupStored(ByteString.copyFrom(bd.data)))(() => _)

    case RestartNow =>
      val cre = new InternalServerErrorException(UNHANDLED.statusCode, Option(UNHANDLED.statusMsg))
      //below is just so that it doesn't print whole stack trace which may confuse someone if this is expected exception or not etc
      cre.setStackTrace(Array())
      throw cre
  }

  private def afterFailureTest(data: Option[String] = None): Unit = {
    logger.info("Successful Persist after failure")
    sender() ! "persistence failure handled"
  }

  final override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
    val errorMsg =
      "actor persist event failed (" +
        "possible-causes: database not reachable/up/responding, required tables are not created etc, " +
        s"persistence-id: $persistenceId, " +
        s"error-msg: ${cause.getMessage})"

    afterFailureTest(Some(errorMsg))
  }
}
