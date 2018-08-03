package com.elletlar.dogwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.preference.PreferenceManager
import android.widget.RemoteViews
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.AppWidgetTarget
import com.bumptech.glide.request.transition.Transition
import com.elletlar.dogwidget.service.DogService
import android.app.PendingIntent
import android.content.ComponentName
import android.net.Uri
import android.util.Log
import com.elletlar.dogwidget.Config
import com.elletlar.dogwidget.R

/**
 * Dog Widget
 *
 * Features:
 * ---------
 * . Displays images of dogs from the dog.ceo servers on the user's home screen
 * . Each widget is capable of displaying a different dog
 * . With multiple widgets, tne images rotate in carousel fashion to avoid "loosing" a favourite image
 *   when an update happens
 * . Images can be rounded or square
 * . Images can be translucency can be set
 * . Clicking on an image opens a full sized image of the dog in a web browser
 */
class DogWidget : AppWidgetProvider() {
    // Globals
    companion object {
        /** Widget is being updated because a new photo is ready */
        const val EXTRA_PHOTO_READY = "com.elletlar.dog.extra_photo_ready"

        /** Image to show if no URL is available */
        const val DEFAULT_IMAGE_URL = "https://images.dog.ceo/breeds/bulldog-french/n02108915_9457.jpg"

        /** Show new dog when widget enabled but do not flicker old dog */
        var mRequestInProgress = true

        /** Log Tag */
        private const val TAG = "DogWidget"

        /** User clicked on widget dog image */
        private const val ACTION_DOG_CLICK = "com.elletlar.dog.dog_click"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null)
            return
        val action = intent.action

        when(action) {
            // Widget installed
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                requestNewImage(context)
            }
            // User removed lock screen
            Intent.ACTION_USER_PRESENT -> { // Update widget whenever use releases screen lock
                requestNewImage(context) // Ask server to get new image
            }
            // Server
            Config.Widget.ACTION_NEW_IMAGE_READY -> {
                mRequestInProgress = false
                updateSelf(context) // Update the widget
            }
            // The widget received a click
            ACTION_DOG_CLICK ->  {
                val url = intent.getStringExtra("url")
                handleDogClick(context, url)
            }
            else -> {
                Log.d(TAG, "OnReceive: No special handling for action: $action")
            }
        }

        // onUpdate is on the same call stack as onReceive
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        var i = 0
        appWidgetIds.forEach {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget)

            val glideUpdateArray = IntArray(1)
            glideUpdateArray[0] = appWidgetIds[i]
            if (!mRequestInProgress) {
                updateImageView(context, remoteViews, glideUpdateArray, i)
            }

            val alpha = prefs.getInt(Config.Keys.ALPHA, Config.Keys.DEFAULT_ALPHA)
            remoteViews.setInt(R.id.img_dog, "setImageAlpha", alpha)

            appWidgetManager?.updateAppWidget(i, remoteViews)
            ++i
        }
    }

    private fun updateImageView(context : Context, remoteViews : RemoteViews, appWidgetIds: IntArray,
                                index : Int) {
        // Get current image from preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        var imageUrl = ""
        if (prefs.contains(Config.Keys.CURRENT_IMAGE_URLS)) {
            val urlList = Config.Keys.getList(context, Config.Keys.CURRENT_IMAGE_URLS)
            imageUrl = getCurrentImage(urlList, index)
        }
        // Create target for the image
        val awt: AppWidgetTarget = object : AppWidgetTarget(context.applicationContext, R.id.img_dog, remoteViews, *appWidgetIds) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                Log.i(TAG, "Image Ready")
                super.onResourceReady(resource, transition)
            }
        }
        var options = RequestOptions().
                override(300, 300).placeholder(R.drawable.default_dog).error(R.drawable.default_dog)

        // User rounded option
        if (prefs.getBoolean(Config.Keys.ROUNDED, Config.Keys.DEFAULT_ROUNDED)) {
            options = options.circleCrop()
        }

        Glide.with(context).asBitmap().load(imageUrl).apply(options).into(awt)

        val pendingIntentDogClick = getDogClickPendingIntent(context, index, imageUrl)
        remoteViews.setOnClickPendingIntent(R.id.lyt, pendingIntentDogClick)
    }

    private fun getDogClickPendingIntent(context: Context, requestCode : Int, imageUrl: String): PendingIntent {
        val intent = Intent(context, javaClass)
        intent.action = ACTION_DOG_CLICK
        intent.putExtra("url", imageUrl)
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * Handle click on dog image
     */
    private fun handleDogClick(context  : Context, constUrl : String) {
        var url = constUrl
        if (url.isEmpty()) {
            url = DEFAULT_IMAGE_URL
        }


        val i = Intent(Intent.ACTION_VIEW)
        i.data = Uri.parse(url)
        context.startActivity(i)
    }

    /**
     * Force onUpdate to be called
     */
    private fun updateSelf(context: Context) {
        val appContext = context.applicationContext

        val intent = Intent(context.applicationContext, DogWidget::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val ids = AppWidgetManager.getInstance(appContext).getAppWidgetIds((ComponentName(appContext, DogWidget::class.java)))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)

        context.sendBroadcast(intent)
    }

    /**
     * Ask the service to download a new image
     *
     * [Note: Service will update the widget when the image is ready]
     */
    private fun requestNewImage(context : Context) {
        mRequestInProgress = true
        val intent = Intent(context, DogService::class.java)
        intent.action = DogService.ACTION_UPDATE_PHOTO
        context.startService(intent)
    }

    /**
     * In the unlikely case that the user has more than MAX_DEFAULT_IMAGES:12 widgets,
     * we recycle the available ones
     */
    private fun getCurrentImage(list : List<String>, index : Int) : String {
        val i: Int = index % Config.Urls.MAX_DEFAULT_IMAGES

        if (i < list.size) {
            return list[i]
        }
        return ""
    }
}
