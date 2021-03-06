package com.evernym.verity.itemmanager


import com.evernym.verity.Status.APP_STATUS_UPDATE_MANUAL
import com.evernym.verity.actor._
import com.evernym.verity.actor.itemmanager.ItemCommonConstants._
import com.evernym.verity.actor.itemmanager._
import com.evernym.verity.actor.testkit.PersistentActorSpec
import com.evernym.verity.actor.testkit.checks.{IgnoreLog, UNSAFE_IgnoreAkkaEvents, UNSAFE_IgnoreLog}
import com.evernym.verity.apphealth.AppStateConstants.{CONTEXT_MANUAL_UPDATE, STATUS_LISTENING}
import com.evernym.verity.apphealth._
import com.evernym.verity.apphealth.state.{InitializingState, ListeningState, SickState}
import com.evernym.verity.util.TimeZoneUtil.getCurrentUTCZonedDateTime
import com.typesafe.config.Config
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Future


class DeleteMessageFailureItemManagerSpec
  extends PersistentActorSpec
    with ItemManagerSpecBase
    with SystemExitSpec {


  override def overrideConfig: Option[Config] = Option {
    watcherConfig
      .withFallback(configForDeleteEventFailure)
  }

  final val ITEM_TYPE = "uap-messages"

  "ItemConfigProvider" - {
    "when tried to add a mapper" - {
      "should be able to add it" in {
        ItemConfigManager.addNewItemContainerMapper(ITEM_TYPE,
          TestTimeBasedItemContainerMapper(ENTITY_ID_MAPPER_VERSION_V1))
        ItemConfigManager.addNewItemContainerMapper(ITEM_TYPE,
          TestTimeBasedItemContainerMapper(LATEST_ITEM_ACTOR_ENTITY_ID_MAPPER_VERSION + 1))
      }
    }
  }

  "ItemManager" - {
    "when sent 'SetItemManagerConfig'" - {
      "should respond 'ItemManagerStateDetail'" taggedAs (UNSAFE_IgnoreLog) in {
        try {
          sendExternalCmdToItemManager(
            itemManagerEntityId1,
            SetItemManagerConfig(
              ITEM_TYPE,
              ITEM_OWNER_VER_KEY,
              migrateItemsToNextLinkedContainer = true,
              migrateItemsToLatestVersionedContainers = false
            )
          )
        }
        catch {
          case t: Throwable =>
//            Is trapping Throwable really a good idea?
//            println(t.getMessage)
//            println(t.getCause.getMessage)
//            println(t.getCause.printStackTrace())
        }
        expectMsgPF() {
          case _: ItemManagerStateDetail =>
        }
      }
    }

    "when sent 'UpdateItem' " - {
      "should respond 'ItemCmdResponse'" in {
        sendExternalCmdToItemManager(itemManagerEntityId1, UpdateItem(ITEM_ID_1, detailOpt=Option("test")))
        expectMsgPF() {
          case ItemCmdResponse(iu: ItemUpdated, senderEntityId) if iu.status == ITEM_STATUS_ACTIVE =>
            updateLatestItemContainerEntityId(ITEM_ID_1, senderEntityId)
        }
      }
    }

    "when sent 'GetState' after adding one item" - {
      "should respond 'ItemManagerStateDetail'" in {
        sendExternalCmdToItemManager(itemManagerEntityId1, GetState)
        expectMsgPF() {
          case ims: ItemManagerStateDetail if ims.headContainerEntityId.isDefined && ims.tailContainerEntityId.isDefined &&
            ims.headContainerEntityId == ims.tailContainerEntityId =>
        }
      }
    }
  }

  "ItemContainer" - {
    "when sent 'GetItem'" - {
      "should return appropriate value" in {
        sendExternalCmdToItemContainer(getLastKnownItemContainerEntityId(ITEM_ID_1), GetItem(ITEM_ID_1))
        expectMsgPF() {
          case ItemCmdResponse(ItemDetailResponse(ITEM_ID_1, ITEM_STATUS_ACTIVE, _, Some("test")), _) =>
        }
        sendExternalCmdToItemManager(itemManagerEntityId1, GetItem(ITEM_ID_1))
        expectMsgPF() {
          case ItemCmdResponse(ItemDetailResponse(ITEM_ID_1, ITEM_STATUS_ACTIVE, _, Some("test")), senderEntityId) =>
            updateLatestItemContainerEntityId(ITEM_ID_1, senderEntityId)
        }
      }
    }
  }

  "ItemManager" - {
    "when sent 'SaveItem' for new item" - {
      "should respond 'ItemCmdResponse'" in {
        sendExternalCmdToItemManager(itemManagerEntityId1, UpdateItem(ITEM_ID_2))
        expectMsgPF() {
          case ItemCmdResponse(iu: ItemUpdated, senderEntityId) if iu.status == ITEM_STATUS_ACTIVE =>
            updateLatestItemContainerEntityId(ITEM_ID_2, senderEntityId)
        }
      }
    }

    //TODO: this commented test and the below one both can't be tested right now
    //due to app state manager being singleton and no ability of resetting it's state.
    //for now, kept this as commented, and keeping the below one (that is the main one) uncommented.
    //    "when sent 'GetItem' for id 1 during app initialization state" - {
    //      "should have moved to new item container" in {
    //        //note: eventually during item container cleanup, if delete events fail (which we are doing in test)
    //        //it should call app state manager with SeriousSystemError event which ultimately shutdowns the service
    //        //by calling System.exit
    //        exitSecurityManager.exitCallCount shouldBe 0
    //        eventually(timeout(Span(10, Seconds))) {
    //          sendExternalCmdToItemManager(itemManagerEntityId1, GetItem(ITEM_ID_1))
    //          exitSecurityManager.exitCallCount shouldBe 1
    //        }
    //      }
    //    }

    "when sent 'GetItem' for id 1 during 'ListeningSuccessful' app state" - {
      "app state manager switches to sick state" taggedAs (IgnoreLog, UNSAFE_IgnoreLog, UNSAFE_IgnoreAkkaEvents) in {
        exitSecurityManager.exitCallCount shouldBe 0
        changeAppStateToListening()
        //note: eventually, during item container cleanup, if delete events fail (which we are doing in test)
        //it should call app state manager with SeriousSystemError event which ultimately change app status to Sick
        eventually(timeout(Span(10, Seconds)), interval(Span(5, Seconds))) {
          sendExternalCmdToItemManager(itemManagerEntityId1, GetItem(ITEM_ID_1))
          AppStateManager.getCurrentState shouldBe SickState
          exitSecurityManager.exitCallCount shouldBe 0
        }
        changeAppStateToListening() //this is so that it doesn't impact any further tests (if any)
      }
    }
  }

  def changeAppStateToListening(): Unit = {
    if (AppStateManager.getCurrentState == InitializingState) {
      AppStateManager << SuccessEventParam(ListeningSuccessful, "SERVICE_INIT",
        CauseDetail("agent-service-started", "agent-service-started-listening-successfully"))
    } else {
      AppStateManager << SuccessEventParam(ManualUpdate(STATUS_LISTENING), CONTEXT_MANUAL_UPDATE,
        CauseDetail(APP_STATUS_UPDATE_MANUAL.statusCode, "manual-update"))
    }
    eventually(timeout(Span(5, Seconds)), interval(Span(2, Seconds))) {
      AppStateManager.getCurrentState shouldBe ListeningState
    }
  }
}


class FailsOnDeleteEventsTestJournal extends TestJournal {
  override def asyncDeleteMessagesTo(persistenceId: String, toSequenceNr: Long): Future[Unit] = {
    Future.failed(new RuntimeException(s"PURPOSEFULLY failing in test (thrown at = $getCurrentUTCZonedDateTime)"))
  }
}
