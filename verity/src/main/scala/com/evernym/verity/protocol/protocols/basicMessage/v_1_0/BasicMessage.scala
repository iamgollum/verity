package com.evernym.verity.protocol.protocols.basicMessage.v_1_0

import java.util.UUID
import com.evernym.verity.Base64Encoded
import com.evernym.verity.constants.InitParamConstants._
import com.evernym.verity.protocol.Control
import com.evernym.verity.protocol.engine._
import com.evernym.verity.protocol.engine.util.?=>
import com.evernym.verity.protocol.protocols.CommonProtoTypes.{Timing => BaseTiming, Localization => l10n}
import com.evernym.verity.protocol.protocols.basicMessage.v_1_0.Role.{Sender, Receiver}
import com.evernym.verity.util.Base64Util.{getBase64Decoded, getBase64Encoded}
import com.evernym.verity.util.TimeUtil._

sealed trait Role
object Role {
  case object Sender extends Role {
    def roleNum = 0
  }
  case object Receiver extends Role {
    def roleNum = 1
  }
  def numToRole: Int ?=> Role = {
    case 0 => Sender
    case 1 => Receiver
  }
  def otherRole: Role ?=> Role = {
    case Sender => Receiver
    case Receiver => Sender
  }
}
trait Event
class BasicMessage(val ctx: ProtocolContextApi[BasicMessage, Role, Msg, Event, State, String])
  extends Protocol[BasicMessage, Role, Msg, Event, State, String](BasicMessageDefinition) {
  import BasicMessage._
  // Event Handlers

  def applyEvent: ApplyEvent = {
    case (_: State.Uninitialized , _ , e: Initialized  ) => (State.Initialized(), initialize(e))
    case (_                      , _ , MyRole(n)       ) => (None, setRole(n))
    case (_: State.Initialized   , r , e: MessageSent  ) =>
      r.selfRole_! match {
        case Sender => State.Messaging(buildMessage(e))
        case Receiver  => State.Messaging(buildMessage(e))
      }
  }
  // Protocol Msg Handlers
  override def handleProtoMsg: (State, Option[Role], Msg) ?=> Any = {
    case (_: State.Initialized  , _ , m: Msg.Message ) => receiveMessage(m)
  }
  // Control Message Handlers
  def handleControl: Control ?=> Any = {
    case c: Control => mainHandleControl(ctx.getState, c)
  }
  def mainHandleControl: (State, Control) ?=> Unit = {
    case (State.Uninitialized(),     m: Ctl.Init)            => ctx.apply(Initialized(m.selfId, m.otherId))
    case (_: State.Initialized,      m: Ctl.SendMessage)     => send(m)
    case (_: State.Messaging,        m: Ctl.SendMessage)     => send(m)
  }

  def receiveMessage(m: Msg.Message): Unit = {
    ctx.apply(MyRole(Receiver.roleNum))
    ctx.apply(messageToEvt(m))

    val signal = Signal.ReceivedMessage(
      m.`~l10n`,
      m.sent_time,
      m.content
    )
    ctx.signal(signal)
  }

  def send(m: Ctl.SendMessage): Unit = {
    ctx.apply(MyRole(Sender.roleNum))
    val messageMsg = Msg.Message(
      m.`~l10n`,
      m.sent_time,
      m.content
    )
    ctx.apply(messageToEvt(messageMsg))
    ctx.send(messageMsg, Some(Receiver), Some(Sender))
  }

  // Helper Functions
  def setRole(role: Int): Option[Roster[Role]] = {
    val myRole = Role.numToRole(role)
    val otherAssignment = Role.otherRole(myRole) -> ctx.getRoster.otherId()
    ctx.getRoster.withSelfAssignment(myRole).withAssignmentById(otherAssignment)
  }

  def initialize(p: Initialized): Roster[Role] = {
    ctx.updatedRoster(Seq(InitParamBase(SELF_ID, p.selfIdValue), InitParamBase(OTHER_ID, p.otherIdValue)))
  }
}

object BasicMessage {
  def buildMessage(m: MessageSent): Msg.Message = {
    Msg.Message(
      l10n(locale = Some("en")),
      BaseTiming(out_time = Some("2018-12-13T17:29:34+0000")),
      "Hello World",
    )
//    Msg.Message(
//      l10n(locale = Some(m.l10n)),
//      BaseTiming(out_time = Some(m.sentTime)),
//      m.content,
//    )
  }

  def messageToEvt(m: Msg.Message): MessageSent = {
    MessageSent(
      m.`~l10n`.locale.get,
      m.sent_time.out_time.get,
      m.content
    )
  }
}