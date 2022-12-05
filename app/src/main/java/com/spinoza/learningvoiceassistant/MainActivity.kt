package com.spinoza.learningvoiceassistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mainViewModel: MainViewModel

    private lateinit var progressBar: ProgressBar
    private lateinit var textInputRequest: TextInputEditText
    private lateinit var podsAdapter: SimpleAdapter

    private var getFromVoiceInputDialog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result ->
            onActivityResult(result)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        initViews()
        seObserves()
    }

    private fun initViews() {
        val materialToolbar: MaterialToolbar = findViewById(R.id.materialToolbar)
        setSupportActionBar(materialToolbar)

        progressBar = findViewById(R.id.progressBar)

        textInputRequest = findViewById(R.id.textInputRequest)
        textInputRequest.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val question = textInputRequest.text.toString()
                mainViewModel.askWolfram(question)
            }

            return@setOnEditorActionListener false
        }

        val listViewPods: ListView = findViewById(R.id.listViewPods)
        podsAdapter = SimpleAdapter(
            applicationContext,
            mainViewModel.getPods(),
            R.layout.item_pod,
            arrayOf(MainViewModel.KEY_FIELD_NAME, MainViewModel.VALUE_FIELD_NAME),
            intArrayOf(R.id.textViewTitle, R.id.textViewContent)
        )
        listViewPods.adapter = podsAdapter
        listViewPods.setOnItemClickListener { _, _, position, _ ->
            mainViewModel.speak(position)
        }

        val voiceInputButton: FloatingActionButton = findViewById(
            R.id.voiceInputButton
        )
        voiceInputButton.setOnClickListener {
            clearPods()
            mainViewModel.stopSpeaking()
            showVoiceInputDialog()
        }

    }

    private fun seObserves() {
        mainViewModel.isProgressBar().observe(this) { isProgressBar ->
            if (isProgressBar)
                progressBar.visibility = View.VISIBLE
            else
                progressBar.visibility = View.GONE
        }

        mainViewModel.getErrorMessage().observe(this) { message ->
            val text = message ?: getString(R.string.error_something_went_wrong)
            showSnackbar(text)
        }

        mainViewModel.getErrorInputRequest().observe(this) {
            textInputRequest.error = getString(R.string.error_do_not_understand)
        }

        mainViewModel.getErrorTts().observe(this) {
            showSnackbar(getString(R.string.error_tts_is_not_ready))
        }

        mainViewModel.isDataSetChanged().observe(this) { isDataSetChanged ->
            if (isDataSetChanged) {
                podsAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stop -> {
                mainViewModel.stopSpeaking()
                return true
            }
            R.id.action_clear -> {
                clearPods()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message, Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction(android.R.string.ok) {
                dismiss()
            }
            show()
        }
    }

    private fun showVoiceInputDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.request_hint))
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
        }
        runCatching {
            getFromVoiceInputDialog.launch(intent)

        }.onFailure { t ->
            showSnackbar(
                t.message ?: getString(R.string.error_voice_recognition_unavailable)
            )
        }
    }


    private fun onActivityResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.get(0)?.let { question ->
                    Log.d("spinozatest", question)
                    mainViewModel.askWolfram(question)
                    textInputRequest.setText(question)
                    Log.d("spinozatest", textInputRequest.text.toString())

                }
        }
    }

    fun clearPods() {
        textInputRequest.text?.clear()
        mainViewModel.clearPods()
    }
}