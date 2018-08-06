package com.elletlar.dogwidget

import android.content.Context
import android.preference.PreferenceManager
import org.json.JSONArray

class Config {

    class Widget {
        @SuppressWarnings("SpellCheckingInspection")
        companion object {
            const val ACTION_NEW_IMAGE_READY = "com.elletlar.dogwidget.UPDATE_IMAGE"
        }
    }

    class Urls {
        companion object {
            @JvmStatic
            fun getDefaultImages() : Array<String> {
                return Urls.DEFAULT_IMAGES
            }

            const val MAX_DEFAULT_IMAGES = 12

            @JvmStatic
            private val DEFAULT_IMAGES = arrayOf(
                    "https://images.dog.ceo/breeds/mix/Polo.jpg",
                    "https://images.dog.ceo/breeds/maltese/n02085936_548.jpg",
                    "https://images.dog.ceo/breeds/retriever-golden/n02099601_1259.jpg",
                    "https://images.dog.ceo/breeds/elkhound-norwegian/n02091467_3849.jpg",
                    "https://images.dog.ceo/breeds/dingo/n02115641_12634.jpg",
                    "https://images.dog.ceo/breeds/kuvasz/n02104029_3942.jpg",
                    "https://images.dog.ceo/breeds/akita/Akita_Dog.jpg",
                    "https://images.dog.ceo/breeds/pomeranian/n02112018_12493.jpg",
                    "https://images.dog.ceo/breeds/deerhound-scottish/n02092002_12414.jpg",
                    "https://images.dog.ceo/breeds/stbernard/n02109525_997.jpg",
                    "https://images.dog.ceo/breeds/labrador/n02099712_6664.jpg",
                    "https://images.dog.ceo/breeds/akita/An_Akita_Inu_resting.jpg")
        }
    }

    class Keys {
        /**
         * Simple scheme:
         *   all-breeds: Returns a list of all breeds
         *   all-labrador: Returns a list of all labrador sub breeds
         *   ...
         *   selected-breeds: Returns a list of the sub breeds the user has chosen
         *   selected-labrador: Returns a list of the labrador sub breeds the user has chosen
         *
         * Design Limitation:
         *   . SQL DB is a better choice, but storing items in ShredPreferences will be quick and
         *     also give me a chance to lear to use arrays, lists and hashes in Kotlin
         *   . DOG APIs only return URLs with no meta data, but it wouldn't be surprising if they add
         *     context info in the future, so again a DB is s a better choice
         */
        companion object {
            /** Boolean: true app has been seutp */
            const val SETUP = "setup"

            // --- Breed Info
            // Key to get all breeds
            const val ALL_BREEDS = "all_breeds"
            const val ALL_BREEDS_SELECTED = "all_breeds-selected"

            private const val PREFIX_ALL = "all"
            private const val PREFIX_SELECTED = "selected"

            const val BREED_UPDATE_TIME = "breed_update_time"

            // --- Options
            const val ROUNDED = "rounded"
            const val DEFAULT_ROUNDED =  true

            const val ALPHA = "alpha"
            const val DEFAULT_ALPHA = 128 // 0 [Transparent] to 255 [Opaque]


            // Key that store all subBreeds for a breed
            fun getSubBreedKey(subBreed : String) : String {
                return "$PREFIX_ALL-$subBreed"
            }

            // Key that stores all the user's chooses subBreeds for a breed
            fun getSubBreedSelectedKey(subBreed : String) : String {
                return "$PREFIX_SELECTED-$subBreed"
            }

            const val CURRENT_IMAGE_URLS = "current_image_urls"

            /**
             * Save list into shared preferences as a JSONArray
             *
             * Rationale: Overcomes SharedPreferences inability to store an ordered collection
             */
            @JvmStatic
            fun putList(context : Context, key: String, list : List<String>){
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val jsonArray = JSONArray(list)
                val ed = prefs.edit()
                ed.putString(key, jsonArray.toString())
                ed.apply()
            }

            /**
             * Load list from a JSONArray stored as a string in shared preferences
             */
            @JvmStatic
            fun getList(context : Context, key: String) : List<String> {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val arrayList = ArrayList<String>()


                val jsonArray = JSONArray(prefs.getString(key, "[]"))
                for  (i in 0 until jsonArray.length()) {
                    arrayList.add(jsonArray.getString(i))
                }

                return arrayList
            }
        }
    }
}