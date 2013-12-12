package no.digipost.labs.oauth

import scala.util.Try
import org.json4s.DefaultFormats
import scala.Predef.String
import com.ning.http.util.Base64
import org.json4s.jackson.JsonMethods._
import scala.util.Success
import scala.util.Failure

case class AccessToken(access_token: String, refresh_token: String, expires_in: String, token_type: String, id_token: Option[String])

object AccessToken {
  implicit val jsonFormats = DefaultFormats

  case class IdToken(aud: String, exp: String, user_id: String, iss: String, nonce: String)

  object IdToken {
    def apply(idTokenValue: String): IdToken = {
      val decodedIdTokenValue = new String(Base64.decode(idTokenValue))
      parse(decodedIdTokenValue).extract[IdToken]
    }

    def split(idToken: String): Try[(String, String)] = {
      val tokenParts = idToken.split("\\.")
      if (tokenParts.length == 2)
        Success(tokenParts(0), tokenParts(1))
      else
        Failure(new Exception(s"id_token invalid format: $idToken"))
    }
  }

  def validate(accessToken: AccessToken, nonce: String, settings: OAuthSettings, cryptoService: CryptoService): Try[Unit] = {
    def validate(expected: String, actual: String, errorMsg: String): Try[Unit] = if (expected == actual) Success() else Failure(new Exception(errorMsg))

    for {
      idToken <- accessToken.id_token.fold[Try[String]](Failure(new Exception("Missing id_token")))(Success(_))
      (idTokenHash, idTokenValue) <- IdToken.split(idToken)
      _ <- validate(idTokenHash, cryptoService.signWithHmacSha256(idTokenValue, settings.secret), "Invalid id_token hash")
      idTokenObject = IdToken(idTokenValue)
      _ <- validate(settings.clientId, idTokenObject.aud, "id_token param audience did not match")
      _ <- validate(nonce, idTokenObject.nonce, "id_token param nonce did not match")
    } yield ()
  }

  def getUserId(accessToken: AccessToken): Try[String] = {
    for {
      idToken <- accessToken.id_token.fold[Try[String]](Failure(new Exception("Missing id_token")))(Success(_))
      (idTokenHash, idTokenValue) <- IdToken.split(idToken)
      idTokenObject = IdToken(idTokenValue)
    } yield idTokenObject.user_id
  }
}
