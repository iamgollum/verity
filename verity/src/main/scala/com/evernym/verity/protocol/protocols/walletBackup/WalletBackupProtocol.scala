package com.evernym.verity.protocol.protocols.walletBackup

import com.evernym.verity.Exceptions.BadRequestErrorException
import com.evernym.verity.Status.BAD_REQUEST
import com.evernym.verity.protocol.engine._
import com.evernym.verity.protocol.engine.msg.PersistenceFailure
import com.evernym.verity.protocol.engine.util.?=>
import com.evernym.verity.protocol.protocols.walletBackup.State.ReadyToPersistBackup
import com.evernym.verity.protocol.protocols.walletBackup.WalletBackupMsgFamily._
import com.evernym.verity.protocol.protocols.walletBackup.{State => S}
import com.evernym.verity.protocol.{Control, SystemMsg}
import com.evernym.verity.util.Base64Util.{getBase64Decoded, getBase64Encoded}
import com.google.protobuf.ByteString

import scala.language.implicitConversions

case class BackupInitParams(recoveryVk: VerKey, ddAddress: String, cloudAddress: Array[Byte])

/**
  * Roles used in Wallet Protocol
  */
sealed trait Role
object Exporter extends Role
object Persister extends Role
object Recoverer extends Role

/**
  * Protocol Errors
  */
trait Error

/**
  * Exceptions signald in this protocol
  */
//TODO: In new version of protocol, send a problem report to the other participant rather than just throwing an error
class WalletBackupNotInitialized extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("wallet backup not initialized"))
class UnableToPersist extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Unable to backup wallet - persister state not READY_TO_PERSIST_WALLET"))
class UnableToBackup extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Unable to backup wallet - exporter state not READY_TO_EXPORT_WALLET"))
class UnableToRecoverBackup extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Unable to recover wallet backup - exporter state not READY_TO_EXPORT_WALLET"))
class UnexpectedProtoMsg extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Protocol message received in unexpected state"))
class NoBackupAvailable extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("No Wallet Backup available to download"))
class UnableToSetupRecoveryKey extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Unable to setup Recovery key"))
class UnsupportedBackupType extends BadRequestErrorException(BAD_REQUEST.statusCode, Option("Backup must be Array[Byte] or base64 encoded string"))

/**
  * Events that result in some state transformation
  */
trait BackupEvt


/** This embodies an instance of protocol for Verity-Platform Wallet Backup.
  */
class WalletBackupProtocol(val ctx: ProtocolContextApi[WalletBackupProtocol, Role, BackupMsg, BackupEvt, BackupState, String])
  extends Protocol[WalletBackupProtocol, Role, BackupMsg, BackupEvt, BackupState, String](WalletBackupProtoDef) {

  def getInitParams(params: Parameters): Seq[WalletBackupInitParam] = params
    .initParams
    .map(p => WalletBackupInitParam(p.name, p.value))
    .toSeq

  /**
    * Protocol Messages
    *
    * @return
    */
  override def handleProtoMsg: (BackupState, Option[Role], BackupMsg) ?=> Any = {
    case (_: S.Uninitialized      , _               , _             ) => throw new WalletBackupNotInitialized
    case (_: S.Initialized        , None            , b: BackupInit ) => provisionPersister(b.params)
    case (s: ReadyToPersistBackup , Some(r)         , _: Restore    ) => recoverBackup(s.vk, s.lastWallet, r)
    case (_: S.RecoveringBackup   , Some(Persister) , r: Restored   ) => ctx.apply(RecoveredBackup()); ctx.signal(r)
    case (s                       , Some(Recoverer) , m             ) => persistersProtoMsgHandler(s, m)
    case (s                       , Some(Exporter)  , m             ) => persistersProtoMsgHandler(s, m)
    case (s                       , Some(Persister) , m             ) => exportersProtoMsgHandler(s, m)
  }

  def persistersProtoMsgHandler: (BackupState, BackupMsg) ?=> Any = {
    case (_: S.ReadyToPersistBackup  , BackupInit(_) )  => ctx.send(BackupReady())
    case (s: S.ReadyToPersistBackup  , Backup(w)     )  => backup(s.vk, w)
    case (_                          , Backup(_)     )  => throw new UnableToPersist
  }

  def exportersProtoMsgHandler: (BackupState, BackupMsg) ?=> Any = {
    case (_: S.BackupInitRequested , BackupReady()    ) => ctx.apply(ReadyToExport())
    case (_: S.BackupInProgress    , BackupAck()      ) => ctx.apply(BackupStoredAck())
    case (_: S.BackupInProgress    , f: BackupFailure ) => ctx.signal(ReportBackupFailure(f.failure))
    case (_                        , _                ) => throw new UnexpectedProtoMsg
  }

  /**
    * Control Message Handlers
    */
  def handleControl: Control ?=> Any = {
    case c: Control => mainHandleControl(ctx.getState, ctx.getRoster.selfRole, c)
  }

  def mainHandleControl: (BackupState, Option[Role], Control) ?=> Any = {
    case (_: S.Uninitialized , None            , Init(p)               ) => ctx.apply(WalletBackupInitialized(getInitParams(p)))
    case (_: S.Initialized   , None            , i: InitBackup         ) => provisionExporter(i.params)
    case (_: S.Initialized   , None            , r: RestoreBackup      ) => provisionRecoverer(r)
    case ( _                 , _               , p: PersistenceFailure ) => failedToPersist(p)
    case (s                  , Some(Persister) , c                     ) => persisterCtl(s, c)
    case (s                  , Some(Exporter)  , c                     ) => exporterCtl(s, c)
  }

  def exporterCtl: (BackupState, Control) ?=> Unit = {
    case (_: S.ReadyToExportBackup , b: ExportBackup  ) => ctx.apply(BackupInProgress());   ctx.send(Backup(b.wallet))
    case (_: S.ReadyToExportBackup , _: RecoverBackup ) => ctx.apply(RecoveryInProgress()); ctx.send(Restore(), fromRole=Some(Exporter))
    case (_                        , _: ExportBackup  ) => throw new UnableToBackup
    case (_                        , _: RecoverBackup ) => throw new UnableToRecoverBackup
  }

  def persisterCtl: (BackupState, Control) ?=> Unit = {
    case (_: S.RecoveryModeRequested , _: FailedToRegisterRecoveryKey ) => throw new UnableToSetupRecoveryKey
    case (_: S.RecoveryModeRequested , _: RecoveryKeyRegistered       ) => ctx.apply(ReadyToPersist()); ctx.send(BackupReady())
  }

  implicit def optSetRosterToSetRoster(r: Option[SetRoster]): SetRoster = r.getOrElse(throw new RuntimeException("no roster setter"))
  implicit def SetRosterToOptSetRoster(r: SetRoster): Option[SetRoster] = Option(r)
  def provisionPersister(params: BackupInitParams): Unit = {
    //      //Todo: some sort of check to ensure person sending message has permission to send message
    //      // Container should be indicating that this is coming from an authenticated user, determining the same domain
    //      //Note: *** code here for authorization and storage service initialization. That's why there are two state changes
    val rSetter = SetRoster(_otherIdx, ctx.getRoster.selfIndex_!, params.recoveryVk)
    ctx.apply(RequestedRecoveryKeySetup(rSetter))
    ctx.signal(ProvideRecoveryDetails(params))
  }

  def provisionExporter(params: BackupInitParams): Unit = {
    val rSetter = SetRoster(ctx.getRoster.selfIndex_!, _otherIdx, params.recoveryVk)
    ctx.apply(ProvisionRequested(rSetter))
    ctx.send(BackupInit(params))
  }

  def provisionRecoverer(restoreBackup: RestoreBackup): Unit = {
    val rSetter = SetRoster(exporterIdx=noIdxForRole, persisterIdx=_otherIdx, restoreBackup.recoveryVk)
    ctx.apply(RecoveryRequested(rSetter))
    ctx.send(Restore(), toRole =Some(Persister), fromRole = ctx.getRoster.selfRole)
  }

  //TODO: Hash of wallet could be calculated and stored with wallet to ensure data integrity.
  // Exporter could verify this with their own hash generation
  // This hash would be stored in the event StorageReferenceStored
  def backup(vk: VerKey, wallet: Any): Unit = {
  //TODO: - RTM -> Once protocol version upgrades are vetted, remove this conditional and make base64 encoded string a later version
    val w: WalletBackup = wallet match {
      case w: WalletBackup => ctx.logger.debug("byte array received - newer expectation is base64 encoded str"); w
      case w: WalletBackupEncoded => ctx.logger.debug("received base64 encoded string"); getBase64Decoded(w)
      case w: List[_] =>
        ctx.logger.debug("Int list received - newer expectation is base64 encoded str");
        // Can't test for List[Int] but that is what is expected
        // So we will cast to List[Int] even though we don't know for sure it is of that type
        val l = w.asInstanceOf[List[Int]]
        l.map(_.toByte).toArray
      case _ => throw new UnsupportedBackupType
    }

    ctx.storeSegment(vk, BackupStored(ByteString.copyFrom(w)))
    ctx.send(BackupAck())
  }

  def recoverBackup(recoveryVk: VerKey, w: Option[WalletBackup], r: Role): Unit = {
    def restored(b: WalletBackup): Restored = Restored(getBase64Encoded(b))

    val backup = ctx
      .getInFlight
      .segmentAs[BackupStored]
      .map(x => restored(x.wallet.toByteArray))
      .getOrElse(restored(w.getOrElse(throw new NoBackupAvailable)))

    ctx.apply(RecoveredBackup())
    ctx.send(backup, toRole=_toRole(r))
  }

  def failedToPersist(err: PersistenceFailure): Unit = ctx.send(BackupFailure(err))

  /**
    * Event Handlers
    */
  def applyEvent: ApplyEvent = applyCommonEvt orElse applyExportersEvt orElse applyPersistersEvt orElse applyRecovererEvt

  def applyCommonEvt: ApplyEvent = {
    case (_: S.Uninitialized , _ , WalletBackupInitialized(p)) => (S.Initialized(), initialize(p))
  }

  def applyExportersEvt: ApplyEvent = {
    case (_: S.Initialized          , _               , ProvisionRequested(s)) => (S.BackupInitRequested(), setRoles(s))
    case (_: S.BackupInitRequested  , _               , ReadyToExport()      ) => S.ReadyToExportBackup()
    case (_: S.ReadyToExportBackup  , _               , BackupInProgress()   ) => S.BackupInProgress()
    case (_: S.BackupInProgress     , _               , BackupStoredAck()    ) => S.ReadyToExportBackup()
    case (_: S.ReadyToExportBackup  , _               , RecoveryInProgress() ) => S.RecoveringBackup()
    case (_: S.RecoveringBackup     , r: Roster[Role] , RecoveredBackup()    ) if r.selfRole_! == Exporter => S.ReadyToExportBackup()
  }

  def applyPersistersEvt: ApplyEvent = {
    case (_: S.Initialized            , _ , RequestedRecoveryKeySetup(Some(s)) ) => (S.RecoveryModeRequested(s.recovererVk), setRoles(s))
    case (s: S.RecoveryModeRequested  , _ , ReadyToPersist()             ) => S.ReadyToPersistBackup(s.vk, None)
    case (s: S.ReadyToPersistBackup   , _ , BackupStored(w)              ) => S.ReadyToPersistBackup(s.vk, Some(w.toByteArray))
    case (_: S.ReadyToPersistBackup   , _ , RecoveredBackup()            ) => ctx.getState
  }

  def applyRecovererEvt: ApplyEvent = {
    case (_: S.Initialized       , _                , RecoveryRequested(s) ) => (S.RecoveringBackup(), setRoles(s, recoveryMode = true))
    case (_: S.RecoveringBackup  , r: Roster[Role]  , RecoveredBackup()    ) if r.selfRole_! == Recoverer => S.Recovered()
  }

  override def handleSystemMsg: SystemMsg ?=> Any = {
    case e: PersistenceFailure => failedToPersist(e)
  }

  def setRoles(r: SetRoster, recoveryMode: Boolean = false): Option[Roster[Role]] = ctx.getRoster
    .withParticipant(r.recovererVk, recoveryMode)
    .map(newRoster => newRoster
      .withAssignment(
        Exporter -> r.exporterIdx,
        Persister -> r.persisterIdx,
        Recoverer -> newRoster.participantIndex_!(r.recovererVk)
      ))

  //TODO: this still feels like boiler plate, need to come back and fix it
  def initialize(params: Seq[WalletBackupInitParam]): Roster[Role] =
    ctx.updatedRoster(params.map(p => InitParamBase(p.name, p.value)))

  def _otherIdx: ParticipantIndex = ctx.getRoster.otherIndex(ctx.getRoster.selfIndex_!)
  def _toRole(r: Role): Option[Role] = ctx.getRoster.roleForId(ctx.getRoster.participantIdForRole_!(r))
  val noIdxForRole: ParticipantIndex = -1
}
