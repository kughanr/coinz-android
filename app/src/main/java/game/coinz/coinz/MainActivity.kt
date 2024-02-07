package game.coinz.coinz

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.JsonObject
import com.mapbox.android.core.location.LocationEngine
import com.mapbox.android.core.location.LocationEngineListener
import com.mapbox.android.core.location.LocationEnginePriority
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast
import java.util.*


//Activity where the map is
class MainActivity : AppCompatActivity(),LocationEngineListener, PermissionsListener, MapboxMap.OnMarkerClickListener{

    private val tag = "MainActivity"

    private val g : Globals = Globals.getInstance()


    private lateinit var mapView: MapView

    private lateinit var map: MapboxMap
    //private var g.originLocation : Location? = null
    private lateinit var permissionsManager : PermissionsManager
    private lateinit var originPosition : Point

    private lateinit var mAuth : FirebaseAuth

    private var locationEngine : LocationEngine?=null
    private var locationLayerPlugin : LocationLayerPlugin? = null
    private var downloadDate = "" //Format YYYY/MM/DD
    private var currentDate = "" //Format YYYY/MM/DD
    private val preferencesFile = "MyPrefsFile" //for storing purposes
    private var nearbyCoins: MutableSet<String> = mutableSetOf()
    private var numberOfCoinsInWallet : Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(g.theme[g.themeKey]!!) //set the theme based on users choice
        setContentView(R.layout.activity_main)

        mAuth = FirebaseAuth.getInstance()
        if (mAuth.currentUser == null) {  //error check in case current user not found
            val login = Intent(this, LoginActivity::class.java)
            finish()
            startActivity(login)
        }

        //initialise mapbox
        Mapbox.getInstance(applicationContext, getString(R.string.access_token))
        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { mapboxMap ->
            map = mapboxMap
            enableLocation()
            map.setOnMarkerClickListener(this)
            spawnCoins()
        }

        //go to profile activity
        profileActivity.setOnClickListener {
            finish()
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        myLocationButton.isEnabled = (g.originLocation != null)  //if location is null my location button disappears
        //go to my location
        myLocationButton.setOnClickListener {
            g.originLocation?.let { location -> setCameraPosition(location)}

        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        if (outState != null) {
            mapView.onSaveInstanceState(outState)
        }
    }

    @SuppressWarnings("MissingPermission")
    override fun onStart() {
        super.onStart()
        //If permissions are granted for mapbox

        if (PermissionsManager.areLocationPermissionsGranted(this)){
            Log.d(tag, "[onStart] permissions granted")
            locationEngine?.requestLocationUpdates()
            locationLayerPlugin?.onStart()
        }
        mapView.onStart()


        val cal = Calendar.getInstance()
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
        Log.d(tag, "testing ${g.downloadDate}")
        if(g.downloadDate != currentDate){ //if this is the first time the user is seeing the map (from any device)
            Log.d(tag, "testing ${g.downloadDate}, $")
            g.downloadDate = currentDate //set the new download date
            g.numberOfCoinsDepositedToday = 0 //clear number of coins deposited
            g.firestoreUser?.update( //update database accordingly
                    "downloadDate", g.downloadDate,
                    "numberOfCoinsDepositedToday", g.numberOfCoinsDepositedToday
            )
            finish()
            startActivity(Intent(this, LoadingScreen::class.java))//since new map restart LoadingScreen to download today's map (e.g. when app is left running till next day)
        }
    }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        //update database in case anything was missed
        g.updateFirestore()
        //Stop mapbox
        locationEngine?.removeLocationUpdates()
        locationLayerPlugin?.onStop()
        mapView.onStop()

        if(locationEngine != null){
            locationEngine?.removeLocationEngineListener(this)
            locationEngine?.removeLocationUpdates()
        }

        //Shared preferences save the geojson file and the date the file was downloaded, and the theme the user is using
        val settings = getSharedPreferences(preferencesFile, Context.MODE_PRIVATE)
        val editor = settings.edit()
        if (g.geojson!=null || g.geojson!="") {     //in case of some error such as files couldn't be downloaded/not found etc.
            editor.putString("geojson", g.geojson)
        }
        editor.putString("lastDownloadDate", currentDate)
        editor.putString("theme", g.themeKey)
        editor.apply()

        Log.d(tag, "[onStop $downloadDate]")
    }
    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        locationEngine?.deactivate()
    }
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    //function to collect coins works by looping through the features in geoojson and comparing with the marker tapped
    override fun onMarkerClick(marker: Marker): Boolean {

        val fc = FeatureCollection.fromJson(g.geojson!!)
        val fs = fc.features()
        var toastCurrency = ""
        var toastValue = ""
        var collected = false
        var distance = 0.0
        if(g.originLocation != null) {
            originPosition = Point.fromLngLat(g.originLocation!!.longitude, g.originLocation!!.latitude) //convert g.originLocation to a point to be used in distance function
            val p: Point = Point.fromLngLat(marker.position.longitude, marker.position.latitude)
            Log.d(tag, "nearby coins collect coins $nearbyCoins")
            Log.d(tag, "firestore ${g.firestoreUser.toString()}")

            for (f in fs!!) {
                val j = f.properties()
                val id = j?.get("id").toString()
                val value = j?.get("value")?.asDouble
                val coinPoint: Point = f.geometry() as Point
                Log.d(tag, "distance ${pointDistance(p, originPosition)}")
                distance = pointDistance(p, originPosition)
                if (distance < 25 && !g.collectedCoins.contains(id)
                        && p == coinPoint && numberOfCoinsInWallet < g.walletSize) { //if collection conditions are satisfied

                    collected = true //mark collected
                    Log.d(tag, "this coin is being collected ${g.collectedCoins}")
                    g.collectedCoins.add(id) //add to collectedCoins
                    Log.d(tag, "this coin was collected ${g.collectedCoins}")


                    val currency = j?.get("currency").toString()
                    Log.d(tag, "CURRENCY $currency")


                    g.numberOfCoinsCollected++ //add 1 to number of coins since 1 coin was collected
                    g.numberOfCoinsInWallet++ //add to number of coins in wallet

                    toastCurrency = currency
                    toastValue = Math.round(value!!).toString()

                    //update relevant currency wallet balance
                    if (currency == "\"PENY\"") {
                        g.numberOfPenysInWallet++
                    }
                    if (currency == "\"DOLR\"") {
                        g.numberOfDolrsInWallet++
                    }
                    if (currency == "\"SHIL\"") {
                        g.numberOfShilsInWallet++
                    }
                    if (currency == "\"QUID\"") {
                        g.numberOfQuidsInWallet++
                    }
                    map.removeMarker(marker) //remove marker from map

                    if (g.collectedCoins.size >= 50) { //achievement handling
                        if (!g.collected50Coins) {
                            g.collected50Coins = true
                            g.walletSize += 5
                            toast("You have earned an achievement (Collect 50 Coins), your wallet has grown!")
                        }
                    } else {
                        g.collected50Coins = false
                    }
                    g.updateWallet(value.toDouble(), currency) //updates local wallet and database
                    break //since coin found end loop
                }
            }
        } else {
            toast("User location is loading, if this issue persists please restart the app")
        }

        if(collected){
            toast("Collected a coin worth $toastValue $toastCurrency ") //tell users what coin they collected
        } else{ //error messages if coin fails to collect
            if(distance > 25){
                toast("You are too far from the coin!")
            } else {
                if (g.numberOfCoinsInWallet >= g.walletSize) {
                    toast("Your wallet is full!")
                } else {
                    toast("The coin cannot be collected at this moment.")
                }
            }
        }
        Log.d(tag, "nearby coins $nearbyCoins collected coins ${g.collectedCoins}")

        return true
    }

    //setting up Mapbox following youtube tutorial showed in lectures (lines 259 - 321)
    private fun enableLocation() {
        if (PermissionsManager.areLocationPermissionsGranted(this)){
            Log.d(tag, "Permissions are granted")
            initializeLocationEngine()
            initializeLocationLayer()
        }
        else{
            Log.d(tag, "Permissions are not granted")
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }
    @SuppressWarnings("MissingPermission")
    private fun initializeLocationEngine() {
        locationEngine = LocationEngineProvider(this).obtainBestLocationEngineAvailable()
        locationEngine?.priority = LocationEnginePriority.HIGH_ACCURACY
        locationEngine?.activate()
        val lastLocation = locationEngine?.lastLocation
        if (lastLocation != null){
            g.originLocation = lastLocation
            setCameraPosition(lastLocation)
        } else {
            locationEngine?.addLocationEngineListener(this)
        }
        Log.d(tag, "[locationEngine] ")
    }
    @SuppressWarnings("MissingPermission")
    private fun initializeLocationLayer() {
        locationLayerPlugin = LocationLayerPlugin(mapView, map, locationEngine)
        locationLayerPlugin?.setLocationLayerEnabled(true)
        locationLayerPlugin?.cameraMode = CameraMode.TRACKING
        locationLayerPlugin?.renderMode = RenderMode.NORMAL

    }
    private fun setCameraPosition(location: Location){
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(location.latitude, location.longitude),15.0))
    }
    override fun onLocationChanged(location: Location?) {
        location?.let{
            g.originLocation = location
            setCameraPosition(location)
        }
    }
    @SuppressWarnings("MissingPermission")
    override fun onConnected() {
        Log.d(tag, "[onConnected] requesting location updates")
        locationEngine?.requestLocationUpdates()
    }
    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Log.d(tag, "Permissions: $permissionsToExplain")
        toast("We need access to your location so you can collect coins")
    }
    override fun onPermissionResult(granted: Boolean) {
       if (granted) {
           enableLocation()
       } else{
           toast("Coinz cannot be played without access to permissions")
       }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    //spawns the coins from geojson file
    private fun spawnCoins(){
        if(g.geojson==null) {
            finish()
            startActivity(Intent(this, LoadingScreen::class.java))
        } else {
            val fc = FeatureCollection.fromJson(g.geojson!!)
            val fs = fc.features()
            for (f in fs!!) {
                val p: Point = f.geometry() as Point
                val l = LatLng(p.latitude(), p.longitude())
                val j: JsonObject? = f.properties()
                val id: String = j?.get("id").toString()

                if (g.collectedCoins.contains(id)) { //if coin is in collected coins list then don't spawn the marker
                    Log.d(tag, "[spawnCoins] coin has been collected $id")
                } else {
                    map.addMarker(MarkerOptions().position(l))
                }
            }
        }
    }

    private fun pointDistance(a : Point, b : Point): Double {
        val r = 6371.392896 // Radius of the earth in km
        val latDiff = Math.toRadians(b.latitude()-a.latitude())
        val lonDiff = Math.toRadians(b.longitude()-a.longitude())
        val d = Math.sin(latDiff/2) * Math.sin(latDiff/2) + Math.cos(Math.toRadians(a.latitude())) * Math.cos(Math.toRadians(b.latitude())) *
                        Math.sin(lonDiff/2) * Math.sin(lonDiff/2)
        val c = 2 * Math.atan2(Math.sqrt(d), Math.sqrt(1-d))
        return Math.abs(r * c * 1000) // Distance in m
    } //function to calculate distance between 2 points using latitude and longitude

}







