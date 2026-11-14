package com.example.audio

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class AiAcousticPrescription(
    val environmentType: String,
    val recommendedPreset: String,
    val bandGains: Map<Int, Float>,
    val recommendedAiMode: AiNoiseMode,
    val masterGain: Float,
    val explanation: String
)

interface GeminiRestService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body body: okhttp3.RequestBody
    ): okhttp3.ResponseBody
}

class AiAcousticAdvisor {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(client)
        .build()

    private val service = retrofit.create(GeminiRestService::class.java)

    suspend fun analyzeEnvironment(
        currentDb: Float,
        peakDb: Float,
        connectedDeviceName: String
    ): AiAcousticPrescription = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            // Local algorithmic fallback if API key is not configured yet
            return@withContext localAcousticAnalysis(currentDb, peakDb)
        }

        try {
            val prompt = """
                You are an expert clinical audiologist and DSP acoustic engineer for HearPod hearing aids.
                Current acoustic telemetry:
                - Ambient Sound Level: ${currentDb.toInt()} dB SPL
                - Peak Transient Level: ${peakDb.toInt()} dB SPL
                - Output Device: $connectedDeviceName
                
                Analyze the environment and provide recommended hearing aid DSP settings in raw JSON format only (no markdown, no backticks).
                Format:
                {
                  "environment": "Office / Cafe / Home / Street",
                  "preset": "Speech Clarity" or "Noisy Restaurant" or "TV / Media" or "Outdoor / Wind",
                  "aiMode": "STUDIO_QUIET" or "BALANCED" or "CONVERSATION_FOCUS",
                  "gain250": -4.0,
                  "gain500": 2.0,
                  "gain1000": 6.0,
                  "gain2000": 8.0,
                  "gain4000": 7.0,
                  "gain8000": 2.0,
                  "masterGain": 1.4,
                  "explanation": "Brief explanation for the user."
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                    put("responseMimeType", "application/json")
                })
            }

            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val response = service.generateContent(apiKey, requestBody)
            val responseString = response.string()

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val rawText = parts?.optJSONObject(0)?.optString("text") ?: ""

            parsePrescriptionJson(rawText, currentDb, peakDb)
        } catch (e: Exception) {
            Log.e("AiAcousticAdvisor", "Gemini API error, falling back to local analysis", e)
            localAcousticAnalysis(currentDb, peakDb)
        }
    }

    private fun parsePrescriptionJson(jsonText: String, currentDb: Float, peakDb: Float): AiAcousticPrescription {
        return try {
            val clean = jsonText.replace("```json", "").replace("```", "").trim()
            val obj = JSONObject(clean)
            val env = obj.optString("environment", "Adaptive Environment")
            val preset = obj.optString("preset", "Speech Clarity")
            val modeStr = obj.optString("aiMode", "STUDIO_QUIET")
            val aiMode = try { AiNoiseMode.valueOf(modeStr) } catch (e: Exception) { AiNoiseMode.STUDIO_QUIET }

            val gains = mapOf(
                250 to obj.optDouble("gain250", -4.0).toFloat(),
                500 to obj.optDouble("gain500", 2.0).toFloat(),
                1000 to obj.optDouble("gain1000", 6.0).toFloat(),
                2000 to obj.optDouble("gain2000", 8.0).toFloat(),
                4000 to obj.optDouble("gain4000", 7.0).toFloat(),
                8000 to obj.optDouble("gain8000", 2.0).toFloat()
            )
            val master = obj.optDouble("masterGain", 1.4).toFloat().coerceIn(0.8f, 2.2f)
            val explanation = obj.optString("explanation", "AI tailored noise suppression and speech formant boost applied.")

            AiAcousticPrescription(
                environmentType = env,
                recommendedPreset = preset,
                bandGains = gains,
                recommendedAiMode = aiMode,
                masterGain = master,
                explanation = explanation
            )
        } catch (e: Exception) {
            localAcousticAnalysis(currentDb, peakDb)
        }
    }

    private fun localAcousticAnalysis(currentDb: Float, peakDb: Float): AiAcousticPrescription {
        return when {
            currentDb > 75f -> {
                AiAcousticPrescription(
                    environmentType = "Noisy Dynamic Environment (${currentDb.toInt()} dB)",
                    recommendedPreset = "Noisy Restaurant",
                    bandGains = mapOf(
                        250 to -8f,
                        500 to -2f,
                        1000 to 8f,
                        2000 to 9f,
                        4000 to 5f,
                        8000 to -2f
                    ),
                    recommendedAiMode = AiNoiseMode.STUDIO_QUIET,
                    masterGain = 1.5f,
                    explanation = "Elevated ambient background detected. Maximized AI spectral noise elimination and prioritized consonant speech frequencies."
                )
            }
            currentDb > 58f -> {
                AiAcousticPrescription(
                    environmentType = "Active Speech Environment (${currentDb.toInt()} dB)",
                    recommendedPreset = "Speech Clarity",
                    bandGains = mapOf(
                        250 to -4f,
                        500 to 2f,
                        1000 to 6f,
                        2000 to 8f,
                        4000 to 7f,
                        8000 to 1f
                    ),
                    recommendedAiMode = AiNoiseMode.STUDIO_QUIET,
                    masterGain = 1.4f,
                    explanation = "Optimal conversational range detected. Applied high-clarity speech focus with complete background hiss elimination."
                )
            }
            else -> {
                AiAcousticPrescription(
                    environmentType = "Quiet Indoor Room (${currentDb.toInt()} dB)",
                    recommendedPreset = "Balanced Natural",
                    bandGains = mapOf(
                        250 to 0f,
                        500 to 1f,
                        1000 to 3f,
                        2000 to 4f,
                        4000 to 3f,
                        8000 to 1f
                    ),
                    recommendedAiMode = AiNoiseMode.STUDIO_QUIET,
                    masterGain = 1.2f,
                    explanation = "Quiet room detected. Suppressed microphone pre-amp noise floor and balanced natural vocal frequencies."
                )
            }
        }
    }
}
