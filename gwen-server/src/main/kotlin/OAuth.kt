package com.badlogicgames.gwen;

import com.esotericsoftware.minlog.Log
import com.esotericsoftware.minlog.Log.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class OAuthConfig(val endPoint: String,
							  val scope: String,
							  val codeRedirectUri: String,
							  val userRedirectUri: String);

interface OAuthClient {
	@retrofit2.http.FormUrlEncoded
	@retrofit2.http.POST("token")
	fun getAccessToken(@retrofit2.http.Field("code") code: String,
							 @retrofit2.http.Field("client_id") clientId: String,
							 @retrofit2.http.Field("client_secret") clientSecret: String,
							 @retrofit2.http.Field("redirect_uri") redirectUri: String,
							 @retrofit2.http.Field("grant_type") grantType: String): retrofit2.Call<OAuthCredentials>

	@retrofit2.http.FormUrlEncoded
	@retrofit2.http.POST("token")
	fun refreshAccessToken(@retrofit2.http.Field("refresh_token") refreshToken: String,
								  @retrofit2.http.Field("client_id") clientId: String,
								  @retrofit2.http.Field("client_secret") clientSecret: String,
								  @retrofit2.http.Field("grant_type") grantType: String): retrofit2.Call<OAuthCredentials>
}

class OAuth {
	private val gwenConfig: GwenConfig;
	private val config: OAuthConfig;
	private val client: OAuthClient;
	private val REFRESH_TIME = 300000;

	constructor(gwenConfig: GwenConfig, config: OAuthConfig) {
		this.gwenConfig = gwenConfig;
		this.config = config;
		val client = Retrofit.Builder().baseUrl(config.endPoint)?.addConverterFactory(GsonConverterFactory.create())?.build()?.create(OAuthClient::class.java);
		if (client == null) {
			error("Couldn't create OAuth client");
			throw Exception("Couldn't create OAuth client");
		}
		this.client = client;
	}

	fun getCredentials(): OAuthCredentials {
		val creds = gwenConfig.credentials ?: throw Exception("Not authorized");
		if (creds.expirationTime - System.currentTimeMillis() < REFRESH_TIME) {
			refreshAccessToken()
		}
		return creds;
	}

	fun isAuthorized(): Boolean = gwenConfig.credentials != null;

	fun getAuthorizationURL(): String = "${config.userRedirectUri}?scope=${config.scope}&response_type=code&redirect_uri=${config.codeRedirectUri}&client_id=${gwenConfig.assistantConfig?.clientId}";

	fun requestAccessToken(code: String) {
		trace("Requesting access token");
		val assistantConfig = gwenConfig.assistantConfig ?: throw Exception("Google Assistant project not configured");
		val response = client.getAccessToken(code, assistantConfig.clientId, assistantConfig.clientSecret, config.codeRedirectUri, "authorization_code").execute();

		if (response.isSuccessful) {
			gwenConfig.credentials = response.body();
			gwenConfig.save();
			trace("Got access token");
		} else {
			error("Couldn't get access token, ${response.errorBody().string()}");
			throw Exception("Couldn't request token");
		}
	}

	private fun refreshAccessToken() {
		trace("Refreshing OAuth access token");
		val assistantConfig = gwenConfig.assistantConfig ?: throw Exception("Google Assistant project no configured");
		var credentials = gwenConfig.credentials ?: throw Exception("Not authorized");
		val response = client.refreshAccessToken(credentials.refreshToken, assistantConfig.clientId, assistantConfig.clientSecret, "refresh_token").execute();
		if (response.isSuccessful) {
			credentials = response.body()
			val originalCredentials = gwenConfig.credentials;
			if (originalCredentials != null) {
				originalCredentials.accessToken = credentials.accessToken;
				originalCredentials.expiresIn = credentials.expiresIn;
				originalCredentials.tokenType = credentials.tokenType;
				gwenConfig.save();
			}
			Log.info("Refreshed token");
		} else {
			gwenConfig.credentials = null;
			error("Couldn't refresh token, ${response.errorBody().string()}");
			throw Exception("Couldn't refresh token ${response.errorBody().string()}")
		}
	}
}

fun loadOAuth(config: GwenConfig): OAuth {
	val oAuthConfig = OAuthConfig(
			  "https://www.googleapis.com/oauth2/v4/",
			  "https://www.googleapis.com/auth/assistant-sdk-prototype",
			  "urn:ietf:wg:oauth:2.0:oob",
			  "https://accounts.google.com/o/oauth2/v2/auth"
	);
	val oauth = OAuth(config, oAuthConfig);
	if (oauth.isAuthorized()) {
		try {
			oauth.getCredentials();
		} catch (t: Throwable) {
			Log.error("Couldn't authorize", t);
		}
	}
	return oauth;
}
