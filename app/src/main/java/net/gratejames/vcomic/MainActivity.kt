package net.gratejames.vcomic

import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase


@Entity
data class Comic(
//    @PrimaryKey val uid: Int,
    @PrimaryKey(autoGenerate = true) var uid: Int,
    @ColumnInfo(name = "Name") var name: String,
    @ColumnInfo(name = "Link") var link: String,
    @ColumnInfo(name = "Zoom") var zoom: Int
)

@Dao
interface ComicDao {
    @Query("SELECT * FROM comic ORDER BY uid ASC")
    fun getAll(): List<Comic>

    @Query("SELECT * FROM comic WHERE uid IN (:comicIds)")
    fun loadAllByIds(comicIds: IntArray): List<Comic>

    @Query("SELECT * FROM comic WHERE uid = (:comicId)")
    fun getAtId(comicId: Int): Comic

    @Query("SELECT * FROM comic ORDER BY uid DESC LIMIT 1")
    fun getAtMaxId(): Comic

    @Query("UPDATE comic SET link = (:newLink) WHERE uid = (:comicId)")
    fun editLinkById(comicId: Int, newLink: String)

    @Query("UPDATE comic SET name = (:newName), link = (:newLink) WHERE uid = (:comicId)")
    fun editById(comicId: Int, newName: String, newLink: String)

    @Query("UPDATE comic SET name = (:newName), link = (:newLink), zoom = (:newZoom) WHERE uid = (:comicId)")
    fun editComicById(comicId: Int, newName: String, newLink: String, newZoom: Int)

    @Insert
    fun insert(comic: Comic): Long

    @Delete
    fun delete(comic: Comic)
}

@Database(
    version = 1,
    entities = [Comic::class]
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun comicDao(): ComicDao
}


class MainActivity : ComponentActivity() {
    enum class MODE {
        NONE, EDIT, ADD
    }

    var clearHistoryAfterNextLoad = false

    override fun onBackPressed() {
        val comicView = findViewById<WebView>(R.id.webView)
        if (comicView.canGoBack()) {
            comicView.goBack()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_layout)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "comic_db"
        ).allowMainThreadQueries().build()
        val comicDao = db.comicDao()

        val comics: MutableList<Comic> = comicDao.getAll().toMutableList()

        val addButton = findViewById<ImageButton>(R.id.addButton)
        val editButton = findViewById<ImageButton>(R.id.editButton)
        val closeBtn = findViewById<ImageButton>(R.id.closeButton)
        val editPanel = findViewById<ConstraintLayout>(R.id.editLayout)
        val deleteButton = findViewById<ImageButton>(R.id.deleteButton)
        val comicSpinner = findViewById<Spinner>(R.id.spinner)
        val comicView = findViewById<WebView>(R.id.webView)
        val editPanelName = findViewById<EditText>(R.id.editName)
        val editPanelLink = findViewById<EditText>(R.id.editLink)
        val sortUpButton = findViewById<ImageButton>(R.id.moveUpButton)
        val sortDownButton = findViewById<ImageButton>(R.id.moveDownButton)

        comicView.getSettings().builtInZoomControls = true;
        comicView.getSettings().displayZoomControls = false;
        comicView.getSettings().loadWithOverviewMode = true;
        comicView.getSettings().useWideViewPort = true;

        comicView.setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return false
            }
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("WEBVIEW", "Just loaded a new page: $url")
//                comics
                comics[comicSpinner.selectedItemPosition].link = url
                val uid = comics[comicSpinner.selectedItemPosition].uid
                comicDao.editLinkById(uid, url)
                if (clearHistoryAfterNextLoad) {
                    clearHistoryAfterNextLoad = false
                    comicView.clearHistory()
                }
                val zoom = comics[comicSpinner.selectedItemPosition].zoom
                if (zoom > 0) {
                    comicView.zoomBy(zoom.toFloat())
                }
                if (zoom < 0) {
                    comicView.zoomBy(1/zoom.toFloat())
                }
                super.onPageFinished(view, url)
            }
        })

        val comicNamesToSpin = arrayListOf("Loading Data...")

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            comicNamesToSpin
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) // Specifies layout
            comicSpinner.adapter = adapter
        }
        comicSpinner.setAdapter(spinnerAdapter)
        spinnerAdapter.clear()
        if (comics.isEmpty()) {
            val xkcd = Comic(0, "xkcd", "https://xkcd.com", 0)
            comics.add(xkcd)
            comicDao.insert(xkcd)
        }
        for (com in comics) {
            spinnerAdapter.add(com.name)
        }
//        comicSpinner.onItemSelectedListener = SpinnerActivity()
        comicSpinner.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, selectedItemView: View?, pos: Int, id: Long) {
                Log.d("SPINNER", "Sel, $pos")
                val thisComic = comics[pos]
                clearHistoryAfterNextLoad = true
                comicView.loadUrl(thisComic.link)
            }

            override fun onNothingSelected(parentView: AdapterView<*>?) {
                Log.d("SPINNER", "Sel: None")
            }
        }

        var mode = MODE.NONE

        sortUpButton.setOnClickListener {
            if (comicSpinner.selectedItemPosition <= 0) {
                Toast.makeText(this@MainActivity, "Already at the top", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this@MainActivity, "Swapping " + comicSpinner.selectedItemPosition.toString() + " and prev", Toast.LENGTH_SHORT).show()

            val thisOne = comics[comicSpinner.selectedItemPosition]
            val prevOne = comics[comicSpinner.selectedItemPosition - 1]
            val prevuid = thisOne.uid
            thisOne.uid = prevOne.uid
            prevOne.uid = prevuid // swap UIDs
            comicNamesToSpin[comicSpinner.selectedItemPosition] = prevOne.name
            comicNamesToSpin[comicSpinner.selectedItemPosition - 1] = thisOne.name
            comicDao.editComicById(prevOne.uid, prevOne.name, prevOne.link, prevOne.zoom)
            comicDao.editComicById(thisOne.uid, thisOne.name, thisOne.link, thisOne.zoom)
            comics[comicSpinner.selectedItemPosition] = prevOne
            comics[comicSpinner.selectedItemPosition - 1] = thisOne

            comicSpinner.setSelection(comicSpinner.selectedItemPosition - 1)
            spinnerAdapter.notifyDataSetChanged()

        }
        sortDownButton.setOnClickListener {
            if (comicSpinner.selectedItemPosition == comics.size - 1) {
                Toast.makeText(this@MainActivity, "Already at the bottom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this@MainActivity, "Swapping " + comicSpinner.selectedItemPosition.toString() + " and next", Toast.LENGTH_SHORT).show()

            val thisOne = comics[comicSpinner.selectedItemPosition]
            val nextOne = comics[comicSpinner.selectedItemPosition + 1]
            val nextvuid = thisOne.uid
            thisOne.uid = nextOne.uid
            nextOne.uid = nextvuid // swap UIDs
            comicNamesToSpin[comicSpinner.selectedItemPosition] = nextOne.name
            comicNamesToSpin[comicSpinner.selectedItemPosition + 1] = thisOne.name
            comicDao.editComicById(nextOne.uid, nextOne.name, nextOne.link, nextOne.zoom)
            comicDao.editComicById(thisOne.uid, thisOne.name, thisOne.link, thisOne.zoom)
            comics[comicSpinner.selectedItemPosition] = nextOne
            comics[comicSpinner.selectedItemPosition + 1] = thisOne

            comicSpinner.setSelection(comicSpinner.selectedItemPosition + 1)
            spinnerAdapter.notifyDataSetChanged()
        }

        addButton.setOnClickListener {
            if (mode == MODE.NONE) {
                mode = MODE.ADD
                editButton.isEnabled = false
                editPanel.visibility = View.VISIBLE
                deleteButton.visibility = View.GONE
                sortUpButton.visibility = View.GONE
                sortDownButton.visibility = View.GONE
                editPanelName.setText("")
                editPanelLink.setText("")
                comicSpinner.isEnabled = false
            } else if (mode == MODE.ADD) {
                mode = MODE.NONE
                editButton.isEnabled = true
                editPanel.visibility = View.GONE
                comicSpinner.isEnabled = true

                val newName = editPanelName.text.toString()
                val newLink = editPanelLink.text.toString()
                if (newName.isEmpty() || newLink.isEmpty()) {
                    Toast.makeText(this, "Neither can be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

//                val comicMaxId = comicDao.getAtMaxId()
//                Log.d("ADD", comicMaxId.name + "'s new next should be " + (comicMaxId.uid+1).toString())
//                comicDao.setNextById(comicMaxId.uid, comicMaxId.uid+1)

                val newComic = Comic(0, newName, newLink, 0)
                comicDao.insert(newComic)
                comics.add(newComic)
                spinnerAdapter.add(newName)
                comicSpinner.setSelection(comics.size - 1)
                clearHistoryAfterNextLoad = true
                comicView.loadUrl(newLink)
            }
//            Log.d("BUTTONS", "Add, $mode")
        }

        editButton.setOnClickListener {
            if (mode == MODE.NONE) {
                mode = MODE.EDIT
                addButton.isEnabled = false
                editPanel.visibility = View.VISIBLE
                deleteButton.visibility = View.VISIBLE
                sortUpButton.visibility = View.VISIBLE
                sortDownButton.visibility = View.VISIBLE
                editPanelName.setText(comics[comicSpinner.selectedItemPosition].name)
                editPanelLink.setText(comics[comicSpinner.selectedItemPosition].link)
                comicSpinner.isEnabled = false
            } else if (mode == MODE.EDIT) {
                mode = MODE.NONE
                addButton.isEnabled = true
                editPanel.visibility = View.GONE
                comicSpinner.isEnabled = true
                val newName = editPanelName.text.toString()
                val newLink = editPanelLink.text.toString()
                if (newName.isEmpty() || newLink.isEmpty()) {
                    Toast.makeText(this, "Neither can be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                comics[comicSpinner.selectedItemPosition].name = newName
                comics[comicSpinner.selectedItemPosition].link = newLink
                comicNamesToSpin[comicSpinner.selectedItemPosition] = newName
                spinnerAdapter.notifyDataSetChanged()
                val uid = comics[comicSpinner.selectedItemPosition].uid
                comicDao.editById(uid, newName, newLink)
                clearHistoryAfterNextLoad = true
                comicView.loadUrl(newLink)
            }
//            Log.d("BUTTONS", "Edit, $mode")
        }

        closeBtn.setOnClickListener {
            mode = MODE.NONE
            editButton.visibility = View.VISIBLE
            addButton.visibility = View.VISIBLE
            editPanel.visibility = View.GONE
            comicSpinner.isEnabled = true
//            Log.d("BUTTONS", "Close, $mode")
        }
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            comicView.goBack()
        }
        findViewById<ImageButton>(R.id.forwardButton).setOnClickListener {
            comicView.goForward()
        }
    }
}