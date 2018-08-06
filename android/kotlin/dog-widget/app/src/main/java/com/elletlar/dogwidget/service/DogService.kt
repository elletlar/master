package com.elletlar.dogwidget.service

import android.app.IntentService
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.preference.PreferenceManager
import android.util.Log
import com.github.kittinunf.fuel.Fuel
import com.elletlar.dogwidget.Config
import com.elletlar.dogwidget.widget.DogWidget
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context


/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * helper methods.
 */
class DogService : IntentService("DogService") {

    companion object {
        /** Log Tag */
        private const val TAG = "DogService"

        /** 24 Hours in milliseconds */
        private const val HOURS24 = 86400000


        // --- Actions
        /** Update the breeds and sub breeds from the server */
        const val ACTION_UPDATE_BREEDS = "com.elletlar.dog.update_breeds"
        /** Update the current photo */
        const val ACTION_UPDATE_PHOTO = "com.elletlar.dog.update_photo"

        // --- URLs
        /** URLs */
        const val URL_BREEDS_LIST_ALL = "https://dog.ceo/api/breeds/list/all"

        // --- JSON parsing
        /** JSON names */
        const val JSON_MESSAGE = "message"
    }

    // Handle actions
    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_UPDATE_BREEDS -> {
                handleUpdateBreeds()
            }
            ACTION_UPDATE_PHOTO -> {
                handleUpdateImage()
                // cancelAlarm(applicationContext)
                setAlarm(applicationContext)
            }
        }
    }

    // --- Handlers

    /**
     * Update breeds and sub breeds from the server
     *
     * Store breeds and sub breeds
     */
    private fun handleUpdateBreeds() {
        if (!isUpdate()) {
            Log.d(TAG, "Not time to update")
            return
        }

        // Update from server
        Fuel.get(URL_BREEDS_LIST_ALL).responseString { _, _, result ->
            result.fold({ d ->
                // Logging
                Log.d("", "Success: $d")
                println("Success: $d")

                val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                // We are initialising the DB
                val isInit = !prefs.contains(Config.Keys.ALL_BREEDS)

                // Obtain the breeds
                val jsonObj = JSONObject(d)
                val breedsJsonObject = jsonObj.getJSONObject(JSON_MESSAGE)
                val breedKeys = breedsJsonObject.keys()
                val breedStringSet = LinkedHashSet<String>() // SharedPreferences only stores Set<String>

                val editor = prefs.edit()

                // Iterate through the breeds
                for (breed in breedKeys) {
                    breedStringSet.add(breed)
                    val subBreeds = breedsJsonObject.getJSONArray(breed)
                    // Note: Many of the breeds have no subBreed
                    if (subBreeds.length() > 0) {
                        val subBreedsStringSet = toStringSet(subBreeds)
                        // Store sub-breeds, for example: "all-labrador"
                        editor.putStringSet(Config.Keys.getSubBreedKey(breed), subBreedsStringSet)
                        if (isInit) { // Select all sub-breeds by default
                            editor.putStringSet(Config.Keys.getSubBreedSelectedKey(breed), subBreedsStringSet)
                        }
                    }
                }

                // Store Breeds
                editor.putStringSet(Config.Keys.ALL_BREEDS, breedStringSet)
                if (isInit) { // Select all breeds by default
                    editor.putStringSet(Config.Keys.ALL_BREEDS_SELECTED, breedStringSet)
                }

                editor.putLong(Config.Keys.BREED_UPDATE_TIME, System.currentTimeMillis())

                // Only call apply once to prevent multiple preference writes
                editor.apply()

                handleUpdateImage()
            }, { err ->
                Log.d("", "Cannot update breeds: " + err.response)
                println("Cannot update breeds: " + err.response)

                // TD: Send failure flag back to widget
                handleUpdateImage()
            })
        }
    }


    /**
     *
     * Random image for subbreed:
     * https://dog.ceo/api/breed/hound/afghan/images/random
     */
    private fun handleUpdateImage() {
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val imageRequestUrl = createImageRequestUrl()
        updateImageUrl(imageRequestUrl)
    }

    private fun createImageRequestUrl() : String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        var imageUrl = ""

        // Find random breed from user selections
        val selectedBreeds = prefs.getStringSet(Config.Keys.ALL_BREEDS_SELECTED,
                LinkedHashSet<String>())
        if (selectedBreeds.size > 0) {
            Log.i(TAG, "Taking random: 1 to " + selectedBreeds.size)
            println("Taking random: 1 to " + selectedBreeds.size)
            val size = selectedBreeds.size

            // Random BREED
            var randomBreedIndex = 0
            if (size > 1) {
                randomBreedIndex = (1..size).random()
            }
            val selectedBreed = selectedBreeds.elementAt(randomBreedIndex)

            // Find random sub breed from user selections [If breeds has sub breed data]
            val selectedSubBreeds = prefs.getStringSet(Config.Keys.getSubBreedSelectedKey(selectedBreed),
                    LinkedHashSet<String>())
            var selectedSubBreed = ""
            if (selectedSubBreeds.size > 0) {
                Log.d("", "size is: " + selectedSubBreeds.size)
                println("size is: " + selectedSubBreeds.size)

                // Random SUB-BREED
                var randomSubBreedIndex = 0
                if (selectedSubBreeds.size > 1) {
                    randomSubBreedIndex = (1..selectedSubBreeds.size).random()
                }

                selectedSubBreed = selectedSubBreeds.elementAt(randomSubBreedIndex)
            }

            // Form and store image URL
            imageUrl = "https://dog.ceo/api/breed/"
            imageUrl += "$selectedBreed/"
            if (selectedSubBreed.isNotEmpty()) {
                imageUrl += "$selectedSubBreed/"
            }
            imageUrl += "images/random"
        } else {
            // Choose a random image if none are selected
            imageUrl += "https://dog.ceo/api/breeds/image/random"
        }
        return imageUrl
    }

    private fun updateImageUrl(imageRequestUrl : String) {
        Fuel.get(imageRequestUrl).responseString { _, _, result ->
            result.fold({ d ->
                // Logging
                Log.d("", "success: $d")
                println("Success: $d")

                // Obtain the breeds
                val jsonObj = JSONObject(d)
                var imageUrl = jsonObj.getString(JSON_MESSAGE)

                // Model: Update images in SharedPrefences
                imageUrl = imageUrl.trim()
                putImageUrl(imageUrl)

                // View: Tell the widget it should update
                updateWidget()
            }, { err ->
                Log.d("", "Cannot update image URL: " + err.response)
                println("Cannot update image URL: " + err.response)
            })
        }
    }

    /**
     * Puts the image URL into a JSON array in shared preferences
     */
    private fun putImageUrl(url: String) {
        // Load from JSON list in shared preferences
        val urlList = Config.Keys.getList(applicationContext, Config.Keys.CURRENT_IMAGE_URLS)
        val urlArrayList : ArrayList<String>

        urlArrayList = urlList as ArrayList<String>


        if (urlArrayList.size > Config.Urls.MAX_DEFAULT_IMAGES) {
            urlArrayList.removeAt(0)
        }
        urlArrayList.add(url)

        // Saves as JSON list in shared preferences
        Config.Keys.putList(applicationContext, Config.Keys.CURRENT_IMAGE_URLS, urlArrayList)
    }

    private fun ClosedRange<Int>.random() =
            ThreadLocalRandom.current().nextInt(endInclusive - start) + start

    // --- Helpers

    /**
     * Update the widget, forces all instances of the widget to update
     *
     * Set EXTRA_PHOTO_READY, the reasaon for the update
     */
    private fun updateWidget() {
        val intent = Intent(this, DogWidget::class.java)
        // intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        intent.action = Config.Widget.ACTION_NEW_IMAGE_READY

        intent.putExtra(DogWidget.EXTRA_PHOTO_READY, true)
        val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(applicationContext, DogWidget::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        sendBroadcast(intent)
    }

    /**
     * Time to update breeds from server?
     */
    private fun isUpdate() : Boolean {
        // Update shared preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val lastUpdated = prefs.getLong(Config.Keys.BREED_UPDATE_TIME, 0)

        val updateTime = lastUpdated + HOURS24

        if (System.currentTimeMillis() > updateTime) {
            return true
        }
        return false
    }

    /**
     * Convert JSONArray to Set<String>
     */
    private fun toStringSet(arr : JSONArray) : Set<String> {
        val set: LinkedHashSet<String> = LinkedHashSet()
        for (i in 0 until arr.length()) {
            set.add(arr.getString(i))
        }
        return set
    }

    /**
     * Gets the pending intent for alarm that periodically updates the widget
     */
    private fun getAlarmPendingIntent(context : Context) : PendingIntent {
        val i = Intent(context, DogService::class.java)
        i.action = ACTION_UPDATE_PHOTO
        return PendingIntent.getService(context, 0, i, 0)
    }

    /**
     * Sets an alarm for periodically updating the widget
     */
    private fun setAlarm(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = getAlarmPendingIntent(context)
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60000, (1000 * 60).toLong(), pi) // Millisec * Second * Minute
    }
}