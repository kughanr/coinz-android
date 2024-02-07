package game.coinz.coinz

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import kotlinx.android.synthetic.main.activity_profile.*
import org.jetbrains.anko.toast
import org.json.JSONObject

class ProfileActivity : AppCompatActivity() {

    private val tag = "Profile"

    private lateinit var user: FirebaseUser
    private var g: Globals = Globals.getInstance()


    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(g.theme[g.themeKey]!!)
        setContentView(R.layout.activity_profile)

        //error check to ensure there is a user logged in
        if (g.firebase?.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            user = g.firebase?.currentUser!!
            userID.text = g.firebase?.currentUser?.email
        }

        //change input font color if dark theme
        if (g.themeKey == "dark") {
            dolr_deposit.setTextColor(Color.WHITE)
            shil_deposit.setTextColor(Color.WHITE)
            peny_deposit.setTextColor(Color.WHITE)
            quid_deposit.setTextColor(Color.WHITE)
            receiverID.setTextColor(Color.WHITE)
        }

        //initialise all the TextViews
        stepsWalkedID.text = "${g.stepsWalked} steps walked"
        wallet.text = "Wallet: ${g.numberOfCoinsInWallet}/${g.walletSize} coins (SpareChange) "
        bankBalance.text = "Bank balance:  ${"%.2f".format(g.bankBalance)} gold"
        dolr_balance.text = "DOLR: ${g.numberOfDolrsInWallet} (${g.numberOfDolrsInSpareChange})"
        shil_balance.text = "SHIL: ${g.numberOfShilsInWallet} (${g.numberOfShilsInSpareChange})"
        quid_balance.text = "QUID ${g.numberOfQuidsInWallet} (${g.numberOfQuidsInSpareChange})"
        peny_balance.text = "PENY: ${g.numberOfPenysInWallet} (${g.numberOfPenysInSpareChange})"
        depositedTodayText.text = "Deposited today: ${g.numberOfCoinsDepositedToday}"
        exchangeRates.text = "Exhange rates:" +
                "\nSHIL: ${"%.3f".format(g.shilExchangeRate)}  DOLR: ${"%.3f".format(g.dolrExchangeRate)} " +
                "\nQUID: ${"%.3f".format(g.quidExchangeRate)}  PENY: ${"%.3f".format(g.penyExchangeRate)}"

        signOut.setOnClickListener {
            signOut()
        }

        toMapButton.setOnClickListener {
            finish()
            startActivity(Intent(this, MainActivity::class.java))
        }

        achievementsButton.setOnClickListener {
            finish()
            startActivity(Intent(this, AchievementsActivity::class.java))
        }

        depositButton.setOnClickListener {
            depositInBank()
            finish()
            startActivity(intent)
        }

        transferButton.setOnClickListener {
            if (receiverID.text.isNotEmpty() && receiverID.text.isNotBlank()) { //ensure there is a receiver ID
                messageCoins(receiverID.text.toString())
            } else {
                toast("Please input a receiver id")
            }
            finish()
            startActivity(intent)
        }

        //theme buttons
        greenThemeButton.setOnClickListener {
            Log.d(tag, "green theme activated")
            g.themeKey = "green"
            finish()
            startActivity(intent)
        }

        darkThemeButton.setOnClickListener {
            Log.d(tag, "dark theme activated")
            g.themeKey = "dark"
            finish()
            startActivity(intent)
        }

        defaultThemeButton.setOnClickListener {
            Log.d(tag, "dark theme activated")
            g.themeKey = "default"
            finish()
            startActivity(intent)
        }


    }

    //sign out function
    private fun signOut() {
        val login = Intent(this, LoginActivity::class.java)
        g.firebase?.signOut()
        Log.d(tag, "Sign out success")
        finish()
        startActivity(login)

    }

    //functions to deposit coins
    //first step, is just checking if the user is not trying to deposit more coins than in the wallet
    //at the end updates database
    private fun depositInBank() {
        if (peny_deposit.text.isNotBlank() && peny_deposit.text.isNotEmpty()) {
            val penyDeposit = peny_deposit.text.toString().toInt()
            if (g.numberOfPenysInWallet >= penyDeposit) {
                depositCoins(penyDeposit, "PENY")

            } else {
                toast("Insufficient Pennies in wallet")
            }
        }
        if (quid_deposit.text.isNotBlank() && quid_deposit.text.isNotEmpty()) {
            val quidDeposit = quid_deposit.text.toString().toInt()
            if (g.numberOfQuidsInWallet >= quidDeposit) {
                depositCoins(quidDeposit, "QUID")

            } else {
                toast("Insufficient QUID in wallet")
            }

        }
        if (shil_deposit.text.isNotBlank() && shil_deposit.text.isNotEmpty()) {
            val shilDeposit = shil_deposit.text.toString().toInt()
            if (g.numberOfShilsInWallet >= shilDeposit) {
                depositCoins(shilDeposit, "SHIL")
            } else {
                toast("Insufficient Shilling in wallet")
            }
        }
        if (dolr_deposit.text.isNotBlank() && dolr_deposit.text.isNotEmpty()) {
            val dolrDeposit = dolr_deposit.text.toString().toInt()
            if (g.numberOfDolrsInWallet >= dolrDeposit) {
                depositCoins(dolrDeposit, "DOLR")
            } else {
                toast("Insufficient Dollar in wallet")
            }
        }

        Log.d(tag, "balances, ${peny_deposit.text}, ${quid_deposit.text}, ${shil_deposit.text}, ${dolr_deposit.text}")

        g.updateFirestore()
    }

    //second step deals with updating the bankBalance and removing the coin from the wallet
    //also deals with the earned 5000 gold achievement
    //deals with going to function that adds coins to spare change
    private fun depositCoins(amount: Int, currency: String): Boolean {
        Log.d(tag, "depositCoins ${g.numberOfCoinsDepositedToday} $amount")
        //if all the coins being deposited can fit in the wallet
        if (g.numberOfCoinsDepositedToday + amount <= 25) {
            //update appropriate values
            g.numberOfCoinsDepositedToday += amount
            g.numberOfCoinsDeposited += amount
            g.numberOfCoinsInWallet -= amount
            var j = 0

            //loop through wallet from the end, remove the coin from the wallet
            //then update bankBalance and decrement the number of coins of the coin's currency
            loop@ for (i in g.wallet?.getJSONArray("coins")!!.length() - 1 downTo 0) {
                Log.d(tag, "deposit coins for $j")
                if (j >= amount) {
                    break@loop
                }
                val coin = g.wallet?.getJSONArray("coins")!!.get(i) as JSONObject
                val value = coin.get("value") as Double
                if (coin.get("currency") == currency) {
                    g.wallet?.getJSONArray("coins")!!.remove(i)
                    j++
                    if (currency == "DOLR") {
                        g.numberOfDolrsInWallet--
                        g.bankBalance += value * g.dolrExchangeRate
                        Log.d(tag, "deposit coins dolr $j $amount")
                    }
                    if (currency == "PENY") {
                        g.numberOfPenysInWallet--
                        g.bankBalance += value * g.penyExchangeRate
                        Log.d(tag, "deposit coins peny $j $amount")
                    }
                    if (currency == "SHIL") {
                        Log.d(tag, "deposit coin  ${g.bankBalance}, $value, ${g.shilExchangeRate} ${g.wallet},")
                        g.numberOfShilsInWallet--
                        g.bankBalance += value * g.shilExchangeRate
                        Log.d(tag, "deposit coins shil $j $amount")
                    }
                    if (currency == "QUID") {
                        g.numberOfQuidsInWallet--
                        g.bankBalance += value * g.quidExchangeRate
                        Log.d(tag, "deposit coins quid $j $amount")
                    }
                }
            }
            if (g.bankBalance > 5000 && !g.earned5000Gold) { //achievement handling
                g.earned5000Gold = true
                g.walletSize += 5
                toast("You have earned an achievement (Earn 5000 Gold), your wallet has grown!")
            }
            return true
            //if some or all coins need to go to spare change
        } else {
            Log.d(tag, "depositing spare change")
            var spareChangeAmount = amount
            //we haven't reached the daily deposit cap
            if (g.numberOfCoinsDepositedToday < 25) {
                spareChangeAmount = g.numberOfCoinsDepositedToday + amount - 25
                val amnt = amount - spareChangeAmount
                depositCoins(amnt, currency) //deposit till we hit daily cap
                // reached daily deposit cap
            }
            depositSpareChange(spareChangeAmount, currency) //coins above the cap go to spare change


        }
        return false
    }

    //function that removes the coin from the wallet and puts in spare change
    private fun depositSpareChange(amount: Int, currency: String) {
        g.numberOfCoinsInWallet -= amount

        var j = 0
        Log.d(tag, "deposit coins outside spare change $j")

        loop@ for (i in g.wallet?.getJSONArray("coins")!!.length() - 1 downTo 0) {
            if (j >= amount) {
                break@loop
            }
            val coin = g.wallet?.getJSONArray("coins")!!.get(i) as JSONObject
            val value = coin.get("value") as Double
            if (coin.get("currency") == currency) {
                g.spareChange.getJSONArray("coins").put(coin)
                g.wallet?.getJSONArray("coins")!!.remove(i)
                j++
                if (currency == "DOLR") {
                    g.numberOfDolrsInWallet--
                    g.numberOfDolrsInSpareChange++
                    Log.d(tag, "deposit spare change dolr $j $amount")
                }
                if (currency == "PENY") {
                    g.numberOfPenysInWallet--
                    g.numberOfPenysInSpareChange++
                    Log.d(tag, "deposit spare change peny $j $amount")
                }
                if (currency == "SHIL") {
                    Log.d(tag, "deposit spare change  ${g.bankBalance}, $value, ${g.shilExchangeRate} ${g.wallet},")
                    g.numberOfShilsInWallet--
                    g.numberOfShilsInSpareChange++
                    Log.d(tag, "deposit spare change shil $j $amount")
                }
                if (currency == "QUID") {
                    g.numberOfQuidsInWallet--
                    g.numberOfQuidsInSpareChange++
                    Log.d(tag, "deposit spare change quid $j $amount ")
                }
            }
        }
    }

    //first step of messaging coins, handles errors (self-messaging, user not found)
    private fun messageCoins(id: String) {
        if (id != g.firebase?.currentUser?.email) {
            val receiver = g.firestore?.collection("users")?.document(id)
            Log.d(tag, "messageCoins ${receiver.toString()}")
            var currentBankBalance = 126.0 //random test value
            receiver?.get()
                    ?.addOnCompleteListener {
                        if (it.isSuccessful) {
                            Log.d(tag, "messageCoins user found, $currentBankBalance")
                            val docRef = it.result
                            if (docRef != null && docRef.exists()) {
                                currentBankBalance = docRef.get("bankBalance").toString().toDouble()     //get the current bank balance of receiver
                                transferCoins(currentBankBalance, receiver) //transfer the coins to the user and updates relevant fields
                                Log.d(tag, "messageCoins document found, $currentBankBalance")
                            } else {
                                Log.d(tag, "messageCoins document not found ${docRef.toString()}")
                                toast("Sorry, user not found, ensure you are entering the correct user ID")
                            }
                        } else {
                            toast("Sorry, user not found, ensure you are entering the correct user ID")
                            Log.d(tag, "messageCoins user not found")
                        }
                    }
        } else {
            toast("You can't message coins to yourself!")
        }
    }

    //ensures enough coins in spare change to transfer, updates receiver's bank balance amount in database
    private fun transferCoins(bankBalance: Double, receiver: DocumentReference?) {
        var newBankBalance = bankBalance
        if (peny_deposit.text.isNotBlank() && peny_deposit.text.isNotEmpty()) {
            val penyDeposit = peny_deposit.text.toString().toInt()
            if (g.numberOfPenysInSpareChange >= penyDeposit) {
                newBankBalance = removeFromSpareChange("PENY", penyDeposit, bankBalance)
            } else {
                toast("Insufficient Pennies in spare change")
            }
        }

        if (quid_deposit.text.isNotBlank() && quid_deposit.text.isNotEmpty()) {
            val quidDeposit = quid_deposit.text.toString().toInt()
            if (g.numberOfQuidsInSpareChange >= quidDeposit) {
                newBankBalance = removeFromSpareChange("QUID", quidDeposit, bankBalance)
            } else {
                toast("Insufficient Quid in spare change")
            }
        }

        if (shil_deposit.text.isNotBlank() && shil_deposit.text.isNotEmpty()) {
            val shilDeposit = shil_deposit.text.toString().toInt()
            if (g.numberOfShilsInSpareChange > shilDeposit) {
                newBankBalance = removeFromSpareChange("SHIL", shilDeposit, bankBalance)
            } else {
                toast("Insufficient Shilling in spare change")
            }
        }

        if (dolr_deposit.text.isNotBlank() && dolr_deposit.text.isNotEmpty()) {
            val dolrDeposit = dolr_deposit.text.toString().toInt()
            if (g.numberOfDolrsInSpareChange >= dolrDeposit) {
                newBankBalance = removeFromSpareChange("DOLR", dolrDeposit, bankBalance)
            } else {
                toast("Insufficient Dollar in spare change  ")
            }
        }

        toast("You have just sent ${newBankBalance - bankBalance} gold!")
        //updates database
        receiver?.update(
                "bankBalance", newBankBalance
        )?.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d(tag, "messageCoins updated $receiver $newBankBalance")
                toast("Your coins have successfully been transferred")
            } else {
                Log.d(tag, "messageCoins failed update $receiver, ${it.exception}")
            }
        }

    }

    //removes the coin from spare change, starting from most recently added
    //returns the receiver's new bank balance amount
    //handles the transferred 25 coins achievement
    private fun removeFromSpareChange(currency: String, amount: Int, bankBalance: Double): Double {
        var j = 0
        var newBankBalance = bankBalance
        g.numberOfCoinsTransferred += amount
        loop@ for (i in g.spareChange.getJSONArray("coins")!!.length() - 1 downTo 0) {
            if (j >= amount) {
                break@loop
            }
            val coin = g.spareChange.getJSONArray("coins")!!.getJSONObject(i)
            if (coin.get("currency") == currency) {
                val value = coin.getDouble("value")
                g.spareChange.getJSONArray("coins")!!.remove(i)
                j++
                if (currency == "DOLR") {
                    newBankBalance += value * g.dolrExchangeRate
                    g.numberOfDolrsInSpareChange--
                }
                if (currency == "PENY") {
                    newBankBalance += value * g.penyExchangeRate
                    g.numberOfPenysInSpareChange--
                }
                if (currency == "SHIL") {
                    newBankBalance += value * g.shilExchangeRate
                    g.numberOfShilsInSpareChange--
                }
                if (currency == "QUID") {
                    newBankBalance += value * g.quidExchangeRate
                    g.numberOfQuidsInSpareChange--
                }
            }
        }
        if (g.numberOfCoinsTransferred >= 25) { //handling achievement
            if (!g.transferred25Coins) {
                g.transferred25Coins = true
                g.walletSize += 5
                toast("You have earned an achievement (Transfer 25 Coins), your wallet has grown!")
            }
        } else {
            g.transferred25Coins = false
        }
        g.updateFirestore()
        return newBankBalance
    }

    override fun onStop() {
        super.onStop()
        g.updateFirestore() //update database when stopping activity, ensuring everything is up to date including steps walked
    }
}
