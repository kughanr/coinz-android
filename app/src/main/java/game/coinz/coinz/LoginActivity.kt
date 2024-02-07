package game.coinz.coinz

/*import android.view.View
import android.widget.Button
import android.widget.Toast*/
//import java.io.IOException
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.android.synthetic.main.activity_login2.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast


class LoginActivity : AppCompatActivity(){


    private val tag = "LoginActivity"
    private val accountCreated : String = "Your account has been created, you can now sign in"
    private val failAccountCreation : String = "An error occurred while trying to create your account"
    private val g : Globals = Globals.getInstance()
    private var dummyList : List<String> = listOf()




    private lateinit var mAuth: FirebaseAuth
    private var firestore: FirebaseFirestore? = null
    //private var firestoreUser: DocumentReference?=null

    private var currentUser: FirebaseUser?=null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login2)
        mAuth = FirebaseAuth.getInstance()
        g.firebase = mAuth
        currentUser = g.firebase?.currentUser //set to current user
        firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build()
        firestore?.firestoreSettings = settings

        g.firestore = firestore

        Log.d(tag, "firestore ${g.firestore}")

        if (currentUser == null) { //if no user is logged in then listen for login and create account
            loginButton.setOnClickListener {
                if(emailID.text.toString() != "" && passwordID.text.toString()!="") { //not empty
                    signIn(emailID.text.toString(), passwordID.text.toString())
                } else {
                    toast("You must enter an email and password to sign in")
                }
                Log.d(tag, "EMAIL ID UPDATE ${emailID.text}")
            }

            createAccButton.setOnClickListener {
                if(emailID.text.toString() != "" && passwordID.text.toString()!="") { //not empty
                    createAccount(emailID.text.toString(), passwordID.text.toString())
                } else {
                    toast("You must enter a username and password")
                }
                Log.d(tag, "PASSWORD ID UPDATE ${passwordID.text}")
            }

            Log.d(tag, "EMAIL ID ${emailID.text}")
        } else { //if there is a user logged in, start the loading screen
            g.firestoreUser = firestore?.collection("users")?.document(currentUser!!.uid)
            Log.d(tag, "user is signed in $currentUser ${g.firestoreUser}")
            Log.d(tag, "user")
            startActivity(Intent(this, LoadingScreen::class.java)) //Go to loading screen on successful sign in
        }



    }

    override fun onStart() {
        super.onStart()
        currentUser = mAuth.currentUser
        if (currentUser != null){ //if user is logged in go to loading screen
            val loadingScreen = Intent(this, LoadingScreen::class.java)
            startActivity(loadingScreen)
        }
    }


    private fun createAccount(email : String, password : String) {
        toast("Your account is being created, please wait a few moments")
            g.firebase?.createUserWithEmailAndPassword(email, password)
                    ?.addOnCompleteListener{ task ->
                        if(task.isSuccessful){ //successful user creation
                            Log.d(tag, "User created, $email, $password $task")
                            toast("Setting up user database")
                            g.firebase?.signInWithEmailAndPassword(email, password) //log user in to extract unique user id from firebase
                                    ?.addOnSuccessListener {

                                        val addNewUserToDatabase = mapOf( //create map for firestore
                                                "email" to email,
                                                "bankBalance" to 0.0,            //bank balance in gold
                                                "numberOfCoinsDeposited" to 0,
                                                "numberOfCoinsCollected" to 0,
                                                "numberOfCoinsTransferred" to 0,
                                                "walletSize" to 10,
                                                "collectedCoins" to dummyList,
                                                "numberOfCoinsDepositedToday" to 0,
                                                "collected50Coins" to false,
                                                "earned5000Gold" to false,
                                                "transferred25Coins" to false,
                                                "wallet" to g.walletString,
                                                "spareChange" to g.walletString,
                                                "stepsWalked" to 0,
                                                "downloadDate" to ""
                                        )

                                        //g.firestoreUser = firestore?.collection("users")?.document(g.firebase?.currentUser!!.uid) //create new document for user with user id
                                        g.firestoreUser = firestore?.collection("users")?.document(email)
                                        g.firestoreUser?.set(addNewUserToDatabase)  //add the fields to the document
                                                ?.addOnSuccessListener {
                                                    longToast(accountCreated) //give message user created
                                                    finish()
                                                    startActivity(intent) //restart activity, to automatically to start the loading screen page
                                                }
                                    }

                        } else { //if user creation fail return error message
                            Log.d(tag, "User creation failed, $email, $password,  ${task.exception.toString()}")
                            longToast(failAccountCreation)
                        }
                    }
    }

    private fun signIn(email : String, password : String){
            g.firebase?.signInWithEmailAndPassword(email, password)
                    ?.addOnCompleteListener{task ->
                        if(task.isSuccessful){
                            Log.d(tag, "sign in successful")
                            g.firestoreUser = firestore?.collection("users")?.document(g.firebase?.currentUser!!.uid)
                            finish()
                            startActivity(intent) //Go to loading screen on successful sign in
                        } else {
                            val msg = "Login unsuccessful, ${task.exception.toString()}"
                            longToast(msg) //sign in fails, print error message
                            Log.d(tag, msg)
                        }
                    }
    }



}
