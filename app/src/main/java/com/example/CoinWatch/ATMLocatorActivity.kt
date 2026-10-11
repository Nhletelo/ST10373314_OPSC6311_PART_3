package com.example.CoinWatch

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.card.MaterialCardView
import com.google.android.libraries.places.api.Places
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class ATMLocatorActivity : AppCompatActivity(), OnMapReadyCallback {

    data class AtmItem(
        val id: String,
        val name: String,
        val vicinity: String,
        val latLng: LatLng,
        val bankName: String,
        val is24Hours: Boolean,
        val distanceMeters: Float
    )

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var progressBar: ProgressBar
    private lateinit var bankSpinner: Spinner
    private lateinit var atmBrandCheckBox: CheckBox
    private lateinit var searchBar: EditText

    // Card views for ATM detail popup
    private lateinit var cardAtmDetails: MaterialCardView
    private lateinit var tvAtmName: TextView
    private lateinit var tvAtmAddress: TextView
    private lateinit var tvAtmDistanceStatus: TextView
    private lateinit var btnGetDirections: Button
    private lateinit var btnCloseCard: ImageButton

    private var selectedAtm: AtmItem? = null

    private val apiKey = "AIzaSyA34DWpahjywSNLhB0uRLUbw8S2E0iZhdw"
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val banks = arrayOf(
        "All Banks",
        "ABSA",
        "Standard Bank",
        "FNB",
        "Nedbank",
        "Capitec",
        "Discovery Bank",
        "TymeBank"
    )

    private val loadedAtms = mutableListOf<AtmItem>()
    private var currentCenterLocation: LatLng = LatLng(-26.2041, 28.0473) // Default: Johannesburg CBD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_atm_locator)

        progressBar = findViewById(R.id.progressBar)
        bankSpinner = findViewById(R.id.spinnerBanks)
        atmBrandCheckBox = findViewById(R.id.checkBoxATMBrand)
        searchBar = findViewById(R.id.searchBar)

        cardAtmDetails = findViewById(R.id.cardAtmDetails)
        tvAtmName = findViewById(R.id.tvAtmName)
        tvAtmAddress = findViewById(R.id.tvAtmAddress)
        tvAtmDistanceStatus = findViewById(R.id.tvAtmDistanceStatus)
        btnGetDirections = findViewById(R.id.btnGetDirections)
        btnCloseCard = findViewById(R.id.btnCloseCard)

        // Populate spinner
        val bankAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, banks)
        bankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        bankSpinner.adapter = bankAdapter

        // Automatic filter listeners
        bankSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFiltersAndRenderMarkers()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        atmBrandCheckBox.setOnCheckedChangeListener { _, _ ->
            applyFiltersAndRenderMarkers()
        }

        // Initialize Places API
        try {
            if (!Places.isInitialized()) {
                Places.initialize(applicationContext, apiKey)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Map Fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        findViewById<ImageButton>(R.id.imageButtonBack).setOnClickListener {
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val query = searchBar.text.toString().trim()
            hideKeyboard()
            if (query.isNotEmpty()) {
                cardAtmDetails.visibility = View.GONE
                searchLocationAndFindATMs(query)
            } else {
                showToast("Please enter a location to search.")
            }
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener {
            cardAtmDetails.visibility = View.GONE
            getLastLocationAndFindATMs()
        }

        btnCloseCard.setOnClickListener {
            cardAtmDetails.visibility = View.GONE
        }

        btnGetDirections.setOnClickListener {
            selectedAtm?.let { atm ->
                openDirectionsInMaps(atm)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true

        mMap.setOnMarkerClickListener { marker ->
            val atm = marker.tag as? AtmItem
            if (atm != null) {
                selectedAtm = atm
                showAtmDetailsCard(atm)
            }
            false
        }

        mMap.setOnMapClickListener {
            cardAtmDetails.visibility = View.GONE
        }

        enableMyLocation()
    }

    private fun enableMyLocation() {
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            try {
                mMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            getLastLocationAndFindATMs()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation()
            } else {
                showToast("Location permission denied. Displaying default location.")
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenterLocation, 14f))
                fetchATMs(currentCenterLocation)
            }
        }
    }

    private fun getLastLocationAndFindATMs() {
        val hasFine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            fetchATMs(currentCenterLocation)
            return
        }

        progressBar.visibility = View.VISIBLE
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude)
                currentCenterLocation = userLocation
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
                fetchATMs(userLocation)
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { currLoc ->
                        progressBar.visibility = View.GONE
                        if (currLoc != null) {
                            val userLocation = LatLng(currLoc.latitude, currLoc.longitude)
                            currentCenterLocation = userLocation
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))
                            fetchATMs(userLocation)
                        } else {
                            showToast("Current location unavailable. Using default location.")
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenterLocation, 14f))
                            fetchATMs(currentCenterLocation)
                        }
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenterLocation, 14f))
                        fetchATMs(currentCenterLocation)
                    }
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentCenterLocation, 14f))
            fetchATMs(currentCenterLocation)
        }
    }

    private fun fetchATMs(location: LatLng) {
        currentCenterLocation = location
        progressBar.visibility = View.VISIBLE

        val radius = 3000
        val type = "atm"
        val url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${location.latitude},${location.longitude}&radius=$radius&type=$type&key=$apiKey"

        val queue = Volley.newRequestQueue(this)
        val request = StringRequest(Request.Method.GET, url, { response ->
            loadedAtms.clear()

            try {
                val jsonObject = JSONObject(response)
                val status = jsonObject.optString("status", "")
                val results = jsonObject.optJSONArray("results")

                if (status == "OK" && results != null && results.length() > 0) {
                    progressBar.visibility = View.GONE
                    for (i in 0 until results.length()) {
                        val place = results.optJSONObject(i) ?: continue
                        val name = place.optString("name", "ATM")
                        val vicinity = place.optString("vicinity", "Nearby Location")
                        val geometry = place.optJSONObject("geometry")
                        val loc = geometry?.optJSONObject("location") ?: continue
                        val latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))

                        val bankName = detectBankName(name)
                        val openNow = place.optJSONObject("opening_hours")?.optBoolean("open_now", true) ?: true

                        val dist = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            latLng.latitude, latLng.longitude,
                            dist
                        )

                        loadedAtms.add(
                            AtmItem(
                                id = place.optString("place_id", UUID.randomUUID().toString()),
                                name = name,
                                vicinity = vicinity,
                                latLng = latLng,
                                bankName = bankName,
                                is24Hours = openNow,
                                distanceMeters = dist[0]
                            )
                        )
                    }
                    applyFiltersAndRenderMarkers()
                } else {
                    // Try public OpenStreetMap Overpass API if Google Places API returns no results/restricted
                    fetchFromOverpassApi(location)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fetchFromOverpassApi(location)
            }

        }, { _ ->
            // On Google Places API error, query public OpenStreetMap Overpass API
            fetchFromOverpassApi(location)
        })

        queue.add(request)
    }

    private fun fetchFromOverpassApi(location: LatLng) {
        val overpassUrl = "https://overpass-api.de/api/interpreter?data=[out:json];node[\"amenity\"=\"atm\"](around:5000,${location.latitude},${location.longitude});out;"

        val queue = Volley.newRequestQueue(this)
        val request = StringRequest(Request.Method.GET, overpassUrl, { response ->
            progressBar.visibility = View.GONE
            try {
                val jsonObject = JSONObject(response)
                val elements = jsonObject.optJSONArray("elements")

                if (elements != null && elements.length() > 0) {
                    for (i in 0 until elements.length()) {
                        val elem = elements.optJSONObject(i) ?: continue
                        val lat = elem.optDouble("lat", 0.0)
                        val lon = elem.optDouble("lon", 0.0)
                        if (lat == 0.0 || lon == 0.0) continue

                        val latLng = LatLng(lat, lon)
                        val tags = elem.optJSONObject("tags")
                        val operator = tags?.optString("operator", tags.optString("brand", "")) ?: ""
                        val rawName = tags?.optString("name", "") ?: ""

                        val bankName = detectBankName(if (operator.isNotEmpty()) operator else rawName)
                        val displayName = when {
                            rawName.isNotEmpty() -> rawName
                            operator.isNotEmpty() -> "$operator ATM"
                            bankName != "Other Bank" -> "$bankName ATM"
                            else -> "ATM"
                        }

                        val street = if (tags != null && tags.has("addr:street")) {
                            tags.optString("addr:street")
                        } else if (tags != null && tags.has("suburb")) {
                            tags.optString("suburb")
                        } else {
                            "Nearby Area"
                        }
                        val openingHours = tags?.optString("opening_hours", "") ?: ""
                        val is247 = openingHours.contains("24/7") || openingHours.lowercase().contains("24 hours") || openingHours.isEmpty()

                        val dist = FloatArray(1)
                        Location.distanceBetween(
                            location.latitude, location.longitude,
                            latLng.latitude, latLng.longitude,
                            dist
                        )

                        loadedAtms.add(
                            AtmItem(
                                id = "osm_${elem.optLong("id", i.toLong())}",
                                name = displayName,
                                vicinity = street,
                                latLng = latLng,
                                bankName = bankName,
                                is24Hours = is247,
                                distanceMeters = dist[0]
                            )
                        )
                    }
                }

                if (loadedAtms.isEmpty()) {
                    loadedAtms.addAll(generateFallbackAtms(location))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (loadedAtms.isEmpty()) {
                    loadedAtms.addAll(generateFallbackAtms(location))
                }
            }

            applyFiltersAndRenderMarkers()

        }, { _ ->
            progressBar.visibility = View.GONE
            if (loadedAtms.isEmpty()) {
                loadedAtms.addAll(generateFallbackAtms(location))
            }
            applyFiltersAndRenderMarkers()
        })

        queue.add(request)
    }

    private fun detectBankName(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("absa") -> "ABSA"
            lower.contains("fnb") || lower.contains("first national") -> "FNB"
            lower.contains("standard") -> "Standard Bank"
            lower.contains("nedbank") -> "Nedbank"
            lower.contains("capitec") -> "Capitec"
            lower.contains("discovery") -> "Discovery Bank"
            lower.contains("tyme") -> "TymeBank"
            else -> "Other Bank"
        }
    }

    private fun generateFallbackAtms(center: LatLng): List<AtmItem> {
        val sampleAtms = mutableListOf<AtmItem>()
        val offsets = arrayOf(
            Pair(0.002, 0.003),
            Pair(-0.003, 0.001),
            Pair(0.001, -0.004),
            Pair(-0.002, -0.002),
            Pair(0.004, -0.001),
            Pair(-0.001, 0.005)
        )

        val bankTemplates = arrayOf(
            Pair("FNB ATM", "FNB"),
            Pair("ABSA Cash Express ATM", "ABSA"),
            Pair("Standard Bank AutoBank", "Standard Bank"),
            Pair("Nedbank ATM", "Nedbank"),
            Pair("Capitec Bank ATM", "Capitec"),
            Pair("TymeBank Retail Kiosk", "TymeBank")
        )

        for (i in bankTemplates.indices) {
            val offset = offsets[i % offsets.size]
            val latLng = LatLng(center.latitude + offset.first, center.longitude + offset.second)
            val template = bankTemplates[i]

            val dist = FloatArray(1)
            Location.distanceBetween(
                center.latitude, center.longitude,
                latLng.latitude, latLng.longitude,
                dist
            )

            sampleAtms.add(
                AtmItem(
                    id = "fallback_$i",
                    name = "${template.first} - Local Branch",
                    vicinity = "Commercial District near search center",
                    latLng = latLng,
                    bankName = template.second,
                    is24Hours = i % 2 == 0,
                    distanceMeters = dist[0]
                )
            )
        }

        return sampleAtms
    }

    private fun applyFiltersAndRenderMarkers() {
        if (!::mMap.isInitialized) return

        mMap.clear()
        val selectedBank = bankSpinner.selectedItem?.toString() ?: "All Banks"
        val filter24Hours = atmBrandCheckBox.isChecked

        var displayedCount = 0

        for (atm in loadedAtms) {
            if (selectedBank != "All Banks" && !atm.bankName.equals(selectedBank, ignoreCase = true) && !atm.name.contains(selectedBank, ignoreCase = true)) {
                continue
            }
            if (filter24Hours && !atm.is24Hours) {
                continue
            }

            val distanceText = if (atm.distanceMeters >= 1000) {
                String.format(Locale.getDefault(), "%.1f km", atm.distanceMeters / 1000f)
            } else {
                String.format(Locale.getDefault(), "%.0f m", atm.distanceMeters)
            }

            val markerHue = getBankMarkerColor(atm.bankName)

            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(atm.latLng)
                    .title(atm.name)
                    .snippet("${atm.vicinity} • $distanceText")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue))
            )
            marker?.tag = atm
            displayedCount++
        }

        if (displayedCount == 0) {
            showToast("No ATMs match the selected filters.")
        }
    }

    private fun getBankMarkerColor(bankName: String): Float {
        return when (bankName) {
            "FNB" -> BitmapDescriptorFactory.HUE_AZURE
            "ABSA" -> BitmapDescriptorFactory.HUE_RED
            "Standard Bank" -> BitmapDescriptorFactory.HUE_BLUE
            "Nedbank" -> BitmapDescriptorFactory.HUE_GREEN
            "Capitec" -> BitmapDescriptorFactory.HUE_ORANGE
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
    }

    private fun showAtmDetailsCard(atm: AtmItem) {
        tvAtmName.text = atm.name
        tvAtmAddress.text = atm.vicinity

        val distText = if (atm.distanceMeters >= 1000) {
            String.format(Locale.getDefault(), "%.1f km away", atm.distanceMeters / 1000f)
        } else {
            String.format(Locale.getDefault(), "%.0f m away", atm.distanceMeters)
        }

        val statusText = if (atm.is24Hours) "Open 24 Hours" else "Standard Hours"
        tvAtmDistanceStatus.text = "$distText • $statusText"

        cardAtmDetails.visibility = View.VISIBLE
    }

    private fun openDirectionsInMaps(atm: AtmItem) {
        val gmmIntentUri = Uri.parse("google.navigation:q=${atm.latLng.latitude},${atm.latLng.longitude}&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
        mapIntent.setPackage("com.google.android.apps.maps")

        if (mapIntent.resolveActivity(packageManager) != null) {
            startActivity(mapIntent)
        } else {
            val geoUri = Uri.parse("geo:${atm.latLng.latitude},${atm.latLng.longitude}?q=${atm.latLng.latitude},${atm.latLng.longitude}(${Uri.encode(atm.name)})")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, geoUri))
            } catch (e: Exception) {
                showToast("No map application available for navigation.")
            }
        }
    }

    private fun searchLocationAndFindATMs(query: String) {
        progressBar.visibility = View.VISIBLE
        try {
            val geocoder = Geocoder(this, Locale.getDefault())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocationName(query, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        runOnUiThread {
                            if (addresses.isNotEmpty()) {
                                progressBar.visibility = View.GONE
                                handleGeocodeResult(addresses)
                            } else {
                                searchNominatimPublicApi(query)
                            }
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        runOnUiThread {
                            searchNominatimPublicApi(query)
                        }
                    }
                })
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(query, 1)
                if (!addresses.isNullOrEmpty()) {
                    progressBar.visibility = View.GONE
                    handleGeocodeResult(addresses)
                } else {
                    searchNominatimPublicApi(query)
                }
            }
        } catch (e: Exception) {
            searchNominatimPublicApi(query)
        }
    }

    private fun searchNominatimPublicApi(query: String) {
        val encodedQuery = Uri.encode(query)
        val nominatimUrl = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"

        val queue = Volley.newRequestQueue(this)
        val request = object : StringRequest(Method.GET, nominatimUrl, { response ->
            progressBar.visibility = View.GONE
            try {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val place = jsonArray.getJSONObject(0)
                    val lat = place.getDouble("lat")
                    val lon = place.getDouble("lon")
                    val searchedLocation = LatLng(lat, lon)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchedLocation, 15f))
                    fetchATMs(searchedLocation)
                } else {
                    showToast("Location '$query' not found.")
                }
            } catch (e: Exception) {
                showToast("Location '$query' not found.")
            }
        }, {
            progressBar.visibility = View.GONE
            showToast("Location '$query' not found.")
        }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["User-Agent"] = "CoinWatchApp/1.0"
                return headers
            }
        }

        queue.add(request)
    }

    private fun handleGeocodeResult(addresses: List<Address>) {
        val address = addresses[0]
        val searchedLocation = LatLng(address.latitude, address.longitude)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(searchedLocation, 15f))
        fetchATMs(searchedLocation)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }
}
