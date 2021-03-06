package com.evernym.verity.urlshortener

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model._
import com.evernym.verity.ExecutionContextProvider.futureExecutionContext
import com.evernym.verity.Status.URL_SHORTENING_FAILED
import com.evernym.verity.actor.testkit.ActorSpec
import com.evernym.verity.testkit.BasicSpec
import com.evernym.verity.util.Util.buildHandledError
import org.json.JSONObject
import org.mockito.ArgumentCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}

class YOURLSSvcSpec extends ActorSpec with BasicSpec with MockitoSugar with ScalaFutures {

  class TestYOURLSSvc(mock: HttpRequest => Future[HttpResponse]) extends YOURLSSvc(appConfig) {
    override lazy val timeout: Duration = testTimeoutSeconds.seconds
    override lazy val apiUrl: String = testApiUrl
    override lazy val apiSignature: Option[String] = Option(testApiSignature)

    override def httpClient(request: HttpRequest)
                           (implicit actorSystem: ActorSystem): Future[HttpResponse] = mock(request)
  }

  class TestYOURLSSvcUserPass(mock: HttpRequest => Future[HttpResponse]) extends TestYOURLSSvc(mock) {
    override lazy val apiSignature: Option[String] = None
    override lazy val apiUsername: String = testApiUsername
    override lazy val apiPassword: String = testApiPassword
  }

  val testTimeoutSeconds: Int = 2
  val testApiUrl: String = "http://shortener/api"
  val testApiSignature: String = "apiSignature"
  val testApiUsername: String = "testUser"
  val testApiPassword: String = "testPass"

  val longUrl: String = "http://long.url/agency/msg?c_i=eyJsYWJlbCI6IkFydGVtIiwic2VydmljZUV"
  val shortUrl: String = "http://sho.rt/url"
  lazy val formData: Map[String, String] = Map(
    "action" -> "shorturl",
    "format" -> "json",
    "signature" -> testApiSignature
  )

  lazy val formDataUserPass: Map[String, String] = Map(
    "action" -> "shorturl",
    "format" -> "json",
    "username" -> testApiUsername,
    "password" -> testApiPassword
  )

  lazy val successfulJsonMsg: JSONObject = {
    val json = new JSONObject
    json.put("status", "success")
    json.put("shorturl", shortUrl)
    json
  }

  lazy val successfulResponse: HttpResponse = HttpResponse(
    StatusCodes.OK,
    entity=HttpEntity(ContentTypes.`application/json`, successfulJsonMsg.toString)
  )

  "YOURLS service provider" - {
    "when url shortener respond with success" - {
      "should return short url" in {
        val response = HttpResponse(StatusCodes.OK, entity=responseEntity())
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Right(shortUrl)

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with failure in json msg" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.OK, entity=responseEntity("failure"))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with 400 - BadRequest" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.BadRequest, entity=responseEntity("failure"))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with 401 - Unauthorized" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.Unauthorized, entity=responseEntity("failure"))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with 404 - NotFound" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.NotFound, entity=responseEntity("failure"))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with 500 - InternalServerError" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.InternalServerError, entity=responseEntity("failure"))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with invalid json msg" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.OK, entity=HttpEntity(ContentTypes.`application/json`, "{\"name\": "))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with json msg without status" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.OK, entity=HttpEntity(ContentTypes.`application/json`, "{\"name\": \"value\""))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener respond with json msg without shorturl" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.OK, entity=HttpEntity(ContentTypes.`application/json`, "{\"status\": \"success\""))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when url shortener response timeouts" - {
      "should return an HandledErrorException" in {
        val response = HttpResponse(StatusCodes.OK, entity=HttpEntity(ContentTypes.`application/json`, "{\"status\": \"success\""))
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvc(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future{
          Thread.sleep((testTimeoutSeconds + 1) * 1000) // sleep longer that timeout
          response
        }

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Left(buildHandledError(URL_SHORTENING_FAILED))

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formData + ("url" -> longUrl)).toEntity
      }
    }

    "when using username and password instead of signature" - {
      "should work correctly" in {
        val response = HttpResponse(StatusCodes.OK, entity=responseEntity())
        lazy val mockedHttpClient: HttpRequest => Future[HttpResponse] = mock[HttpRequest => Future[HttpResponse]]
        val service = new TestYOURLSSvcUserPass(mockedHttpClient)
        val captor: ArgumentCaptor[HttpRequest] = ArgumentCaptor.forClass(classOf[HttpRequest])

        when(mockedHttpClient(any[HttpRequest])) thenReturn Future.successful(response)

        val result = service.shortenURL(UrlInfo(longUrl))(system)
        result shouldBe Right(shortUrl)

        verify(mockedHttpClient)(captor.capture())
        val request: HttpRequest = captor.getValue
        request.method shouldBe POST
        request.uri.toString shouldBe testApiUrl
        request.entity shouldBe FormData(formDataUserPass + ("url" -> longUrl)).toEntity
      }
    }

  }

  def responseEntity(status: String = "success", shortUrl: String = shortUrl): ResponseEntity = {
    val json = new JSONObject
    json.put("status", status)
    json.put("shorturl", shortUrl)
    HttpEntity(ContentTypes.`application/json`, json.toString)
  }
}
