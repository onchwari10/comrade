package com.example.test

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.net.Proxy
import java.text.SimpleDateFormat
import java.util.*

data class AccessTokenResponse(
    val access_token: String,
    val expires_in: String
)

data class STKPushRequest(
    val BusinessShortCode: String,
    val Password: String,
    val Timestamp: String,
    val TransactionType: String,
    val Amount: Int,
    val PartyA: String,
    val PartyB: String,
    val PhoneNumber: String,
    val CallBackURL: String,
    val AccountReference: String,
    val TransactionDesc: String
)

data class STKPushResponse(
    val MerchantRequestID: String,
    val CheckoutRequestID: String,
    val ResponseCode: String,
    val ResponseDescription: String,
    val CustomerMessage: String
)

data class B2CRequest(
    val InitiatorName: String,
    val SecurityCredential: String,
    val CommandID: String,
    val Amount: Int,
    val PartyA: String,
    val PartyB: String,
    val Remarks: String,
    val QueueTimeOutURL: String,
    val ResultURL: String,
    val Occasion: String
)

data class B2CResponse(
    val ConversationID: String,
    val OriginatorConversationID: String,
    val ResponseCode: String,
    val ResponseDescription: String
)

interface MpesaApi {
    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(@Header("Authorization") auth: String): AccessTokenResponse

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun sendSTKPush(@Header("Authorization") auth: String, @Body request: STKPushRequest): STKPushResponse

    @POST("mpesa/b2c/v1/paymentrequest")
    suspend fun sendB2CRequest(@Header("Authorization") auth: String, @Body request: B2CRequest): B2CResponse
}

object MpesaClient {
    private const val BASE_URL = "https://sandbox.safaricom.co.ke/"
    private const val CONSUMER_KEY = "BBdIln0lg8RuhzA4poCCTEJFfvklypcT4cUEe5ivI0UWPQwn"
    private const val CONSUMER_SECRET = "95jdTlfZIXyfjwMFvNnZ8HSc1yTtfRPbBgYLvZX0gT3beRkzeGK3YvAUo3iF2C5w"
    
    // Sandbox default values
    const val BUSINESS_SHORT_CODE = "174379"
    const val PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919"
    const val CALLBACK_URL = "https://mydomain.com/path" 
    
    // B2C Sandbox defaults
    const val INITIATOR_NAME = "testapi"
    const val SECURITY_CREDENTIAL = "S9f3FvO+vV2..." // This should be encrypted in a real app
    const val B2C_SHORT_CODE = "600000"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    // Feature: Explicitly bypass system proxies to avoid redirection errors like 172.22.2.38
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    val api: MpesaApi = retrofit.create(MpesaApi::class.java)

    fun getAuthHeader(): String {
        val keys = "$CONSUMER_KEY:$CONSUMER_SECRET"
        return "Basic " + Base64.encodeToString(keys.toByteArray(), Base64.NO_WRAP)
    }

    fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
    }

    fun getPassword(shortCode: String, passkey: String, timestamp: String): String {
        val str = shortCode + passkey + timestamp
        return Base64.encodeToString(str.toByteArray(), Base64.NO_WRAP)
    }
}
