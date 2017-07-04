package com.badlogicgames.gwen

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.*
import java.net.URI

data class OAuthConfig(val endPoint: String,
                       val clientId: String,
                       val clientSecret: String,
                       val credentialsFile: String,
                       val scope: String,
                       val codeRedirectUri: String,
                       val userRedirectUri: String);

data class OAuthCredentials(@SerializedName("access_token") var accessToken: String,
                            @SerializedName("expires_in") var expiresIn: Int,
                            @SerializedName("token_type") var tokenType: String,
                            @SerializedName("refresh_token") var refreshToken: String,
                            @SerializedName("id_token") var idToken: String,
                            @SerializedName("expiration_time") var expirationTime: Long);

interface OAuthClient {
    @FormUrlEncoded
    @POST("token")
    fun getAccessToken(@Field("code") code: String,
                       @Field("client_id") clientId: String,
                       @Field("client_secret") clientSecret: String,
                       @Field("redirect_uri") redirectUri: String,
                       @Field("grant_type") grantType: String): Call<OAuthCredentials>

    @FormUrlEncoded
    @POST("token")
    fun refreshAccessToken(@Field("refresh_token") refreshToken: String,
                           @Field("client_id") clientId: String,
                           @Field("client_secret") clientSecret: String,
                           @Field("grant_type") grantType: String): Call<OAuthCredentials>
}

class OAuth {
    val config: OAuthConfig;
    private val client: OAuthClient;
    private val gson: Gson;
    private var credentials: OAuthCredentials? = null;
    private val REFRESH_TIME = 300000;

    constructor(config: OAuthConfig) {
        this.config = config;
        val oAuthClient = Retrofit.Builder().baseUrl(config.endPoint)?.addConverterFactory(GsonConverterFactory.create())?.build()?.create(OAuthClient::class.java);
        if (oAuthClient == null) {
            throw Exception("Couldn't create OAuth client");
        }
        this.client = oAuthClient;
        this.gson = Gson();

        val credsFile = File(config.credentialsFile);
        if (credsFile.exists()) {
            credentials = gson.fromJson(JsonReader(FileReader(credsFile)), OAuthCredentials::class.java);
        }
    }

    fun getCredentials(): OAuthCredentials {
        val creds = credentials;
        if (creds == null) throw Error("Not authorized");

        if (creds.expirationTime - System.currentTimeMillis() < REFRESH_TIME) {
            refreshAccessToken()
        }

        return creds;
    }

    fun isAuthorized(): Boolean = credentials != null;

    fun getAuthorizationURL(): String = "${config.userRedirectUri}?scope=${config.scope}&response_type=code&redirect_uri=${config.codeRedirectUri}&client_id=${config.clientId}";

    fun commandLineRequestFlow() {
        val url = getAuthorizationURL();
        if (!GraphicsEnvironment.isHeadless()) Desktop.getDesktop().browse(URI(url));
        val reader = BufferedReader(InputStreamReader(System.`in`))
        println("Open ${url}");
        println("Paste code here: ");
        requestAccessToken(reader.readLine());
    }

    fun requestAccessToken(code: String): OAuthCredentials? {
        credentials == null;
        val response = client.getAccessToken(code, config.clientId, config.clientSecret, config.codeRedirectUri, "authorization_code").execute();

        if (response.isSuccessful) {
            val credentials = response.body();
            saveCredentials(credentials);
            this.credentials = credentials;
        } else {
            throw Error("Couldn't request token");
        }
        return credentials;
    }

    private fun refreshAccessToken() {
        val creds = credentials;
        if (creds == null) throw Error("Not authorized");
        val response = client.refreshAccessToken(creds.refreshToken, config.clientId, config.clientSecret, "refresh_token").execute();
        if (response.isSuccessful) {
            val credentials = response.body()
            val originalCredentials = this.credentials;
            if (originalCredentials != null) {
                originalCredentials.accessToken = credentials.accessToken;
                originalCredentials.expiresIn = credentials.expiresIn;
                originalCredentials.tokenType = credentials.tokenType;
                saveCredentials(originalCredentials);
            }
            this.credentials = originalCredentials;
        } else {
            credentials = null;
            throw Error("Couldn't refresh token ${response.errorBody().string()}")
        }
    }

    private fun saveCredentials(credentials: OAuthCredentials) {
        FileWriter(config.credentialsFile).use {
            credentials.expirationTime = System.currentTimeMillis() + credentials.expiresIn * 1000;
            gson.toJson(credentials, it);
        }
    }
}
