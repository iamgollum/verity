package com.evernym.verity.actor.testkit.actor

import java.net.ServerSocket

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

trait ActorSystemConfig {

  def journalFailingOnLargeEvents =
    s"""
      akka {
        persistence {
          journal {
            plugin = "akka.persistence.journal.FailsOnLargeEventTestJournal"
            FailsOnLargeEventTestJournal {
              class = "com.evernym.verity.actor.FailsOnLargeEventTestJournal"
              dir = ${tmpdir(systemNameForPort(getNextAvailablePort))}
              native = false
            }
          }
        }
      }
    """

  def levelDBJournal(tdir: String) =
    s"""
      akka {
        persistence {
          journal {
            plugin = "akka.persistence.journal.leveldb"
            leveldb {
              dir = "$tdir/journal"
              native = false
            }
          }
        }
      }

    """

  def singleNodeClusterSharded(systemName: String, port: Int, tdir: String, overrideConfig: Option[String]=None): Config =
    ConfigFactory.parseString(
      s"""
  akka {

    loglevel = "WARNING"

    test {
      single-expect-default = 5s
    }

    loggers = ["akka.event.slf4j.Slf4jLogger", "com.evernym.verity.actor.testkit.QuietTestEventListener"]
    stdout-loglevel = "off"

    debug {
      receive = on
    }

    actor {
      provider = "akka.cluster.ClusterActorRefProvider"
    }

    remote {
      log-remote-lifecycle-events = off
      artery.canonical {
        hostname = "127.0.0.1"
        port = $port
      }
    }

    persistence {
      snapshot-store {
        plugin = "akka.persistence.snapshot-store.local"
        local.dir = "$tdir/snapshots"
      }
    }

    cluster {

      seed-nodes = [
        "akka://$systemName@127.0.0.1:$port"
      ]
      roles = ["backend"]
      jmx.multi-mbeans-in-same-jvm = on  # this is to get rid of warnings in tests
    }

    actor {

      serializers {
        protoser = "com.evernym.verity.actor.event.serializer.ProtoBufSerializer"
        kryo-akka = "com.twitter.chill.akka.AkkaSerializer"
      }

      serialization-bindings {
        "com.evernym.verity.actor.TransformedMultiEvent" = protoser
        "com.evernym.verity.actor.TransformedEvent" = protoser
        "com.evernym.verity.actor.TransformedState" = protoser
        "com.evernym.verity.actor.ActorMessage" = kryo-akka
      }

      allow-java-serialization = off

      //NOTE: below config is to test message serialization/deserialization in testing environment to catch any related issues early
      serialize-messages = on
    }
  }

  """
        + levelDBJournal(tdir)            //default persistence
        + overrideConfig.getOrElse("")    //override any of the above default configuration
    )


  def tmpdir(systemName: String) = s"target/actorspecs/$systemName"

  def overrideConfigValuesIfAny(oldConfig: Config): Config = {
    val testConfig = ConfigFactory.load()
    val testConfigNames = Set("akka.test.single-expect-default")
    var newConfig = oldConfig
    testConfigNames.foreach { tcn =>
      newConfig = oldConfig.withValue(tcn, ConfigValueFactory.fromAnyRef(testConfig.getString(tcn)))
    }
    newConfig
  }

  def systemNameForPort(port: Int): String = "actorSpecSystem" + port

  def getConfigByPort(port: Int, overrideConfig: Option[String]=None): Config = {
    val systemName = systemNameForPort(port)
    val tdir = tmpdir(systemName)
    overrideConfigValuesIfAny(singleNodeClusterSharded(systemName, port, tdir, overrideConfig))
      .withFallback(ConfigFactory.load("application.conf"))
  }

  def getConfigByJournalPath(tdir: String): Config = {
    val port = getNextAvailablePort
    val systemName = "actorSpecSystem" + port
    overrideConfigValuesIfAny(singleNodeClusterSharded(systemName, port, tdir))
  }

  def getConfig(overrideConfig: Option[String]=None): Config = {
    getConfigByPort(getNextAvailablePort, overrideConfig)
  }

  def getOverriddenConfig(overrideConfig: Option[String]=None): Config = {
    val port = getNextAvailablePort
    getConfigByPort(port, overrideConfig)
  }

  def system(overrideConfig: Option[String]=None): ActorSystem = {
    systemWithConfig(overrideConfig)._1
  }

  def systemWithConfig(overrideConfig: Option[String]=None): (ActorSystem, Config) = {
    val port = getNextAvailablePort
    val config = getConfigByPort(port, overrideConfig)
    val systemName = systemNameForPort(port)
    (ActorSystem(systemName, config), config)
  }

  def getNextAvailablePort: Int = {
    val ss = new ServerSocket(0)
    ss.setReuseAddress(true)
    val port = ss.getLocalPort
    ss.close()
    port
  }
}
