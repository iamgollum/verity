package com.evernym.verity.protocol.testkit

import com.evernym.verity.Status.StatusDetail
import com.evernym.verity.actor.testkit.actor.MockLedgerTxnExecutor
import com.evernym.verity.ledger._
import com.evernym.verity.libindy.WalletAccessLibindy
import com.evernym.verity.protocol.engine._
import com.evernym.verity.testkit.TestWallet
import com.evernym.verity.Status

import scala.util.{Failure, Try}

object MockableLedgerAccess {
  val MOCK_NO_DID = "MOCK_NO_DID"
  def apply(): MockableLedgerAccess = {
    new MockableLedgerAccess()
  }

  def apply(ledgerAvailable: Boolean): MockableLedgerAccess =
    new MockableLedgerAccess(ledgerAvailable=ledgerAvailable)
}

class MockableLedgerAccess(val schemas: Map[String, GetSchemaResp] = MockLedgerData.schemas01,
                           val credDefs: Map[String, GetCredDefResp] = MockLedgerData.credDefs01,
                           val ledgerAvailable: Boolean = true) extends LedgerAccess {
  import MockableLedgerAccess._
  val testWallet = new TestWallet(false)
  implicit val wap = testWallet.wap
  override val walletAccess = new WalletAccessController(
    Set(),
    new WalletAccessLibindy(
      testWallet.appConfig,
      testWallet.walletDetail.walletAPI,
      testWallet.walletDetail.seed
    )
  )

  override def getCredDef(credDefId: String): Try[GetCredDefResp] =
    if(ledgerAvailable) Try(credDefs.getOrElse(credDefId, throw new Exception("Unknown cred def")))
    else Failure(LedgerAccessException(Status.LEDGER_NOT_CONNECTED.statusMsg))

  override def writeCredDef(submitterDID: DID, credDefJson: String): Try[Either[StatusDetail, TxnResp]] =
    if (ledgerAvailable & submitterDID.equals(MOCK_NO_DID)) Failure(LedgerRejectException(s"verkey for $MOCK_NO_DID cannot be found"))
    else if (ledgerAvailable) Try(Right(TxnResp(submitterDID, None, None, "", None, 0, None)))
    else Failure(LedgerAccessException(Status.LEDGER_NOT_CONNECTED.statusMsg))

  override def getSchema(schemaId: String): Try[GetSchemaResp] =
    if(ledgerAvailable) Try(schemas.getOrElse(schemaId, throw new Exception("Unknown schema")))
    else Failure(LedgerAccessException(Status.LEDGER_NOT_CONNECTED.statusMsg))

  override def writeSchema(submitterDID: String, schemaJson: String): Try[Either[StatusDetail, TxnResp]] =
    if (ledgerAvailable & submitterDID.equals(MOCK_NO_DID)) Failure(LedgerRejectException(s"verkey for $MOCK_NO_DID cannot be found"))
    else if (ledgerAvailable) Try(Right(TxnResp(submitterDID, None, None, "", None, 0, None)))
    else Failure(LedgerAccessException(Status.LEDGER_NOT_CONNECTED.statusMsg))
}


object MockLedgerData {
  val txnResp = MockLedgerTxnExecutor.buildTxnResp("5XwZzMweuePeFZzArqvepR", None, None, "107")

  val schemas01 = Map(
    "NcYxiDXkpYi6ov5FcYDi1e:2:gvt:1.0" ->
      GetSchemaResp(
        txnResp,
        Some(SchemaV1(
          "NcYxiDXkpYi6ov5FcYDi1e:2:gvt:1.0",
          "schema-name",
          "0.1",
          Seq("attr-1","attr2"),
          Some(55),
          "0.1"
        ))
      )

  )

  val credDefs01 = Map(
    "NcYxiDXkpYi6ov5FcYDi1e:3:CL:NcYxiDXkpYi6ov5FcYDi1e:2:gvt:1.0:Tag1" ->
    GetCredDefResp(
      txnResp,
      Some(CredDefV1(
        "NcYxiDXkpYi6ov5FcYDi1e:3:CL:NcYxiDXkpYi6ov5FcYDi1e:2:gvt:1.0:Tag1",
        "CL",
        "55",
        "tag",
        "1.0",
        Map.empty
      ))
    )
  )
}

