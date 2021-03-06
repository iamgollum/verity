package com.evernym.verity.itemmanager

import java.time.LocalDateTime

import com.evernym.verity.actor.ForIdentifier
import com.evernym.verity.actor.itemmanager.ItemCommonConstants.ENTITY_ID_MAPPER_VERSION_V1
import com.evernym.verity.actor.itemmanager.ItemCommonType.{ItemContainerEntityId, ItemId, VersionId}
import com.evernym.verity.actor.itemmanager.{ExternalCmdWrapper, ItemConfigManager, ItemContainerMapper}
import com.evernym.verity.actor.persistence.PersistenceConfig
import com.evernym.verity.actor.testkit.AkkaTestBasic.{getNextAvailablePort, systemNameForPort, tmpdir}
import com.evernym.verity.actor.testkit.PersistentActorSpec
import com.evernym.verity.testkit.BasicSpec
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.Eventually

trait ItemManagerSpecBase extends PersistentActorSpec with BasicSpec with Eventually {

  final val ITEM_ID_1 = "123"
  final val ITEM_ID_2 = "242"
  final val ITEM_ID_3 = "335"
  final val ITEM_ID_4 = "584"

  final val ITEM_OWNER_ID = "uap-1"
  final val ITEM_OWNER_VER_KEY = None

  final val ORIG_ITEM_DETAIL = "detail"
  final val UPDATED_ITEM_DETAIL = "detail updated"

  val itemManagerEntityId1: String = ITEM_OWNER_ID

  implicit val persistenceConfig: PersistenceConfig = PersistenceConfig (
    allowOnlyEvents=false,
    allowOnlySnapshots=true,
    snapshotEveryNEvents=None,
    deleteEventsOnSnapshot = true,
    keepNSnapshots = Option(1))

  var itemContainerEntityDetails: Map[ItemId, ItemContainerEntityDetail] = Map.empty

  def getLastKnownItemContainerEntityId(itemId: ItemId): ItemContainerEntityId =
    itemContainerEntityDetails(itemId).latestEntityId

  def getOriginalItemContainerEntityId(itemId: ItemId): ItemContainerEntityId =
    itemContainerEntityDetails(itemId).originalEntityId

  def updateLatestItemContainerEntityId(itemId: ItemId, newItemContainerId: ItemContainerEntityId): Unit = {
    val updated = itemContainerEntityDetails
      .getOrElse(itemId, ItemContainerEntityDetail(newItemContainerId, newItemContainerId))
      .copy(latestEntityId=newItemContainerId)
    itemContainerEntityDetails = itemContainerEntityDetails + (itemId -> updated)
  }

  def prepareExternalCmdWrapper(cmd: Any): ExternalCmdWrapper = ExternalCmdWrapper(cmd, None)

  def sendExternalCmdToItemManager(toId: ItemContainerEntityId, cmd: Any): Unit = {
    sendCmdToItemManager(toId, prepareExternalCmdWrapper(cmd))
  }

  def sendCmdToItemManager(toId: ItemContainerEntityId, cmd: Any): Unit = {
    platform.itemManagerRegion ! ForIdentifier(toId, cmd)
  }

  def sendCmdToItemContainer(toId: ItemContainerEntityId, cmd: Any): Unit = {
    platform.itemContainerRegion ! ForIdentifier(toId, cmd)
  }

  def sendExternalCmdToItemContainer(toId: ItemContainerEntityId, cmd: Any): Unit = {
    platform.itemContainerRegion ! ForIdentifier(toId, ExternalCmdWrapper(cmd, None))
  }

  case class ItemContainerEntityDetail(originalEntityId: ItemContainerEntityId, latestEntityId: ItemContainerEntityId)

  case class TestTimeBasedItemContainerMapper(versionId: VersionId) extends ItemContainerMapper {

    def getItemContainerId(itemId: ItemId): ItemContainerEntityId = {
      val ldTime = LocalDateTime.now()
      val paddedMonth = ldTime.getMonthValue.toString.reverse.padTo(2, '0').reverse
      val paddedDay = ldTime.getDayOfMonth.toString.reverse.padTo(2, '0').reverse
      val paddedHour = ldTime.getHour.toString.reverse.padTo(2, '0').reverse
      val paddedMinutes = ldTime.getMinute.toString.reverse.padTo(2, '0').reverse
      val paddedSeconds = ldTime.getSecond.toString.reverse.padTo(2, '0').reverse
      s"${ldTime.getYear}$paddedMonth$paddedDay$paddedHour$paddedMinutes$paddedSeconds"
    }
  }

  lazy val LATEST_ITEM_ACTOR_ENTITY_ID_MAPPER_VERSION = ENTITY_ID_MAPPER_VERSION_V1
  lazy val LATEST_CONFIGURED_ITEM_ACTOR_ENTITY_ID_VERSION_PREFIX = ItemConfigManager.entityIdVersionPrefix(appConfig)

  def configForDeleteEventFailure: Config = ConfigFactory parseString {
    s"""
      akka {
        persistence {
          journal {
            plugin = "akka.persistence.journal.FailsOnDeleteEventsTestJournal"
            FailsOnDeleteEventsTestJournal {
              class = "com.evernym.verity.itemmanager.FailsOnDeleteEventsTestJournal"
              dir = ${tmpdir(systemNameForPort(getNextAvailablePort))}
              native = false
            }
          }
        }
      }
    """
  }

  def watcherConfig: Config =
    ConfigFactory parseString {
      """
        |verity {
        |
        |  user-agent-pairwise-watcher {
        |    enabled = true
        |
        |    scheduled-job {
        |      initial-delay-in-seconds = 5
        |      interval-in-seconds = 3
        |    }
        |  }
        |
        |  item-container {
        |
        |    scheduled-job {
        |      initial-delay-in-seconds = 1
        |      interval-in-seconds = 1
        |    }
        |
        |    migration {
        |      chunk-size = 20
        |    }
        |
        |  }
        |
        |  cache {
        |    agency-detail-cache-expiration-time-in-seconds = 0
        |    endpoint-cache-expiration-time-in-seconds = 0
        |  }
        |}
        |""".stripMargin
    }

}
