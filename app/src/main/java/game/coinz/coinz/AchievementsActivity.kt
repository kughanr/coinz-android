package game.coinz.coinz

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_achievements.*


class AchievementsActivity : AppCompatActivity() {

    private val tag : String = "Achievements"

    private val g : Globals = Globals.getInstance()

    //map of all the achievements and the corresponding boolean
    private var achievements : Map<String, Boolean> = mapOf(
            "Earned 5000 Gold" to g.earned5000Gold ,
            "Transferred 25 Coins" to  g.transferred25Coins,
           "Collected 50 Coins" to  g.collected50Coins
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(g.theme[g.themeKey]!!)
        setContentView(R.layout.activity_achievements)

        //button to profile page
        achievementToProfile.setOnClickListener {
            finish()
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        Log.d(tag, "Achievements ${achievements.toList()}")

        //loop through the achievement map, to decided which list(Achieved/Unachieved) the achievement should go to
        for ((key,value) in achievements) {
            Log.d(tag, "Achievements $key")
            if(value){
                var dummy : String = achievedList.text as String
                dummy += key + "\n"
                achievedList.text = dummy
            } else {
                var dummy : String = unachievedList.text as String
                dummy += key + "\n"
                unachievedList.text = dummy
            }
        }
    }
}
