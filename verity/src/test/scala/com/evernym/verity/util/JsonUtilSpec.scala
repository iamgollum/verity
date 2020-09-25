package com.evernym.verity.util

import com.evernym.verity.agentmsg.DefaultMsgCodec
import com.evernym.verity.testkit.BasicSpec

class JsonUtilSpec extends BasicSpec {

  "JsonUtil" - {

    "when given proper json to parse" - {
      "should successfully parse it to JSONObject" in {
        val properJson = """{"key":"value"}"""
        val map = DefaultMsgCodec.fromJson[Map[String,String]](properJson)
        map shouldBe a[Map[_, _]]
        map.get("key") shouldBe Some("value")
      }
    }

    "when given invalid json to parse" - {
      "should throw exception" in {
        val nonJsonValue = "anyValue"
        an [Exception]  should be thrownBy DefaultMsgCodec.fromJson[Map[String,String]](nonJsonValue)
      }
    }

    "mapToJson" - {
      "Only String Map" in {
        val map = Map(
          "foo" -> "bar",
          "test" -> "exp"
        )
        JsonUtil.mapToJson(map) shouldBe an [String]
      }
      "Mixed Type Map" in {
        val map = Map(
          "foo" -> "bar",
          "test" -> 1,
          "bool" -> true,
          "null" -> null
        )
        JsonUtil.mapToJson(map) shouldBe an [String]
      }
    }

    "seqToJson" - {
      "Empty Seq" in {
        val map = Map(
          "foo" -> "bar",
          "test" -> "exp"
        )
        JsonUtil.seqToJson(Seq()) shouldBe an [String]
        JsonUtil.seqToJson(Seq()) shouldBe "[]"
      }
      "Simple seqs" in {
        JsonUtil.seqToJson(Seq("test")) shouldBe "[\"test\"]"
        JsonUtil.seqToJson(Seq("test", "foo")) shouldBe "[\"test\",\"foo\"]"
      }
    }

    "isDeserializableAsJson" - {
      "valid json" in {
        JsonUtil.isDeserializableAsJson("""{"key1":"value1"}""".getBytes) shouldBe true
      }

      "invalid json" in {
        val invalidJsonBytes1 = Array[Byte](49, 32, 89, 89, -17, -65, -67, -17, -65, -67, 62, -17, -65, -67, -17, -65, -67, 4, -17, -65, -67, -17, -65, -67, -33, -112, 95, 10, -17, -65, -67, 22, 79, -17, -65, -67, 53, -17, -65, -67, 79, -17, -65, -67, -17, -65, -67, -17, -65, -67, 105, -17, -65, -67, 118, -46, -125, -38, -115, -17, -65, -67, -17, -65, -67, -48, -116, -17, -65, -67, 25, 113, 39, -17, -65, -67, -17, -65, -67, 84, 7, 82, 87, -17, -65, -67, -17, -65, -67, -17, -65, -67, 95, 68, -17, -65, -67, 32, 53, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, 24, -41, -114, -17, -65, -67, 64, 125, 122, -17, -65, -67, -17, -65, -67, 32, 1, -17, -65, -67, -17, -65, -67, 65, 109, 87, 54, -17, -65, -67, -17, -65, -67, 36, 1, -17, -65, -67, -17, -65, -67, -17, -65, -67, -62, -85, -17, -65, -67, 75, -17, -65, -67, -17, -65, -67, 19, 17, 116, -56, -120, 103, -17, -65, -67, 83, 38, 61, -17, -65, -67, 45, 124, 21, 114, -17, -65, -67, 30, -48, -95, 49, 105, 21, -17, -65, -67, 42, -17, -65, -67, -17, -65, -67, 87, -17, -65, -67, 48, 118, -17, -65, -67, 12, -17, -65, -67, -17, -65, -67, 11, -17, -65, -67, 109, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, 12, 76, -17, -65, -67, 87, 124, 61, -17, -65, -67, -17, -65, -67, 48, 44, -17, -65, -67, 69, -17, -65, -67, -17, -65, -67, 101, -17, -65, -67, 4, 97, 21, -17, -65, -67, -17, -65, -67, 10, -17, -65, -67, -17, -65, -67, 32, -36, -107, -17, -65, -67, -17, -65, -67, 77, 62, 121, 90, -17, -65, -67, 45, 104, 94, 82, 43, 54, -17, -65, -67, 126, -17, -65, -67)
        println("invalidJsonBytes1: " + new String(invalidJsonBytes1))
        JsonUtil.isDeserializableAsJson(invalidJsonBytes1) shouldBe false

        val invalidJsonBytes2 = Array[Byte](49, 10, 89, 89, -17, -65, -67, -17, -65, -67, 62, -17, -65, -67, -17, -65, -67, 4, -17, -65, -67, -17, -65, -67, -33, -112, 95, -17, -65, -67, 22, 79, -17, -65, -67, 53, -17, -65, -67, 79, -17, -65, -67, -17, -65, -67, -17, -65, -67, 105, -17, -65, -67, 118, -46, -125, -38, -115, -17, -65, -67, -17, -65, -67, -48, -116, -17, -65, -67, 25, 113, 39, -17, -65, -67, -17, -65, -67, 84, 7, 82, 87, -17, -65, -67, -17, -65, -67, -17, -65, -67, 95, 68, -17, -65, -67, 32, 53, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, 24, -41, -114, -17, -65, -67, 64, 125, 122, -17, -65, -67, -17, -65, -67, 32, 1, -17, -65, -67, -17, -65, -67, 65, 109, 87, 54, -17, -65, -67, -17, -65, -67, 36, 1, -17, -65, -67, -17, -65, -67, -17, -65, -67, -62, -85, -17, -65, -67, 75, -17, -65, -67, -17, -65, -67, 19, 17, 116, -56, -120, 103, -17, -65, -67, 83, 38, 61, -17, -65, -67, 45, 124, 21, 114, -17, -65, -67, 30, -48, -95, 49, 105, 21, -17, -65, -67, 42, -17, -65, -67, -17, -65, -67, 87, -17, -65, -67, 48, 118, -17, -65, -67, 12, -17, -65, -67, -17, -65, -67, 11, -17, -65, -67, 109, -17, -65, -67, -17, -65, -67, -17, -65, -67, -17, -65, -67, 12, 76, -17, -65, -67, 87, 124, 61, -17, -65, -67, -17, -65, -67, 48, 44, -17, -65, -67, 69, -17, -65, -67, -17, -65, -67, 101, -17, -65, -67, 4, 97, 21, -17, -65, -67, -17, -65, -67, 10, -17, -65, -67, -17, -65, -67, 32, -36, -107, -17, -65, -67, -17, -65, -67, 77, 62, 121, 90, -17, -65, -67, 45, 104, 94, 82, 43, 54, -17, -65, -67, 126, -17, -65, -67)
        println("invalidJsonBytes2: " + new String(invalidJsonBytes2))
        JsonUtil.isDeserializableAsJson(invalidJsonBytes2) shouldBe false
      }
    }
  }
}
