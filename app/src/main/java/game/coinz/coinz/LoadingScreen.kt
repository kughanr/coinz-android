package game.coinz.coinz

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.android.synthetic.main.activity_loading_screen.*
import org.jetbrains.anko.toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

//Interim activity/splash screen from logging in, to main activity, used to
//get data from the database and download the geojson file
class LoadingScreen : AppCompatActivity(), SensorEventListener{

    private val tag = "LoadingScreen"

    private val downloadRunner = DownloadCompleteRunner
    private val downloadTask = DownloadFileTask(downloadRunner)
    private var downloadDate = "" //Format YYYY/MM/DD
    private var currentDate = "" //Format YYYY/MM/DD
    private val preferencesFile = "MyPrefsFile" //for storing purposes
    private lateinit var url: String  //url used to download json
    private val cal = Calendar.getInstance() //calendar to get dates

    private val g : Globals = Globals.getInstance()

    private var sensorManager : SensorManager ?= null
    private var activityRunning : Boolean = false

    private lateinit var mAuth: FirebaseAuth
    private var firestore: FirebaseFirestore? = null
    private var currentUser: FirebaseUser?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loading_screen)
        initializeTheme() //set up the theme in shared preferences

        //setup firestore
        mAuth = FirebaseAuth.getInstance()
        currentUser = mAuth.currentUser //set to current user
        firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        firestore?.firestoreSettings = settings
        g.firestore = firestore
        g.firebase = mAuth
        g.firestoreUser = firestore?.collection("users")?.document(currentUser?.email!!) //get the user document
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager //used for enabling pedometer
    }

    override fun onStart() {
        super.onStart()
        loading.text = getString(R.string.getting_coins)
        downloadGEOjson() //downloads the current days geojson file if not yet downloaded else gets it from shared preferences
        loading.text = getString(R.string.retrieving_data)
        getFirestoreData() //gets user information from firestore, will return to login screen if failed

    }

    override fun onResume() {
        super.onResume()
        activityRunning = true
        //handling pedometer cases if device has sensor
        val countSensor : Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (countSensor != null ){
            sensorManager!!.registerListener(this, countSensor, SensorManager.SENSOR_DELAY_UI)
            toast("Steps are being counted")
            Log.d(tag, "count sensor available ${sensorManager.toString()}, $countSensor")
        } else {
            toast("Count sensor unavailable")
            Log.d(tag, "count sensor unavailable ${sensorManager.toString()}, $countSensor")
        }

    }

    //AsyncTask for downloading the geojson file (from lectures)
    interface DownloadCompleteListener {
        fun downloadComplete(result: String)
    }
    object DownloadCompleteRunner : DownloadCompleteListener {
        private var result : String? = null
        override fun downloadComplete(result: String){
            this.result = result
        }
    }
    class DownloadFileTask(private val caller : DownloadCompleteListener) : AsyncTask<String, Void, String>() {
        private var g = Globals.getInstance()
        override fun doInBackground(vararg urls: String): String = try {
            loadFileFromNetwork(urls[0])
        } catch (e: IOException) {
            "Unable to load content. Check your network connection ${e.message}"
        }
        private fun loadFileFromNetwork(urlString: String): String {
            val stream : InputStream = downloadUrl(urlString)
            return stream.bufferedReader().use{it.readText()} // Read input from stream, build result as a string
        }
        // Given a string representation of a URL, sets up a connection and gets an input stream.
        @Throws(IOException::class)
        private fun downloadUrl(urlString: String): InputStream {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 10000 // milliseconds
            conn.connectTimeout = 15000 // milliseconds
            conn.requestMethod = "GET"
            conn.doInput = true
            conn.connect() // Starts the query
            return conn.inputStream
        }
        override fun onPostExecute(result: String) {
            super.onPostExecute(result)
            g.getExchangeRates()
            caller.downloadComplete(result)
        }
    } // end class DownloadFileTask


    private fun downloadGEOjson(){ //downloads geojson file
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        downloadDate = settings.getString("lastDownloadDate", "" )!! //gets the last downloaded date, uses "" as the default value
        Log.d(tag, "[onStart], $downloadDate")
        //get current data
        var day = cal.get(Calendar.DAY_OF_MONTH).toString()
        var month = (cal.get(Calendar.MONTH)+1).toString()
        Log.d(tag, "${day.length}")
        if (day.toInt() < 10) {
            day = "0$day"
        }
        if (month.length < 2 && month.toInt()<10){
            month = "0$month"
        }
        currentDate = cal.get(Calendar.YEAR).toString() + "/" + month + "/" + day
        url = "http://homepages.inf.ed.ac.uk/stg/coinz/$currentDate/coinzmap.geojson"
        Log.d(tag, "[onStart] $url, $downloadDate, $currentDate, ${currentDate==downloadDate}")
        if (currentDate != downloadDate) { //If gejosn file is not downloaded
            Log.d(tag, "currentDate != downloadDate")
            g.geojson = downloadTask.execute(url).get()     //download geojson file
        } else{ //if geojson file is donwloaded
            Log.d(tag, "currentDate == downloadDate")
            g.geojson = settings.getString("geojson", "") //get from shared preferences
            g.getExchangeRates()
        }
        //Log.d(tag, "[onStart] $downloadDate geojson and url] ${g.geojson} $url ") //To debug

    }

    @Suppress("UNCHECKED_CAST")
    //get all the firestore data
    private fun getFirestoreData(){
        Log.d(tag, "firestore ${g.firestoreUser}")
        g.firestoreUser?.get()
                ?.addOnCompleteListener {
                    val loginActivity = Intent(this, LoginActivity::class.java)
                    if (it.isSuccessful) {
                        val documentSnapshot = it.result
                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            g.wallet = JSONObject(documentSnapshot.getString("wallet")!!)
                            g.spareChange = JSONObject(documentSnapshot.getString("spareChange")!!)
                            g.collectedCoins = (documentSnapshot.get("collectedCoins") as List<String>).toMutableSet()
                            g.numberOfCoinsCollected = documentSnapshot.get("numberOfCoinsCollected").toString().toInt()
                            g.numberOfCoinsDeposited = documentSnapshot.get("numberOfCoinsDeposited").toString().toInt()
                            g.bankBalance = documentSnapshot.get("bankBalance").toString().toDouble()
                            g.walletSize = documentSnapshot.get("walletSize").toString().toInt()
                            g.numberOfCoinsDepositedToday = documentSnapshot.get("numberOfCoinsDepositedToday").toString().toInt()
                            g.collected50Coins = documentSnapshot.get("collected50Coins") as Boolean
                            g.earned5000Gold = documentSnapshot.get("earned5000Gold") as Boolean
                            g.transferred25Coins = documentSnapshot.get("transferred25Coins") as Boolean
                            g.stepsWalked = documentSnapshot.get("stepsWalked").toString().toInt()
                            g.downloadDate = documentSnapshot.getString("downloadDate")!!
                            g.filledOnce = false
                            Log.d(tag, "firestore walletArr ${g.wallet}, ${g.numberOfCoinsDepositedToday}, ${g.downloadDate}, ${g.spareChange}")
                            fillLocalWallet() //fill local wallet and spare change
                            val mainActivity = Intent(this, MainActivity::class.java)
                            startActivity(mainActivity)
                        } else { //if data not found, try re-login
                            Log.d(tag, "firestore failed to get data")
                            toast("Error occurred retrieving your data, please try logging in again")
                            mAuth.signOut()
                            finish()
                            startActivity(loginActivity)
                        }
                    }
                    else { //if failure to connect to firestore then ask users to check internet connection
                        toast("Please ensure your internet connection is working well")
                        startActivity(loginActivity)
                    }
                }
    }
    /*clears global data then
    gets the coins in the wallet and spare change
    with a helper function that loops through a JSONArray*/
    private fun fillLocalWallet(){
        g.numberOfPenysInWallet = 0
        g.numberOfDolrsInWallet = 0
        g.numberOfShilsInWallet = 0
        g.numberOfQuidsInWallet = 0
        g.numberOfCoinsInWallet = 0
        g.numberOfPenysInSpareChange = 0
        g.numberOfDolrsInSpareChange = 0
        g.numberOfShilsInSpareChange = 0
        g.numberOfQuidsInSpareChange = 0
        val coins =g.wallet?.getJSONArray("coins")!!
        val coinsSpareChange = g.spareChange.getJSONArray("coins")
        if(!g.filledOnce) { //to prevent wallet from double filling, only one fill per login
            Log.d(tag, "firestore fillLocalWallet  ${g.wallet}, }")
            fillLocalWalletHelper(coins, true)
            fillLocalWalletHelper(coinsSpareChange, false)
            g.filledOnce = true
        }
    }

    private fun fillLocalWalletHelper(coins:JSONArray, wallet : Boolean){ //JSONArray and boolean for wallet/spareChange
        for (i in 0 until coins.length()) {

            val coin = coins.get(i) as JSONObject
            val currency = coin.get("currency").toString()
            Log.d(tag, "fillLocalWallet $currency ")
            if (currency == "DOLR") {
                if(wallet) {
                    g.numberOfDolrsInWallet++
                } else {
                    g.numberOfDolrsInSpareChange++
                }
            }
            if (currency == "PENY") {
                if(wallet) {
                    g.numberOfPenysInWallet++
                } else {
                    g.numberOfPenysInSpareChange++
                }
            }
            if (currency == "SHIL") {
                if(wallet) {
                    g.numberOfShilsInWallet++
                } else {
                    g.numberOfShilsInSpareChange++
                }
            }
            if (currency == "QUID") {
                if(wallet) {
                    g.numberOfQuidsInWallet++
                } else {
                    g.numberOfQuidsInSpareChange++
                }
            }
        }

        g.numberOfCoinsInWallet = g.numberOfDolrsInWallet + g.numberOfQuidsInWallet + g.numberOfPenysInWallet + g.numberOfShilsInWallet

    }



    //functions for step counter
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(tag, "onAccuracyChanged")
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if(activityRunning) {
            g.stepsWalked = event?.values!![0].toInt()
            Log.d(tag, "onSensorChanged ${g.stepsWalked}, $event")
        }
    }

    //get the theme saved in shared preferences, defaults to default theme
    private fun initializeTheme(){
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        g.themeKey = settings.getString("theme", "default")!!
        setTheme(g.theme[g.themeKey]!!) //set the theme
    }

}
