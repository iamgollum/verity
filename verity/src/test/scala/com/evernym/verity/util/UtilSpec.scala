package com.evernym.verity.util

import com.evernym.verity.Exceptions.{BadRequestErrorException, InvalidComMethodException, InvalidValueException}
import com.evernym.verity.Status.INVALID_VALUE
import com.evernym.verity.actor.testkit.CommonSpecUtil
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.util.Util._
import com.evernym.verity.UrlDetail


class UtilSpec extends BasicSpec with CommonSpecUtil {

  "Util" - {
    "when asked to replace tokens in a msg without any token and empty map" - {
      "should replace it successfully" in {
        val msg = "push notification content, 12345"
        replaceVariables(msg, Map.empty) shouldBe msg
      }
    }
    "when asked to replace tokens in a msg without any token and non map" - {
      "should replace it successfully" in {
        val msg = "push notification content, 12345"
        replaceVariables(msg, Map("name" -> "test")) shouldBe msg
      }
    }
    "when asked to replace tokens in a msg with token and empty map" - {
      "should replace it successfully" in {
        val msg = "are you on the call with #{name}"
        replaceVariables(msg, Map.empty) shouldBe msg
      }
    }
    "when asked to replace tokens in a msg with token and map without tokens" - {
      "should replace it successfully" in {
        val msg = "are you on the call with #{name}"
        replaceVariables(msg, Map("a" -> "b")) shouldBe msg
      }
    }
    "when asked to replace tokens in a msg with token and map with matching tokens" - {
      "should replace it successfully" in {
        val msg = "are you on the call with #{name}"
        replaceVariables(msg, Map("name" -> "user")) shouldBe "are you on the call with user"
      }
    }
    "when asked to replace tokens in a msg with special characters" - {
      "should replace it successfully" in {
        val msg = "~!@####$%^&*()_}{:?><"
        replaceVariables(msg, Map("name" -> "user")) shouldBe msg
      }
    }
    "when asked to replace tokens in a msg with data which contains special characters" - {
      "should replace it successfully" in {
        val msg = "are you on the call with #{name}"
        val expMsg = "are you on the call with #@%@#%@%@###############################!@###%%$"
        replaceVariables(msg, Map("name" -> "#@%@#%@%@###############################!@###%%$")) shouldBe expMsg
      }
    }
    "when asked to parse url" - {
      "should respond with proper parsed url detail" in {
        val caught1 = intercept[InvalidComMethodException] { UrlDetail("xyz://127.0.0.1:9000/agent/callback") }
        assert(caught1.getMessage.contains("unknown protocol: xyz"))

        val caught2 = intercept[InvalidComMethodException] { UrlDetail("localhost") }
        assert(caught2.getMessage.contains("no protocol: localhost"))

        val caught3 = intercept[InvalidComMethodException] { UrlDetail("127.128.129.130") }
        assert(caught3.getMessage.contains("no protocol: 127.128.129.130"))

        UrlDetail("127.0.0.1:9000/agent/callback") shouldBe UrlDetail("http", "127.0.0.1", 9000, Option("agent/callback"))
        UrlDetail("127.0.0.1:9000/agent/callback?k1=v1") shouldBe UrlDetail("http", "127.0.0.1", 9000, Option("agent/callback"), Option("k1=v1"))

        UrlDetail("http://127.0.0.1:9000/agent/callback?k1=v1") shouldBe UrlDetail("http", "127.0.0.1", 9000, Option("agent/callback"), Option("k1=v1"))
        UrlDetail("https://127.0.0.1:9000/agent/callback") shouldBe UrlDetail("https", "127.0.0.1", 9000, Option("agent/callback"))

        UrlDetail("http://localhost:9000/agent/callback") shouldBe UrlDetail("http", "localhost", 9000, Option("agent/callback"))
        UrlDetail("https://localhost:9001/agent/callback1?k1=v1") shouldBe UrlDetail("https", "localhost", 9001, Option("agent/callback1"), Option("k1=v1"))

        UrlDetail("https://localhost/agent/callback1?k1=v1") shouldBe UrlDetail("https", "localhost", 443, Option("agent/callback1"), Option("k1=v1"))
        UrlDetail("http://localhost/agent/callback1") shouldBe UrlDetail("http", "localhost", 80, Option("agent/callback1"))

        UrlDetail("https://localhost.com/agent/callback1") shouldBe UrlDetail("https", "localhost.com", 443, Option("agent/callback1"))
        UrlDetail("https://localhost.com/agent/callback1") shouldBe UrlDetail("https", "localhost.com", 443, Option("agent/callback1"))

        UrlDetail("http://localhost:9000/agent/callback").isHttp shouldBe true
        UrlDetail("http://localhost:9000/agent/callback").isHttps shouldBe false
        UrlDetail("https://localhost.com/agent/callback1").isHttps shouldBe true
        UrlDetail("https://localhost.com/agent/callback1").isHttp shouldBe false

        UrlDetail("https://localhost.com/agent/callback1").api shouldBe "localhost.com:443/agent/callback1"
        UrlDetail("https://localhost.com/agent/callback1").url shouldBe "https://localhost.com:443/agent/callback1"
        UrlDetail("https://localhost.com/agent/callback1").path shouldBe "agent/callback1"
      }
    }
    "when asked to instantiate UrlDetail object with different constructors" - {
      "should be able to do it successfully" in {
        UrlDetail("localhost:9001/agency/msg") shouldBe UrlDetail("localhost:9001/agency/msg")
        UrlDetail("http://localhost:9001/agency/msg") shouldBe UrlDetail("localhost", 9001, Some("agency/msg"))
        UrlDetail("localhost:9001/agency/msg") shouldBe UrlDetail("localhost", 9001, Some("agency/msg"))
        UrlDetail("http://api.enym.com/agency/msg") shouldBe UrlDetail("api.enym.com", 80, Some("agency/msg"))
        UrlDetail("https://api.enym.com/agency/msg") shouldBe UrlDetail("api.enym.com", 443, Some("agency/msg"))
      }
    }
    "when asked to normalized valid phone number" - {
      "should be able to get corrected normalized phone number" in {
        getNormalizedPhoneNumber("12345") shouldBe "12345"
        getNormalizedPhoneNumber("+12345") shouldBe "+12345"
        getNormalizedPhoneNumber("+1(234)-567") shouldBe "+1234567"
        getNormalizedPhoneNumber("+1(234) 567") shouldBe "+1234567"
      }
    }
    "when asked to normalized invalid phone number" - {
      "should throw exception" in {
        val invalidPhones = Set("+A1(234) 567", "")
        invalidPhones.foreach { iph =>
          val e = intercept[BadRequestErrorException] {
            getNormalizedPhoneNumber(iph)
          }
          e.respCode shouldBe INVALID_VALUE.statusCode
          e.respMsg shouldBe Option("invalid phone number")
        }
      }
    }
    "when asked to check if correct DID and verKey belong to each other" - {
      "should be able to check it" in {
        val idData = generateNewAgentDIDDetail()
        checkIfDIDBelongsToVerKey(idData.did, idData.verKey)
      }
    }
    "when asked to check if incorrect DID and verKey belong to each other" - {
      "should be able to check it" in {
        val idData1 = generateNewAgentDIDDetail()
        val idData2 = generateNewAgentDIDDetail()
        intercept[InvalidValueException] {
          checkIfDIDBelongsToVerKey(idData1.did, idData2.verKey)
        }
      }
    }

    "when asked to test if given string is an IP address" - {
      "should be able to check it" in {
        SubnetUtilsExt.isClassfulIpAddress("127.0.0.1") shouldBe true
        SubnetUtilsExt.isClassfulIpAddress("127.0.0.1/32") shouldBe false
        SubnetUtilsExt.isClassfulIpAddress("randomStr") shouldBe false
        SubnetUtilsExt.isClassfulIpAddress("127.0.0.1/") shouldBe false
        SubnetUtilsExt.isClassfulIpAddress("127.0.0.1/str") shouldBe false
      }
    }

    "when asked to test if given string is an CIDR address" - {
      "should be able to check it" in {
        SubnetUtilsExt.isClasslessIPAddress("127.0.0.1/32") shouldBe true
        SubnetUtilsExt.isClasslessIPAddress("127.0.0.1") shouldBe false
        SubnetUtilsExt.isClasslessIPAddress("randomStr") shouldBe false
        SubnetUtilsExt.isClasslessIPAddress("127.0.0.1/") shouldBe false
        SubnetUtilsExt.isClasslessIPAddress("127.0.0.1/str") shouldBe false
      }
    }

  }

  "SubnetUtilsExt" - {

    "when asked to validate conflict" - {
      "should validate it" in {
        new SubnetUtilsExt("127.0.0.24/16").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe true
        new SubnetUtilsExt("127.0.0.24/32").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe true
        new SubnetUtilsExt("127.0.0.24/12").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe true

        new SubnetUtilsExt("127.255.255.255/32").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe false
        new SubnetUtilsExt("127.255.255.254/31").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe false
        new SubnetUtilsExt("129.0.0.24/32").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe false
        new SubnetUtilsExt("129.0.0.24/32").isIpRangeConflicting(new SubnetUtilsExt("127.0.0.24/16")) shouldBe false
      }
    }
  }

  "LRUCache" - {
    "when created" - {
      val cache = makeCache[String, Int](3)

      "it should have zero size" in {
        cache.size() shouldBe 0
      }

      "it should not contain anything" in {
        cache.containsKey("a") shouldBe false
        Option(cache.get("a")) shouldBe None
      }
    }

    "after putting one element" - {
      val cache = makeCache[String, Int](3)
      cache.put("a", 3)

      "it should have size one" in {
        cache.size() shouldBe 1
      }

      "it should contain added element" in {
        cache.containsKey("a") shouldBe true
        cache.get("a") shouldBe 3
        Option(cache.get("a")) shouldBe Some(3)
      }

      "it should not contain anything else" in {
        cache.containsKey("b") shouldBe false
        Option(cache.get("b")) shouldBe None
      }
    }

    "putting zero element of elementary type also plays well with Option" in {
      val cache = makeCache[String, Int](3)
      cache.put("a", 0)

      cache.containsKey("a") shouldBe true
      cache.get("a") shouldBe 0
      Option(cache.get("a")) shouldBe Some(0)
    }

    "after putting more elements than capacity" - {
      val cache = makeCache[String, Int](3)
      cache.put("a", 3)
      cache.put("b", 4)
      cache.put("c", 5)
      cache.put("d", 6)
      cache.put("e", 7)

      "it should have size equal to capacity" in {
        cache.size() shouldBe 3
      }

      "it should contain last added elements" in {
        cache.get("c") shouldBe 5
        cache.get("d") shouldBe 6
        cache.get("e") shouldBe 7
      }

      "it should not contain first added elements" in {
        cache.containsKey("a") shouldBe false
        cache.containsKey("b") shouldBe false
      }
    }

    "after putting more elements than capacity while updating old elements" - {
      val cache = makeCache[String, Int](3)
      cache.put("a", 3)
      cache.put("b", 4)
      cache.put("c", 5)
      cache.put("b", 2)
      cache.put("d", 6)
      cache.put("e", 7)

      "it should have size equal to capacity" in {
        cache.size() shouldBe 3
      }

      "it should contain last added elements" in {
        cache.get("b") shouldBe 2
        cache.get("d") shouldBe 6
        cache.get("e") shouldBe 7
      }

      "it should not contain first added elements" in {
        cache.containsKey("a") shouldBe false
        cache.containsKey("c") shouldBe false
      }
    }

    "after putting doing more updates than capacity withoug exceeding total allowed element count" - {
      val cache = makeCache[String, Int](3)
      cache.put("a", 3)
      cache.put("b", 4)
      cache.put("c", 5)
      cache.put("b", 2)

      "it should have size equal to capacity" in {
        cache.size() shouldBe 3
      }

      "it should contain all added elements" in {
        cache.get("a") shouldBe 3
        cache.get("b") shouldBe 2
        cache.get("c") shouldBe 5
      }
    }

    "after putting more elements than capacity while accessing old elements" - {
      val cache = makeCache[String, Int](3)
      cache.put("a", 3)
      cache.put("b", 4)
      cache.put("c", 5)
      cache.get("b")
      cache.put("d", 6)
      cache.put("e", 7)

      "it should have size equal to capacity" in {
        cache.size() shouldBe 3
      }

      "it should contain last touched elements" in {
        cache.get("b") shouldBe 4
        cache.get("d") shouldBe 6
        cache.get("e") shouldBe 7
      }

      "it should not contain first added elements" in {
        cache.containsKey("a") shouldBe false
        cache.containsKey("c") shouldBe false
      }
    }
  }
}