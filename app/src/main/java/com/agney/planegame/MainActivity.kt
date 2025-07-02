package com.agney.planegame

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

data class Zombie(
    val position: Offset,
    val speed: Float = 2f,
    val size: Float = 100f
)

data class Bullet(
    val position: Offset,
    val speed: Float = 10f,
    val size: Float = 10f
)

data class HighScore(
    val score: Int,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PlaneGame()
        }
    }
}

@Composable
fun PlaneGame() {
    var currentScreen by remember { mutableStateOf("menu") } // menu, game, highScores
    var highScores by remember { mutableStateOf(listOf<HighScore>()) }
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("GamePrefs", Context.MODE_PRIVATE) }

    // Load high scores when the app starts
    LaunchedEffect(Unit) {
        highScores = loadHighScores(sharedPreferences)
    }

    when (currentScreen) {
        "menu" -> StartMenu(
            onStartGame = { currentScreen = "game" },
            onShowHighScores = { currentScreen = "highScores" }
        )
        "game" -> GameScreen(
            onGameOver = { score ->
                saveHighScore(sharedPreferences, score)
                highScores = loadHighScores(sharedPreferences)
                currentScreen = "menu"
            }
        )
        "highScores" -> HighScoreScreen(
            highScores = highScores,
            onBack = { currentScreen = "menu" }
        )
    }
}

@Composable
fun StartMenu(
    onStartGame: () -> Unit,
    onShowHighScores: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Zombie Shooter",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        Button(
            onClick = onStartGame,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "Start Game",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Button(
            onClick = onShowHighScores,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = "High Scores",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun HighScoreScreen(
    highScores: List<HighScore>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "High Scores",
            color = Color.White,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )
        
        if (highScores.isEmpty()) {
            Text(
                text = "No high scores yet!",
                color = Color.White,
                fontSize = 24.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            highScores.forEachIndexed { index, score ->
    Text(
                    text = "${index + 1}. Score: ${score.score} - ${score.date}",
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onBack,
            modifier = Modifier.padding(top = 32.dp)
        ) {
            Text(
                text = "Back to Menu",
                fontSize = 20.sp
            )
        }
    }
}

@Composable
fun GameScreen(onGameOver: (Int) -> Unit) {
    var score by remember { mutableStateOf(0) }
    var gameOver by remember { mutableStateOf(false) }
    var planePosition by remember { mutableStateOf(Offset(0f, 0f)) }
    var zombies by remember { mutableStateOf(listOf<Zombie>()) }
    var bullets by remember { mutableStateOf(listOf<Bullet>()) }
    var moveLeft by remember { mutableStateOf(false) }
    var moveRight by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableStateOf(1f) }
    var gameTime by remember { mutableStateOf(0L) }
    val focusManager = LocalFocusManager.current
    
    val density = LocalDensity.current
    val planeSize = with(density) { 60.dp.toPx() }
    var screenWidth = 500f
    var screenHeight = 1000f
    val image = ImageBitmap.imageResource(id = R.drawable.minecraftzombiesmall)
    val player = ImageBitmap.imageResource(id = R.drawable.player)
    val arrow = ImageBitmap.imageResource(id = R.drawable.arrow)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { keyEvent ->
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            moveLeft = true
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            moveLeft = false
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            moveRight = true
                        } else if (keyEvent.type == KeyEventType.KeyUp) {
                            moveRight = false
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            bullets = bullets + Bullet(position = planePosition)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            onGameOver(score)
                        }
                        true
                    }
                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val screenCenter = size.width / 2
                        moveLeft = offset.x < screenCenter
                        moveRight = offset.x > screenCenter
                        tryAwaitRelease()
                        moveLeft = false
                        moveRight = false
                    },
                    onTap = { offset ->
                        bullets = bullets + Bullet(position = planePosition)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            screenWidth = size.width
            screenHeight = size.height
            
            if (planePosition == Offset.Zero) {
                planePosition = Offset(screenWidth / 2, screenHeight - planeSize * 2)
            }
            
            // Draw plane
            drawImage(
                image = player,
                topLeft = Offset(
                    planePosition.x - planeSize,
                    planePosition.y - planeSize
                )
            )

            // Draw zombies
            zombies.forEach { zombie ->
                drawImage(
                    image = image,
                    topLeft = Offset(
                        zombie.position.x - zombie.size / 2,
                        zombie.position.y - zombie.size / 2
                    )
                )
            }
            
            // Draw bullets
            bullets.forEach { bullet ->
                drawImage(
                    image = arrow,
                    topLeft = Offset(
                        bullet.position.x - bullet.size / 2,
                        bullet.position.y - bullet.size / 2
                    )
                )
            }
        }

        // Draw score and controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Score: $score",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Speed: ${String.format("%.1f", speedMultiplier)}x",
                color = Color.White,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Controls:\n" +
                       "D-pad Left/Right: Move\n" +
                       "OK/Center: Shoot\n" +
                       "Back: Exit\n" +
                       "Touch: Tap left/right to move, tap center to shoot",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    // Update game state
    LaunchedEffect(gameOver) {
        val startTime = System.currentTimeMillis()
        while (!gameOver) {
            delay(16) // ~60 FPS
            
            gameTime = System.currentTimeMillis() - startTime
            speedMultiplier = 1f + (gameTime / 5000f) * 0.1f

            val newX = planePosition.x + when {
                moveLeft -> -15f
                moveRight -> 15f
                else -> 0f
            }

            planePosition = Offset(
                newX.coerceIn(planeSize, screenWidth - planeSize),
                planePosition.y
            )

            bullets = bullets.map { bullet ->
                bullet.copy(position = bullet.position.copy(y = bullet.position.y - bullet.speed))
            }.filter { it.position.y > 0 }

            zombies = zombies.map { zombie ->
                zombie.copy(
                    position = zombie.position.copy(
                        y = zombie.position.y + (zombie.speed * speedMultiplier)
                    )
                )
            }

            zombies.forEach { zombie ->
                if (zombie.position.y >= screenHeight) {
                    gameOver = true
                    onGameOver(score)
                }

                bullets.forEach { bullet ->
                    if (isColliding(zombie, bullet)) {
                        zombies = zombies - zombie
                        bullets = bullets - bullet
                        score += 10
                    }
                }
            }

            if (Random.nextFloat() < 0.02f) {
                zombies = zombies + Zombie(
                    position = Offset(
                        (screenWidth * 0.1f) + (Random.nextFloat() * (screenWidth * 0.8f)),
                        0f
                    )
                )
            }
        }
    }
}

fun saveHighScore(sharedPreferences: SharedPreferences, score: Int) {
    val currentScores = loadHighScores(sharedPreferences).toMutableList()
    val newScore = HighScore(score, java.time.LocalDate.now().toString())
    currentScores.add(newScore)
    currentScores.sortByDescending { it.score }
    val topScores = currentScores.take(10)
    
    sharedPreferences.edit().apply {
        topScores.forEachIndexed { index, highScore ->
            putInt("score_$index", highScore.score)
            putString("date_$index", highScore.date)
        }
        apply()
    }
}

fun loadHighScores(sharedPreferences: SharedPreferences): List<HighScore> {
    val scores = mutableListOf<HighScore>()
    for (i in 0..9) {
        val score = sharedPreferences.getInt("score_$i", 0)
        val date = sharedPreferences.getString("date_$i", "") ?: ""
        if (score > 0) {
            scores.add(HighScore(score, date))
        }
    }
    return scores
}

fun isColliding(obj1: Zombie, obj2: Bullet): Boolean {
    val distance = (obj1.position - obj2.position).getDistance()
    return distance < (obj1.size + obj2.size) / 2
}
