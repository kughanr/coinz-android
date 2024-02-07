package game.coinz.coinz

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

//variables and functions shared between activities
class Globals private constructor() {

    private val tag : String = "Globals"

    // Global variable
    var bankBalance: Double = 0.0       //bank balance in gold
    var numberOfCoinsDeposited: Int = 0
    var numberOfCoinsCollected: Int = 0
    var numberOfPenysInWallet: Int = 0
    var numberOfDolrsInWallet: Int = 0
    var numberOfShilsInWallet: Int = 0
    var numberOfQuidsInWallet: Int = 0
    var numberOfCoinsInWallet: Int = 0
    var numberOfPenysInSpareChange: Int = 0
    var numberOfDolrsInSpareChange: Int = 0
    var numberOfShilsInSpareChange: Int = 0
    var numberOfQuidsInSpareChange: Int = 0
    var numberOfCoinsDepositedToday: Int = 0
    var numberOfCoinsTransferred: Int = 0
    var geojson: String? = null
    var firebase: FirebaseAuth?=null
    var firestore: FirebaseFirestore? = null
    var firestoreUser: DocumentReference? = null
    var collectedCoins: MutableSet<String> = mutableSetOf()
    var penyExchangeRate : Double = 0.0
    var quidExchangeRate : Double = 0.0
    var shilExchangeRate : Double = 0.0
    var dolrExchangeRate : Double = 0.0
    var walletSize : Int = 75
    var stepsWalked : Int = 0
    //var appRunning : Boolean = false
    var walletString : String =
        "{" +
            "coins : []" +
                "}"
    var wallet: JSONObject?=JSONObject(walletString)
    var spareChange: JSONObject  = JSONObject(walletString)
    var filledOnce: Boolean = false

    var collected50Coins : Boolean = true
    var earned5000Gold : Boolean = true
    var transferred25Coins : Boolean = true

    var downloadDate : String = ""


    //hashMap of themes
    var theme : Map<String, Int> = mapOf(
            "default" to R.style.AppTheme,
            "green" to R.style.AppThemeGreen,
            "dark" to R.style.AppThemeDark
    )

    var themeKey : String = "default"

    var originLocation : Location? = null





    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: Globals? = null

        @Synchronized
        fun getInstance(): Globals {
            if (instance == null) {
                instance = Globals()
            }
            return instance as Globals
        }
    }

    fun updateWallet(value:Double, currency:String){

        val coin = "{value : $value, currency : $currency}"
        val coinz = JSONObject(coin)
        wallet?.getJSONArray("coins")!!.put(coinz) //add the collected coin as a JSONObject to the wallet
        updateFirestore() //update database
    }

    fun updateFirestore(){ //updates database with local data
        firestoreUser?.update(
                "bankBalance",bankBalance, //bank balance in gold
                "numberOfCoinsDeposited",numberOfCoinsDeposited,
                "numberOfCoinsCollected",numberOfCoinsCollected,
                "numberOfCoinsTransferred",numberOfCoinsTransferred,
                "walletSize",walletSize,
                "collectedCoins",collectedCoins.toList(),
                //"numberOfCoinsInWallet",numberOfCoinsInWallet,
                "numberOfCoinsDepositedToday",numberOfCoinsDepositedToday,
                "collected50Coins",collected50Coins,
                "earned5000Gold",earned5000Gold,
                "transferred25Coins",transferred25Coins,
                "wallet",wallet.toString(),
                "spareChange", spareChange.toString(),
                "stepsWalked", stepsWalked,
                "downloadDate", downloadDate
        )
    }

    fun getExchangeRates() { //converts geojson file to JSONObject and extract info
        Log.d(tag, "getExchangeRates g.geojson")
        if (geojson != null || geojson != "") {
            val geoJson = JSONObject(geojson)
            val j = geoJson.get("rates") as JSONObject
            penyExchangeRate = j.get("PENY").toString().toDouble()
            dolrExchangeRate = j.get("DOLR").toString().toDouble()
            quidExchangeRate = j.get("QUID").toString().toDouble()
            shilExchangeRate = j.get("SHIL").toString().toDouble()
            Log.d(tag, "exchange rates ${j.get("SHIL")}")
        } else {
            Log.d(tag, "geojson null")

        }
    }

}