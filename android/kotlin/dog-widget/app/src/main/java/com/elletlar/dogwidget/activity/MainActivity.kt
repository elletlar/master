package com.elletlar.dogwidget.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import com.elletlar.dogwidget.Config
import com.elletlar.dogwidget.R
import android.text.InputType
import android.app.AlertDialog
import android.content.SharedPreferences
import android.widget.*
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Main Activity: All preferences and options for configuring the dog widget
 */
class MainActivity : Activity() {

    /** Shared Preferences */
    private lateinit var mPrefs: SharedPreferences

    /** Firebase analytics */
    private lateinit var mFirebaseAnalytics: FirebaseAnalytics

    // --- Lifecycle Methods

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase Test
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(applicationContext)
        setUserProperty("Unicorns", "Love them")
        logEvent("on_create")

        mPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Breed selection
        val breedBtn = findViewById<TextView>(R.id.btn_breeds)
        breedBtn.setOnClickListener {
            val intent = Intent(this@MainActivity, BreedActivity::class.java)
            startActivity(intent)
        }

        // Alpha
        val alphaRlt = findViewById<RelativeLayout>(R.id.lyt_alpha)
        val alpha = mPrefs.getInt(Config.Keys.ALPHA, Config.Keys.DEFAULT_ALPHA)
        val alphaTv = alphaRlt.findViewById<TextView>(R.id.txt_alpha)
        val transparencyInt = toTransparency(alpha)
        alphaTv.text = transparencyInt.toString() + "%"
        alphaRlt.setOnClickListener { openAlphaDialog(transparencyInt) }

        // Rounded
        val roundedCbx: CheckBox = findViewById(R.id.chk_rounded)
        val checked = mPrefs.getBoolean(Config.Keys.ROUNDED, Config.Keys.DEFAULT_ROUNDED)
        roundedCbx.isChecked = checked
        roundedCbx.setOnCheckedChangeListener { _, isChecked ->
            mPrefs.edit().putBoolean(Config.Keys.ROUNDED, isChecked).apply()
        }
    }

    // onStart
    override fun onStart() {
        logEvent("on_start")
        super.onStart()
    }


    // --- Helper Methods

    /**
     * Example of setting a user property on Firebase
     */
    private fun setUserProperty(property : String, value : String) {
        mFirebaseAnalytics.setUserProperty(property, value)
    }

    /**
     * Sample of logging to the Firebase console
     */
    private fun logEvent(value : String) {
        val b = Bundle()
        b.putString("method_name", value)
        mFirebaseAnalytics.logEvent("app_method", b)
    }

    /**
     * Opens dialog that enables user to set the transparency effect on the widgets
     */
    @SuppressLint("SetTextI18n") // Sample project, no need for internationalisation
    private fun openAlphaDialog(alpha : Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Transparency")

        // Set up the input
        val input = EditText(this)

        // Specify the type of input expected this, for example, sets the input as a password, and will mask the text
        input.inputType = InputType.TYPE_CLASS_NUMBER
        builder.setView(input)
        input.setText(alpha.toString())

        // Set up the buttons
        builder.setPositiveButton("OK") { _, _ ->
            val num = input.text.toString()
            val numInt = num.toIntOrNull() ?: return@setPositiveButton

            if (numInt in 0..100) {
                val theAlpha = toAlpha(numInt)
                mPrefs.edit().putInt(Config.Keys.ALPHA, theAlpha).apply()
            }
            val alphaTv = findViewById<TextView>(R.id.txt_alpha)
            alphaTv.text = numInt.toString() + "%"
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        builder.setMessage("Amount of transparency 1 to 100")

        builder.show()
    }

    /**
     * What the API uses:
     *
     * 0 (transparent) to 255 (opaque)
     */
    private fun toAlpha(percent : Int) : Int {
        val percentOpaque = 100 - percent

        val temp = (percentOpaque / 100f) * 255
        return temp.toInt()
    }

    /**
     * Easier for humans to understand
     *
     * 0% solid, 100%: invisible
     */
    private fun toTransparency(value : Int) : Int {
        val percentOpaque = value / 255f
        val percentOpaqueInt = percentOpaque * 100
        val percentTransparent = 100 - percentOpaqueInt
        return percentTransparent.toInt()
    }
}