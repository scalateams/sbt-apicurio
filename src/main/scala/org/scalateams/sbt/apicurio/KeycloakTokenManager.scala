package org.scalateams.sbt.apicurio

import org.scalateams.sbt.apicurio.ApicurioModels._
import sttp.client3._
import sttp.client3.circe._

import java.util.concurrent.atomic.AtomicReference
import scala.util.Try

/** Manages OAuth2 access tokens for Keycloak authentication with proactive refresh.
  *
  * Token lifecycle:
  *   - Requests initial token on first use
  *   - Caches token and tracks expiry time
  *   - Proactively refreshes token 30 seconds before expiry
  *   - Thread-safe for concurrent access
  *
  * @param config
  *   Keycloak configuration including URL, realm, client ID and secret
  * @param backend
  *   STTP backend for making HTTP requests
  * @param refreshBufferSeconds
  *   Number of seconds before expiry to trigger refresh (default: 30)
  */
class KeycloakTokenManager(
  config: KeycloakConfig,
  backend: SttpBackend[Identity, Any],
  refreshBufferSeconds: Long = 30) {

  /** Cached token state with expiry tracking */
  private case class TokenState(
    accessToken: String,
    expiresAt: Long // Unix timestamp in seconds
  ) {

    /** Check if token needs refresh based on buffer time */
    def needsRefresh(currentTime: Long): Boolean =
      currentTime >= (expiresAt - refreshBufferSeconds)
  }

  /** Thread-safe mutable state for cached token */
  private val tokenState: AtomicReference[Option[TokenState]] = new AtomicReference(None)

  /** Gets a valid access token, refreshing if necessary.
    *
    * This method is thread-safe and will:
    *   - Return cached token if still valid
    *   - Request new token if no cached token exists
    *   - Proactively refresh token if within refresh buffer window
    *
    * @return
    *   Either an error or a valid access token
    */
  def getValidToken(): ApicurioResult[String] = {
    val currentTime = System.currentTimeMillis() / 1000

    tokenState.get() match {
      case Some(state) if !state.needsRefresh(currentTime) =>
        // Token is still valid
        Right(state.accessToken)

      case _ =>
        // Token is missing, expired, or needs refresh
        refreshToken()
    }
  }

  /** Requests a new access token from Keycloak using client credentials flow.
    *
    * Makes a POST request to the Keycloak token endpoint with:
    *   - grant_type=client_credentials
    *   - client_id and client_secret for authentication
    *
    * @return
    *   Either an error or the new token state
    */
  private def requestToken(): ApicurioResult[TokenState] = {
    val tokenEndpoint = config.tokenEndpoint

    Try {
      val request = basicRequest
        .post(uri"$tokenEndpoint")
        .body(
          Map(
            "grant_type" -> "client_credentials",
            "client_id" -> config.clientId,
            "client_secret" -> config.clientSecret
          )
        )
        .response(asJson[TokenResponse])

      val response = request.send(backend)

      response.body match {
        case Right(tokenResponse) =>
          val currentTime = System.currentTimeMillis() / 1000
          val expiresAt = currentTime + tokenResponse.expires_in

          Right(TokenState(tokenResponse.access_token, expiresAt))

        case Left(error) =>
          val statusCode = response.code.code
          val errorMessage = s"Failed to obtain access token (HTTP $statusCode): ${error.getMessage}"
          Left(ApicurioError.AuthenticationError(errorMessage))
      }
    }.fold(
      ex =>
        Left(
          ApicurioError.AuthenticationError(
            s"Network error while requesting token from ${config.tokenEndpoint}",
            Some(ex)
          )
        ),
      either => either
    )
  }

  /** Refreshes the cached token and updates the atomic reference.
    *
    * This method is synchronized to prevent multiple concurrent refresh attempts. If multiple threads call this
    * simultaneously, only one will perform the refresh while others wait and use the refreshed token.
    *
    * @return
    *   Either an error or the new access token
    */
  private def refreshToken(): ApicurioResult[String] = synchronized {
    // Double-check: another thread might have refreshed while we were waiting
    val currentTime = System.currentTimeMillis() / 1000
    tokenState.get() match {
      case Some(state) if !state.needsRefresh(currentTime) =>
        // Token was refreshed by another thread
        Right(state.accessToken)

      case _ =>
        // Perform refresh
        requestToken().map { newState =>
          tokenState.set(Some(newState))
          newState.accessToken
        }
    }
  }

  /** Clears the cached token state. Useful for testing or forcing re-authentication. */
  def clearCache(): Unit = tokenState.set(None)
}

object KeycloakTokenManager {

  /** Creates a token manager with the default STTP backend.
    *
    * @param config
    *   Keycloak configuration
    * @return
    *   New token manager instance
    */
  def apply(config: KeycloakConfig): KeycloakTokenManager =
    new KeycloakTokenManager(config, HttpURLConnectionBackend())

  /** Creates a token manager with a custom STTP backend (useful for testing).
    *
    * @param config
    *   Keycloak configuration
    * @param backend
    *   Custom STTP backend
    * @return
    *   New token manager instance
    */
  def apply(config: KeycloakConfig, backend: SttpBackend[Identity, Any]): KeycloakTokenManager =
    new KeycloakTokenManager(config, backend)
}
