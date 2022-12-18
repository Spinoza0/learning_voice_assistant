package com.spinoza.learningvoiceassistant

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.SimpleAdapter
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.spinoza.learningvoiceassistant.databinding.ActivityMainBinding
import com.spinoza.learningvoiceassistant.databinding.ItemPodBinding
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bindingItem: ItemPodBinding
    private lateinit var mainViewModel: MainViewModel
    private lateinit var podsAdapter: SimpleAdapter
    private lateinit var viewSnackBar: View

    private var getFromVoiceInputDialog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        { result -> onActivityResult(result) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        bindingItem = ItemPodBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        initViews()
        setObserves()
    }

    private fun initViews() {
        setSupportActionBar(binding.materialToolbar)
        viewSnackBar = findViewById(android.R.id.content)

        binding.textInputRequest.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mainViewModel.clearPods()
                mainViewModel.askWolfram(binding.textInputRequest.text.toString())
            }

            return@setOnEditorActionListener false
        }

        podsAdapter = SimpleAdapter(
            applicationContext,
            mainViewModel.getPods(),
            R.layout.item_pod,
            arrayOf(MainViewModel.KEY_FIELD_NAME, MainViewModel.VALUE_FIELD_NAME),
            intArrayOf(bindingItem.textViewTitle.id, bindingItem.textViewContent.id)
        )
        binding.listViewPods.adapter = podsAdapter
        binding.listViewPods.setOnItemClickListener { _, _, position, _ ->
            mainViewModel.speak(position)
        }

        binding.voiceInputButton.setOnClickListener {
            mainViewModel.clearPods()
            mainViewModel.stopSpeaking()
            showVoiceInputDialog()
        }

    }

    private fun setObserves() {
        mainViewModel.isProgressBar().observe(this) { isProgressBar ->
            binding.progressBar.visibility = if (isProgressBar) View.VISIBLE else View.GONE
        }

        mainViewModel.getErrorMessage().observe(this) { message ->
            val text = message ?: getString(R.string.error_something_went_wrong)
            showSnackBar(text)
        }

        mainViewModel.getErrorInputRequest().observe(this) {
            binding.textInputRequest.error = getString(R.string.error_do_not_understand)
        }

        mainViewModel.getErrorTts().observe(this) {
            showSnackBar(getString(R.string.error_tts_is_not_ready))
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
                binding.textInputRequest.text?.clear()
                mainViewModel.clearPods()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    private fun showSnackBar(message: String) {
        Snackbar.make(viewSnackBar, message, Snackbar.LENGTH_INDEFINITE).apply {
            setAction(android.R.string.ok) { dismiss() }
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
        runCatching { getFromVoiceInputDialog.launch(intent) }
            .onFailure { t ->
                showSnackBar(t.message ?: getString(R.string.error_voice_recognition_unavailable))
            }
    }


    private fun onActivityResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.get(0)?.let { question ->
                    binding.textInputRequest.setText(question)
                    mainViewModel.askWolfram(question)
                }
        }
    }
}