package com.evernym.verity.protocol.protocols.writeSchema.v_0_6

import com.evernym.verity.constants.InitParamConstants.MY_ISSUER_DID
import com.evernym.verity.actor.{ParameterStored, ProtocolInitialized}
import com.evernym.verity.protocol.Control
import com.evernym.verity.protocol.actor.Init
import com.evernym.verity.protocol.engine._
import com.evernym.verity.protocol.engine.util.?=>
import com.evernym.verity.protocol.protocols.presentproof.v_1_0.ProtocolHelpers.noHandleProtoMsg
import com.evernym.verity.protocol.protocols.writeSchema.v_0_6.Role.Writer
import com.evernym.verity.protocol.protocols.writeSchema.v_0_6.State.{Done, Error, Initialized, Processing}
import com.evernym.verity.util.JsonUtil.seqToJson

import scala.util.{Failure, Success}

trait Event

class WriteSchema(val ctx: ProtocolContextApi[WriteSchema, Role, Msg, Any, WriteSchemaState, String])
  extends Protocol[WriteSchema, Role, Msg, Any, WriteSchemaState, String](WriteSchemaDefinition) {

  override def handleProtoMsg: (WriteSchemaState, Option[Role], Msg) ?=> Any = noHandleProtoMsg()

  override def handleControl: Control ?=> Any = {
    case c => mainHandleControl(ctx.getState, ctx.getRoster.selfRole, c)
  }

  def mainHandleControl: (WriteSchemaState, Option[Role], Control) ?=> Any = {
    case (_, _, c: Init) => ctx.apply(ProtocolInitialized(c.parametersStored.toSeq))
    case (s: State.Initialized, _, m: Write) =>
      writeSchemaToLedger(m, s)
  }

  override def applyEvent: ApplyEvent = {
    case (_, _, e: ProtocolInitialized) =>
      (State.Initialized(getInitParams(e)), initialize(e.parameters))
    case (_: Initialized, _, e: RequestReceived) =>
      (
        Processing(e.name, e.version, e.attrs),
        ctx.getRoster.withAssignment(Writer() -> ctx.getRoster.selfIndex_!)
      )
    case (_: Processing, _, e: AskedForEndorsement) => State.WaitingOnEndorser(e.schemaId, e.schemaJson)
    case (_: Processing, _, e: SchemaWritten) => Done(e.schemaId)
    case (_: Processing, _, e: WriteFailed) => Error(e.error)
  }

  def writeSchemaToLedger(m: Write, init: State.Initialized): Unit = {
    ctx.apply(RequestReceived(m.name, m.version, m.attrNames))
    try {
      val submitterDID = _submitterDID(init)
      val (schemaId, schemaJson) = ctx.wallet.createSchema(submitterDID, m.name, m.version, seqToJson(m.attrNames)).get

      ctx.ledger.writeSchema(submitterDID, schemaJson) match {
        case Success(_) =>
          ctx.apply(SchemaWritten(schemaId))
          ctx.signal(StatusReport(schemaId))
        case Failure(e: LedgerRejectException) if missingVkErr(submitterDID, e) =>
          ctx.logger.warn(e.toString)
          ctx.apply(AskedForEndorsement(schemaId, schemaJson))
          ctx.signal(NeedsEndorsement(schemaId, schemaJson))
        case Failure(e) => problemReport(e)
      }

    } catch {
      case e: Exception => problemReport(e)
    }
  }

  def problemReport(e: Throwable): Unit = {
    ctx.logger.error(e.toString)
    ctx.apply(WriteFailed(Option(e.getMessage).getOrElse("unknown error")))
    ctx.signal(ProblemReport(e.toString))
  }

  def _submitterDID(init: State.Initialized): DID =
    init
      .parameters
      .initParams
      .find(_.name.equals(MY_ISSUER_DID))
      .map(_.value)
      .getOrElse(throw MissingIssuerDID)

  def missingVkErr(did: DID, e: LedgerRejectException): Boolean =
    e.msg.contains(s"verkey for $did cannot be found")

  def initialize(params: Seq[ParameterStored]): Roster[Role] = {
    //TODO: this still feels like boiler plate, need to come back and fix it
    ctx.updatedRoster(params.map(p => InitParamBase(p.name, p.value)))
  }

  def getInitParams(params: ProtocolInitialized): Parameters =
    Parameters(params
      .parameters
      .map(p => Parameter(p.name, p.value))
      .toSet
    )
}
