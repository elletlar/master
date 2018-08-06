package com.elletlar.dogwidget.activity

import android.os.Bundle
import android.app.ExpandableListActivity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import com.elletlar.dogwidget.R
import android.util.Log
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.*
import com.elletlar.dogwidget.Config
import com.elletlar.dogwidget.service.DogService
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Implements an expandable list view of breed and sub breeds. The widget will only show dogs
 * from the breeds and sub breeds specified in this activity.
 */
class BreedActivity : ExpandableListActivity() {
    companion object {
        /** Log Tag */
        private const val TAG = "BreedActivity"
    }

    /** Shared Preferences: Holds info on breeds, sub-breeds, and their selection status */
    private lateinit var mPrefs: SharedPreferences

    // --- List
    /** The expandable list view: Breeds and sub-breeds */
    private lateinit var mExpandableList: ExpandableListView
    /** The adapter suppying breed and sub-breed info to the expandable list view */
    private lateinit var mAdapter : ExpandableListAdapter

    // --- Arrays for adapters
    /** strings for group elements */
    lateinit var mBreedArray : Array<String>
    /** strings for child elements */
    lateinit var mSubBreedArray : Array<Array<String>>

    // --- Selection Map
    /** Keep track of items that are selected */
    private val selectionMap = HashMap<String, HashMap<String, Boolean>>()

    lateinit var mNoData  : View


    /**
     * Updates the breed list from shared preferences if it is changed by the service.
     *
     *  Reference must be given a variable name due to weak reference defect in Android
     */
    val mPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs: SharedPreferences, key: String ->
        // Note: Service only uses a single "apply()" so the updating of the breed information is atomic,
        // but better to use a content provider and listen to the URI in a real project
        if (key.equals(Config.Keys.ALL_BREEDS)) {
            storedDataToSelectionHash()
            storedDataToAdapterData()
            mAdapter = ExpandableListAdapter(this)
            mExpandableList.setAdapter(mAdapter)
            if (mAdapter.groupCount > 0) {
                showNoDataView(false) // Make list visisble
            }
        }
    }

    // --- Lifecycle Methods

    // onCrete
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breed)

        // Prefs
        mPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        mPrefs.registerOnSharedPreferenceChangeListener(mPrefsListener)

        // Load the breed list and selection data [TD: Async task is better]
        storedDataToSelectionHash()
        storedDataToAdapterData()

        // Get list and set adapter
        mExpandableList = expandableListView
        mAdapter = ExpandableListAdapter(this)
        mExpandableList.setAdapter(mAdapter)

        // Handle no JSON case
        mNoData = findViewById<View>(R.id.no_data)
        findViewById<Button>(R.id.btn_fetch)
        if (mAdapter.groupCount == 0) {
            showNoDataView(true)
        }
        val fetch = findViewById<Button>(R.id.btn_fetch)
        fetch.setOnClickListener(View.OnClickListener {
            updateBreeds();
            // Test
            mPrefs.edit().putStringSet(Config.Keys.ALL_BREEDS, HashSet<String>()).apply();
        })

        // Breed click
        mExpandableList.setOnGroupClickListener { _, _, groupPosition, _ ->
            toggleSelectedBreed(groupPosition)
            mAdapter.notifyDataSetChanged()

            false // Allow event to travel to framework or sublist won't be opened
        }

        // Sub breed click
        mExpandableList.setOnChildClickListener { _, _, groupPosition, childPosition, _ ->
            toggleSelectedSubBreed(groupPosition, childPosition)
            mAdapter.notifyDataSetChanged()

            false
        }

        // Set expansion and collapse listeners
        mExpandableList.setOnGroupExpandListener { Log.e("onGroupExpand", "OK") }
        mExpandableList.setOnGroupCollapseListener { Log.e("onGroupCollapse", "OK") }
    }

    /**
     * Show or hide no data view
     * true, show no data view
     */
    fun showNoDataView(show : Boolean) {
        if (show) {
            mExpandableList.visibility = View.GONE
            mNoData.visibility = View.VISIBLE
        } else {
            mNoData.visibility = View.GONE
            mExpandableList.visibility = View.VISIBLE
        }
    }

    /**
     * Update breeds from server
     */
    private fun updateBreeds() {
        val serviceIntent = Intent(applicationContext, DogService::class.java)
        serviceIntent.action = DogService.ACTION_UPDATE_BREEDS
        applicationContext.startService(serviceIntent)
    }


    // --- Breed manipulation

    /**
     * Remove Selected Breed
     *
     * Removes a breed from the selection list in shared preferences
     */
    private fun removeSelectedBreed(breed : String) {
        val selectedBreeds =
            mPrefs.getStringSet(Config.Keys.ALL_BREEDS_SELECTED, LinkedHashSet<String>())
        if (selectedBreeds.contains(breed)) {
            selectedBreeds.remove(breed)
            mPrefs.edit().putStringSet(Config.Keys.ALL_BREEDS_SELECTED, selectedBreeds).apply()
        }
    }

    /**
     * Remove Selected Breed
     *
     * Removes a breed from the selection list in shared preferences
     */
    private fun addSelectedBreed(breed : String) {
        val selectedBreeds =
                mPrefs.getStringSet(Config.Keys.ALL_BREEDS_SELECTED, LinkedHashSet<String>())
        if (!selectedBreeds.contains(breed)) {
            selectedBreeds.add(breed)
            mPrefs.edit().putStringSet(Config.Keys.ALL_BREEDS_SELECTED, selectedBreeds).apply()
        }
    }

    // --- Sub Breed Manipulation

    /**
     * Toggle the selection on the sub breed
     *
     * @breedPos the position of the breed
     * @subBreedPos the position of the sub breed
     */
    private fun toggleSelectedSubBreed(breedPos : Int, subBreedPos: Int) {
        val breed = mBreedArray[breedPos]
        val subBreed = mSubBreedArray[breedPos][subBreedPos]

        // Handle the sub breed
        val set = mPrefs.getStringSet(Config.Keys.getSubBreedSelectedKey(breed), LinkedHashSet<String>())
        var subBreedMap = selectionMap[breed]

        if (set.contains(subBreed)) {
            set.remove(subBreed)
            if (subBreedMap != null)
                subBreedMap.remove(subBreed)
        } else {
            set.add(subBreed)
            if (subBreedMap == null) {
                subBreedMap = HashMap()
                selectionMap[breed] = subBreedMap
            }
            subBreedMap[subBreed] = true
        }

        mPrefs.edit().putStringSet(Config.Keys.getSubBreedSelectedKey(breed), set).apply()
        if (set.size == 0) {
            // Last sub-breed removed (deselect breed)
            removeSelectedBreed(breed)
        } else if (set.size == 1) {
            // First sub-breed added (select breed)
            addSelectedBreed(breed)
        }
    }

    /**
     * Toggle the selected breed
     *
     * Note: A selected breed that has no sub breeds appears as an empty sub breed hash
     * in the selectionMap. An unselected breed has no key in selectionMap.
     *
     * @breedPos the position of the breed
     */
    private fun toggleSelectedBreed(breedPos : Int) {
        val breed = mBreedArray[breedPos]
        val subBreedArr = mSubBreedArray[breedPos]
        if (subBreedArr.isNotEmpty()) {
            return
        }

        val set = mPrefs.getStringSet(Config.Keys.ALL_BREEDS_SELECTED, LinkedHashSet<String>())
        val isSelected = set.contains(breed)
        if (isSelected) {
            selectionMap.remove(breed) // Update list
            set.remove(breed) // Update DB
        } else {
            selectionMap[breed] = HashMap() // Update List
            set.add(breed) // Update DB
        }
        mPrefs.edit().putStringSet(Config.Keys.ALL_BREEDS_SELECTED, set).apply()
    }

    // Adapter Data
    private fun storedDataToAdapterData() {
        mSubBreedArray = emptyArray()

        val breedList = mPrefs.getStringSet(Config.Keys.ALL_BREEDS, LinkedHashSet<String>())
        mBreedArray = breedList.toTypedArray()
        mBreedArray.sort()

        for (breed in mBreedArray) {
            val subBreedList = mPrefs.getStringSet(Config.Keys.getSubBreedKey(breed),
                    LinkedHashSet<String>())
            val temp = subBreedList.toTypedArray()
            temp.sort()
            mSubBreedArray += temp
        }

   }

    /**
     *
     */
    private fun storedDataToSelectionHash() {
        val breedList = mPrefs.getStringSet(Config.Keys.ALL_BREEDS_SELECTED, LinkedHashSet<String>())
        for (breed in breedList) {
            val subBreedMap : HashMap<String, Boolean> = HashMap()

            val subBreedList = mPrefs.getStringSet(Config.Keys.getSubBreedSelectedKey(breed), LinkedHashSet<String>())
            for (subBreed in subBreedList) {
                subBreedMap[subBreed] =  true
            }

            // Add sub-breeds or empty sub-breed hash if there are none
            selectionMap[breed] = subBreedMap
        }
    }

    // --- Adapter
    inner class ExpandableListAdapter(private val myContext: Context) : BaseExpandableListAdapter() {
        override fun onGroupExpanded(groupPosition: Int) {
            super.onGroupExpanded(groupPosition)
            Log.d(TAG, "Group expanded")
        }

        override fun getGroup(groupPosition: Int): Any? {
            return null
        }

        // --- Breed Related Methods

        // getGroupCount
        override fun getGroupCount(): Int {
            return mBreedArray.size
        }

        // getGroupId
        override fun getGroupId(groupPosition: Int): Long {
            return 0
        }

        // getGroupView
        override fun getGroupView(groupPosition: Int, isExpanded: Boolean,
                                  convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                val inflater = myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    view = inflater.inflate(R.layout.row_breed, parent, false)
            }

            val breed = mBreedArray[groupPosition]

            // Breed
            val tvGroupName = view!!.findViewById<TextView>(R.id.txt) as TextView
            tvGroupName.text = breed

            // CheckBox
            val checkBox = view.findViewById<CheckBox>(R.id.chk) as CheckBox
            val expanderImage = view.findViewById<ImageView>(R.id.img_expander) as ImageView

            // Checkbox
            if (mSubBreedArray[groupPosition].isEmpty()) {
                checkBox.isChecked = selectionMap.containsKey(breed)
                checkBox.visibility = View.VISIBLE
                expanderImage.visibility = View.GONE
            // Expander
            } else {
                expanderImage.visibility = View.VISIBLE
                if (isExpanded) {
                    expanderImage.setImageResource(R.drawable.expander_ic_maximized)
                } else {
                    expanderImage.setImageResource(R.drawable.expander_ic_minimized)
                }
                checkBox.visibility = View.GONE
            }

            return view
        }

        // --- Sub Breed Related Methods

        // getChild
        override fun getChild(groupPosition: Int, childPosition: Int): Any? {
            return null
        }

        // getChildId
        override fun getChildId(groupPosition: Int, childPosition: Int): Long {
            return 0
        }

        // getChildView
        override fun getChildView(groupPosition: Int, childPosition: Int,
                                  isLastChild: Boolean, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                val inflater = myContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                view = inflater.inflate(R.layout.row_sub_breed, parent, false)
            }

            // Model
            val breed = mBreedArray[groupPosition]
            val subBreed = mSubBreedArray[groupPosition][childPosition]

            // Text View
            val subBreedTv = view!!.findViewById<TextView>(R.id.txt) as TextView
            subBreedTv.text = subBreed

            // Selection Info
            // var subBreedMap = selectionMap.get(breed)

            // CheckBox
            val checkBox = view.findViewById<CheckBox>(R.id.chk) as CheckBox
            val containsKey = selectionMap.containsKey(breed)
            val containsSubBreedKey = containsKey && selectionMap[breed]?.containsKey(subBreed)!!
            checkBox.isChecked = containsKey && containsSubBreedKey
            return view
        }

        override fun getChildrenCount(groupPosition: Int): Int {
            return mSubBreedArray[groupPosition].size
        }

        override fun hasStableIds(): Boolean {
            return false
        }

        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean {
            return true
        }
    }
}