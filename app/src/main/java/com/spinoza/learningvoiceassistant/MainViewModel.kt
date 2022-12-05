package com.spinoza.learningvoiceassistant

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wolfram.alpha.WAEngine
import com.wolfram.alpha.WAPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val KEY_FIELD_NAME = "Title"
        const val VALUE_FIELD_NAME = "Content"
    }

    private val waEngine: WAEngine = WAEngine().apply {
        appID = WolframApi.KEY
        addFormat("plaintext")
    }

    private val pods = mutableListOf<HashMap<String, String>>()
    private val dataSetChanged = MutableLiveData<Boolean>()
    private val progressBarVisibility = MutableLiveData<Boolean>()
    private val errorMessage = MutableLiveData<String?>()
    private val errorInputRequest = MutableLiveData<String>()
    private val errorTts = MutableLiveData<String>()

    private var textToSpeech: TextToSpeech
    private var isTtsReady = false

    fun getPods(): MutableList<HashMap<String, String>> = pods
    fun isDataSetChanged(): LiveData<Boolean> = dataSetChanged
    fun isProgressBar(): LiveData<Boolean> = progressBarVisibility
    fun getErrorMessage(): LiveData<String?> = errorMessage
    fun getErrorInputRequest(): LiveData<String> = errorInputRequest
    fun getErrorTts(): LiveData<String> = errorTts

    init {
        textToSpeech = TextToSpeech(application.applicationContext) { code ->
            isTtsReady = code == TextToSpeech.SUCCESS
            if (!isTtsReady) {
                errorTts.value = ""
            }
        }
        textToSpeech.language = Locale.US
    }

    fun clearPods() {
        pods.clear()
        dataSetChanged.value = true
    }

    fun askWolfram(request: String) {
        clearPods()
        progressBarVisibility.value = true
        CoroutineScope(Dispatchers.IO).launch {
            val query = waEngine.createQuery().apply { input = request }
            kotlin.runCatching {
                waEngine.performQuery(query)
            }.onSuccess { result ->
                withContext(Dispatchers.Main) {
                    progressBarVisibility.value = false
                    if (result.isError) {
                        errorMessage.value = result.errorMessage
                    } else if (!result.isSuccess) {
                        errorInputRequest.value = ""
                    } else {
                        for (pod in result.pods) {
                            if (pod.isError) continue
                            val content = StringBuilder()
                            for (subpod in pod.subpods) {
                                for (element in subpod.contents) {
                                    if (element is WAPlainText) {
                                        content.append(element.text)
                                    }
                                }
                            }
                            pods.add(0, HashMap<String, String>().apply {
                                put(KEY_FIELD_NAME, pod.title)
                                put(VALUE_FIELD_NAME, content.toString())
                            })
                        }
                        dataSetChanged.value = true
                    }
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    progressBarVisibility.value = false
                    errorMessage.value = t.message
                }
            }
        }
    }

    fun speak(position: Int) {
        if (isTtsReady) {
            val title = pods[position][KEY_FIELD_NAME]
            val content = pods[position][VALUE_FIELD_NAME]
            textToSpeech.speak(content, TextToSpeech.QUEUE_FLUSH, null, title)
        }
    }

    fun stopSpeaking() {
        if (isTtsReady) {
            textToSpeech.stop()
        }
    }

}

