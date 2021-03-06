package com.evernym.verity.libindy

import java.util.UUID

import com.evernym.verity.actor.testkit.CommonSpecUtil
import com.evernym.verity.config.AppConfigWrapper
import com.evernym.verity.testkit.BasicSpecWithIndyCleanup
import com.evernym.verity.vault.{WalletAlreadyExist, WalletAlreadyOpened}
import org.hyperledger.indy.sdk.did.DidResults.CreateAndStoreMyDidResult
import org.hyperledger.indy.sdk.did._


class LibIndyWalletProviderSpec extends BasicSpecWithIndyCleanup with CommonSpecUtil {
  
  lazy val lip1 = new LibIndyWalletProvider(AppConfigWrapper)
  lazy val lip2 = new LibIndyWalletProvider(AppConfigWrapper)
  lazy val wn1: String = UUID.randomUUID().toString
  lazy val wn2: String = UUID.randomUUID().toString

  var lip1Wallet: LibIndyWalletExt = _

  var lip1KeyCreatedResult: CreateAndStoreMyDidResult = _
  var walletKey: String = _

  "LibIndyWalletProvider instance one" - {

    "when asked to create new wallet" - {
      "should be able to create it successfully" in {
        walletKey = lip1.generateKey()
        lip1.create(wn1, walletKey, testWalletConfig)
      }
    }

    "when asked to open created wallet" - {
      "should be able to open the wallet" in {
        lip1Wallet = lip1.open(wn1, walletKey, testWalletConfig)
      }
    }

    "when asked to open already opened wallet" - {
      "throws WalletAlreadyOpened exception" in {
        intercept[WalletAlreadyOpened] {
          lip1.open(wn1, walletKey, testWalletConfig)
        }
      }
    }

    "when asked to create new key" - {
      "should be able to create it successfully" in {
        val DIDJson = new DidJSONParameters.CreateAndStoreMyDidJSONParameter(null, null, null, null)
        lip1KeyCreatedResult = Did.createAndStoreMyDid(lip1Wallet.wallet, DIDJson.toJson).get
      }
    }

    "when asked to get ver key back" - {
      "should be able to get it successfully" in {
        val vk = Did.keyForLocalDid(lip1Wallet.wallet, lip1KeyCreatedResult.getDid).get
        vk shouldBe lip1KeyCreatedResult.getVerkey
      }
    }

    "when asked to store their key" - {
      "should be able to store it successfully" in {
        val did1 = generateNewDid()
        lip1Wallet.storeTheirDID(did1.DID, did1.verKey)
      }
    }
  }

  "LibIndyWalletProvider instance two" - {
    "when asked to create existing new wallet" - {
      "should fail " in {
        intercept[WalletAlreadyExist] {
          lip2.create(wn1, walletKey, testWalletConfig)
        }
      }
    }
  }

  "LibIndyWalletProvider instance one" - {
    "when asked to get ver key again" - {
      "should be able to get it successfully" in {
        val vk = Did.keyForLocalDid(lip1Wallet.wallet, lip1KeyCreatedResult.getDid).get
        vk shouldBe lip1KeyCreatedResult.getVerkey
      }
    }
  }

}
