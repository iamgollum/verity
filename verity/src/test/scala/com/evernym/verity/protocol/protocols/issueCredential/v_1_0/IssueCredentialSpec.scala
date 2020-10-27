package com.evernym.verity.protocol.protocols.issueCredential.v_1_0

import com.evernym.verity.constants.InitParamConstants.{THEIR_PAIRWISE_DID, MY_PAIRWISE_DID}
import com.evernym.verity.protocol.didcomm.decorators.PleaseAck
import com.evernym.verity.protocol.engine.MsgFamily
import com.evernym.verity.protocol.protocols.issueCredential.common.IssueCredentialSpecBase
import com.evernym.verity.protocol.protocols.issueCredential.v_1_0.Ctl._
import com.evernym.verity.protocol.protocols.issueCredential.v_1_0.Msg.{IssueCred, OfferCred, RequestCred}
import com.evernym.verity.protocol.testkit.DSL.{signal, state}
import com.evernym.verity.protocol.testkit.{MockableLedgerAccess, MockableWalletAccess, TestsProtocolsImpl}
import com.evernym.verity.testkit.BasicFixtureSpec
import com.evernym.verity.util.Base64Util

import scala.reflect.ClassTag


class IssueCredentialSpec extends TestsProtocolsImpl(IssueCredentialProtoDef)
  with IssueCredentialSpecBase
  with BasicFixtureSpec {

  override val defaultInitParams = Map(
    MY_PAIRWISE_DID -> "8XFh8yBzrpJQmNyZzgoTqB",
    THEIR_PAIRWISE_DID -> "8XFh8yBzrpJQmNyZzgoTqB"
  )

  "Credential Protocol Definition" - {
    "should have two roles" in { _ =>
      IssueCredentialProtoDef.roles.size shouldBe 2
      IssueCredentialProtoDef.roles shouldBe Set(Role.Issuer(), Role.Holder())
    }
  }

  "CredentialProtocol" - {

    "when Holder sends Propose control message" - {
      "holder and issuer should transition to ProposalSent and ProposalReceived state respectively" in { f =>
        //https://github.com/hyperledger/aries-rfcs/tree/bb42a6c35e0d5543718fb36dd099551ab192f7b0/features/0036-issue-credential#propose-credential

        val (issuer, holder) = (f.alice, f.bob)

        (holder engage issuer) ~ Propose(createTest1CredDef, credValues)
        holder.role shouldBe Role.Holder()
        holder expect signal[SignalMsg.Sent]
        val proposalSent = holder expect state[State.ProposalSent]
        assertStatus[State.ProposalSent](holder)
        assertProposalSent(proposalSent)

        issuer.role shouldBe Role.Issuer()
        issuer expect signal[SignalMsg.AcceptProposal]
        val proposalReceived = issuer expect state[State.ProposalReceived]
        assertStatus[State.ProposalReceived](issuer)
        assertProposalReceived(proposalReceived)

        issuer ~ Reject(Option("rejected received proposal"))
        issuer expect state[State.Rejected]
        assertStatus[State.Rejected](issuer)

        holder expect state[State.ProblemReported]
        assertStatus[State.ProblemReported](holder)
      }
    }

    "when Issuer sends Offer control message" - {
      "issuer and holder should transition to OfferSent and OfferReceived state respectively" in { f =>
        //https://github.com/hyperledger/aries-rfcs/tree/bb42a6c35e0d5543718fb36dd099551ab192f7b0/features/0036-issue-credential#offer-credential

        val (issuer, holder) = (f.alice, f.bob)

        (holder engage issuer) ~ Propose(createTest1CredDef, credValues)
        holder expect signal[SignalMsg.Sent]
        issuer expect signal[SignalMsg.AcceptProposal]

        issuer walletAccess MockableWalletAccess()

        issuer ~ buildSendOffer()
        issuer expect signal[SignalMsg.Sent]
        val offerSent = issuer expect state[State.OfferSent]
        assertStatus[State.OfferSent](issuer)
        assertOfferSent(offerSent)

        holder expect signal[SignalMsg.AcceptOffer]
        val offerReceived = holder expect state[State.OfferReceived]
        assertStatus[State.OfferReceived](holder)
        assertOfferReceived(offerReceived)
      }
    }

    "when Holder sends Request control message" - {
      "holder and issuer should transition to RequestSent and RequestReceived state respectively" in { f =>
        //https://github.com/hyperledger/aries-rfcs/tree/bb42a6c35e0d5543718fb36dd099551ab192f7b0/features/0036-issue-credential#request-credential

        val (issuer, holder) = (f.alice, f.bob)

        (holder engage issuer) ~ Propose(createTest1CredDef, credValues)
        holder expect signal[SignalMsg.Sent]
        issuer expect signal[SignalMsg.AcceptProposal]

        issuer walletAccess MockableWalletAccess()

        issuer ~ buildSendOffer()
        issuer expect signal[SignalMsg.Sent]
        holder expect signal[SignalMsg.AcceptOffer]

        holder walletAccess MockableWalletAccess()
        holder ledgerAccess MockableLedgerAccess()

        holder ~ buildSendRequest()
        holder expect signal[SignalMsg.Sent]
        val reqSent = holder expect state[State.RequestSent]
        assertStatus[State.RequestSent](holder)
        assertRequestSent(reqSent)

        issuer expect signal[SignalMsg.AcceptRequest]
        val reqReceived = issuer expect state[State.RequestReceived]
        assertStatus[State.RequestReceived](issuer)
        assertRequestReceived(reqReceived)

      }
    }

    "when Issuer sends Issue control message" - {
      "issuer and holder should transition to IssueCredSent and IssueCredReceived state respectively" in { f =>
        //https://github.com/hyperledger/aries-rfcs/tree/bb42a6c35e0d5543718fb36dd099551ab192f7b0/features/0036-issue-credential#issue-credential

        val (issuer, holder) = (f.alice, f.bob)

        (holder engage issuer) ~ Propose(createTest1CredDef, credValues)
        holder expect signal[SignalMsg.Sent]
        issuer expect signal[SignalMsg.AcceptProposal]

        issuer walletAccess MockableWalletAccess()
        issuer ~ buildSendOffer()
        issuer expect signal[SignalMsg.Sent]
        holder expect signal[SignalMsg.AcceptOffer]

        holder walletAccess MockableWalletAccess()
        holder ledgerAccess MockableLedgerAccess()
        holder ~ buildSendRequest()
        holder expect signal[SignalMsg.Sent]
        issuer expect signal[SignalMsg.AcceptRequest]

        issuer ~ Issue(`~please_ack` = Option(PleaseAck()))
        issuer expect signal[SignalMsg.Sent]
        val issueCredSent = issuer expect state[State.IssueCredSent]
        assertStatus[State.IssueCredSent](issuer)
        assertIssueSent(issueCredSent)

        holder expect signal[SignalMsg.Received]
        val issueCredReceived = holder expect state[State.IssueCredReceived]
        assertStatus[State.IssueCredReceived](holder)
        assertIssueReceived(issueCredReceived)

        issuer expect signal[SignalMsg.Ack]
      }
    }
  }

  "when Issuer do not use auto_issue in offer (legacy)" - {
    "it should follow two-step issuance flow" in { f =>
      val (issuer, holder) = (f.alice, f.bob)

      issuer walletAccess MockableWalletAccess()
      (issuer engage holder) ~ buildSendOffer(None)
      issuer expect signal[SignalMsg.Sent]
      holder expect signal[SignalMsg.AcceptOffer]

      holder walletAccess MockableWalletAccess()
      holder ledgerAccess MockableLedgerAccess()
      holder ~ buildSendRequest()
      holder expect signal[SignalMsg.Sent]
      issuer expect signal[SignalMsg.AcceptRequest]

      issuer ~ Issue(`~please_ack` = Option(PleaseAck()))
      issuer expect signal[SignalMsg.Sent]
      val issueCredSent = issuer expect state[State.IssueCredSent]
      assertStatus[State.IssueCredSent](issuer)
      assertIssueSent(issueCredSent)

      holder expect signal[SignalMsg.Received]
      val issueCredReceived = holder expect state[State.IssueCredReceived]
      assertStatus[State.IssueCredReceived](holder)
      assertIssueReceived(issueCredReceived)

      issuer expect signal[SignalMsg.Ack]
    }
  }

  "when Issuer set auto_issue in offer to FALSE" - {
    "it should follow two-step issuance flow" in { f =>
      val (issuer, holder) = (f.alice, f.bob)

      issuer walletAccess MockableWalletAccess()
      (issuer engage holder) ~ buildSendOffer(Option(false))
      issuer expect signal[SignalMsg.Sent]
      holder expect signal[SignalMsg.AcceptOffer]

      holder walletAccess MockableWalletAccess()
      holder ledgerAccess MockableLedgerAccess()
      holder ~ buildSendRequest()
      holder expect signal[SignalMsg.Sent]
      issuer expect signal[SignalMsg.AcceptRequest]

      issuer ~ Issue(`~please_ack` = Option(PleaseAck()))
      issuer expect signal[SignalMsg.Sent]
      val issueCredSent = issuer expect state[State.IssueCredSent]
      assertStatus[State.IssueCredSent](issuer)
      assertIssueSent(issueCredSent)

      holder expect signal[SignalMsg.Received]
      val issueCredReceived = holder expect state[State.IssueCredReceived]
      assertStatus[State.IssueCredReceived](holder)
      assertIssueReceived(issueCredReceived)

      issuer expect signal[SignalMsg.Ack]
    }
  }

  "when Issuer set auto_issue in offer to TRUE" - {
    "it should follow one-step issuance flow" in { f =>
      val (issuer, holder) = (f.alice, f.bob)

      issuer walletAccess MockableWalletAccess()
      (issuer engage holder) ~ buildSendOffer(Option(true))
      issuer expect signal[SignalMsg.Sent]
      holder expect signal[SignalMsg.AcceptOffer]

      holder walletAccess MockableWalletAccess()
      holder ledgerAccess MockableLedgerAccess()
      holder ~ buildSendRequest()
      holder expect signal[SignalMsg.Sent]

      issuer expect signal[SignalMsg.Sent]
      val issueCredSent = issuer expect state[State.IssueCredSent]
      assertStatus[State.IssueCredSent](issuer)
      assertIssueSent(issueCredSent)

      holder expect signal[SignalMsg.Received]
      val issueCredReceived = holder expect state[State.IssueCredReceived]
      assertStatus[State.IssueCredReceived](holder)
      assertIssueReceived(issueCredReceived)
    }
  }

  "when Issuer sends wrong message for the current state" - {
    "it should return problem-report but not change state" in { f =>
      val (issuer, holder) = (f.alice, f.bob)

      issuer walletAccess MockableWalletAccess()
      (issuer engage holder) ~ buildSendOffer(Option(false))
      issuer expect signal[SignalMsg.Sent]
      holder expect signal[SignalMsg.AcceptOffer]

      holder walletAccess MockableWalletAccess()
      holder ledgerAccess MockableLedgerAccess()
      holder ~ buildSendRequest()
      holder expect signal[SignalMsg.Sent]

      issuer expect signal[SignalMsg.AcceptRequest]
      // if offer is sent in this state, problem-report is generated
      issuer ~ buildSendOffer(Option(false))
      val pr = issuer expect signal[SignalMsg.ProblemReport]
      println(s"Problem report: $pr")
      pr.description.code shouldBe ProblemReportCodes.unexpectedMessage
      issuer expect state[State.RequestReceived]

      // protocol continues to work normally afterwards.
      issuer ~ Issue(`~please_ack` = Option(PleaseAck()))
      issuer expect signal[SignalMsg.Sent]
      val issueCredSent = issuer expect state[State.IssueCredSent]
      assertStatus[State.IssueCredSent](issuer)
      assertIssueSent(issueCredSent)

      holder expect signal[SignalMsg.Received]
      val issueCredReceived = holder expect state[State.IssueCredReceived]
      assertStatus[State.IssueCredReceived](holder)
      assertIssueReceived(issueCredReceived)

      issuer expect signal[SignalMsg.Ack]
    }
  }

  def assertStatus[T: ClassTag](from: TestEnvir): Unit = {
    from ~ Status()
    from expect state[T]
  }

  def assertProposalSent(proposalSent: State.ProposalSent): Unit = {
    proposalSent.credProposed.cred_def_id shouldBe createTest1CredDef
    proposalSent.credProposed.credential_proposal shouldBe Option(buildCredPreview())
  }

  def assertProposalReceived(proposalReceived: State.ProposalReceived): Unit = {
    proposalReceived.credProposed.cred_def_id shouldBe createTest1CredDef
    proposalReceived.credProposed.credential_proposal shouldBe Option(buildCredPreview())
  }

  def assertOfferSent(offerSent: State.OfferSent): Unit = {
    assertOffer(offerSent.credOffer)
  }

  def assertOfferReceived(offerReceived: State.OfferReceived): Unit = {
    assertOffer(offerReceived.credOffer)
  }

  def assertRequestSent(requestSent: State.RequestSent): Unit = {
    assertRequest(requestSent.credRequest)
  }

  def assertRequestReceived(requestReceived: State.RequestReceived): Unit = {
    assertRequest(requestReceived.credRequest)
  }

  def assertIssueSent(issueSent: State.IssueCredSent): Unit = {
    assertIssuedCred(issueSent.credIssued)
  }

  def assertIssueReceived(issueReceived: State.IssueCredReceived): Unit = {
    assertIssuedCred(issueReceived.credIssued)
  }

  def assertOffer(credOffer: OfferCred): Unit = {
    credOffer.`offers~attach`.size shouldBe 1
    credOffer.price.contains(price) shouldBe true
    val attachedOffer = credOffer.`offers~attach`.head
    attachedOffer.`@id`.get shouldBe "libindy-cred-offer-0"
    attachedOffer.`mime-type`.get shouldBe "application/json"
    attachedOffer.data.base64.nonEmpty shouldBe true
    val dataBase64Decoded = new String(Base64Util.getBase64Decoded(attachedOffer.data.base64))
    dataBase64Decoded shouldBe expectedOfferAttachment
    println("cred offer:" + dataBase64Decoded)
  }

  def assertRequest(requestCred: RequestCred): Unit = {
    requestCred.`requests~attach`.size shouldBe 1
    val attachedRequest = requestCred.`requests~attach`.head
    attachedRequest.`@id`.get shouldBe "libindy-cred-req-0"
    attachedRequest.`mime-type`.get shouldBe "application/json"
    attachedRequest.data.base64.nonEmpty shouldBe true
    val dataBase64Decoded = new String(Base64Util.getBase64Decoded(attachedRequest.data.base64))
    dataBase64Decoded shouldBe expectedReqAttachment
    println("cred req:" + dataBase64Decoded)
  }

  def assertIssuedCred(issueCred: IssueCred): Unit = {
    issueCred.`credentials~attach`.size shouldBe 1
    val attachedCred = issueCred.`credentials~attach`.head
    attachedCred.`@id`.get shouldBe "libindy-cred-0"
    attachedCred.`mime-type`.get shouldBe "application/json"
    attachedCred.data.base64.nonEmpty shouldBe true
    val dataBase64Decoded = new String(Base64Util.getBase64Decoded(attachedCred.data.base64))
    println("cred:" + dataBase64Decoded)
  }

  lazy val price = "0"

  lazy val expectedOfferAttachment =
    s"""
        {
        	"schema_id": "<schema-id>",
        	"cred_def_id": "$createTest1CredDef",
        	"nonce": "nonce",
        	"key_correctness_proof" : "<key_correctness_proof>"
        }"""

  lazy val expectedReqAttachment =
    s"""
        {
          "prover_did" : <prover-DID>,
          "cred_def_id" : $createTest1CredDef,
          "blinded_ms" : <blinded_master_secret>,
          "blinded_ms_correctness_proof" : <blinded_ms_correctness_proof>,
          "nonce": <nonce>
        }"""

  def credValues: Map[String, String] = Map(
      "name" ->  "Joe",
      "age"  -> "41"
  )

  lazy val credPreviewTypeStr = MsgFamily.typeStrFromMsgType(IssueCredentialProtoDef.msgFamily, "credential-preview")

  def buildCredPreview(): CredPreview = {
    val credAttributes = credValues.map { case (name, value) =>
      CredPreviewAttribute(name, value, None)
    }.toVector
    CredPreview(credPreviewTypeStr, credAttributes)
  }

  def buildSendOffer(autoIssue: Option[Boolean] = None): Offer = {
    Offer(createTest1CredDef, credValues, Option(price), auto_issue=autoIssue)
  }

  def buildSendRequest(): Request = {
    Request(createTest1CredDef, Option("some-comment"))
  }
}
