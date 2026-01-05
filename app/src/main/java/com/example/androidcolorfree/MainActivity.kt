package com.jzhuang.colorfree

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random
import com.google.android.gms.common.api.ApiException // Import ApiException

const val PREFS_NAME = "AppPrefs"
const val IS_PRO_KEY = "is_pro_user"

class MainActivity : ComponentActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firebaseAuth: FirebaseAuth

    private val googleSignInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { firebaseAuthWithGoogle(it) }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign in failed: \${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Signed in as \${firebaseAuth.currentUser?.displayName}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Firebase auth failed: \${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        // Handle notification permission result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MobileAds.initialize(this) {}
        loadInterstitialAd()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
        firebaseAuth = FirebaseAuth.getInstance()

        val serviceIntent = Intent(this, ColorTimerService::class.java)
        startService(serviceIntent)

        askForNotificationPermission()

        setContent {
            AndroidColorFreeTheme {
                AppNavigation(
                    onSignInGoogle = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                    onSignOut = { 
                        firebaseAuth.signOut()
                        googleSignInClient.signOut()
                        Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                    },
                    showInterstitialAd = { showAdAndGrantColor() }
                )
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, "ca-app-pub-3940256099942544/1033173712", adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
                Toast.makeText(this@MainActivity, "Ad failed to load: \${adError.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
            }
        })
    }

    private fun showAdAndGrantColor() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // Called when ad is dismissed. Grant color and load a new ad.
                    val intent = Intent(this@MainActivity, ColorTimerService::class.java).apply {
                        action = ColorTimerService.ACTION_START_TIMER
                        putExtra(ColorTimerService.EXTRA_DURATION_MS, 5 * 60 * 1000L) 
                    }
                    startService(intent)
                    loadInterstitialAd()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Toast.makeText(this@MainActivity, "Ad failed to show: \${adError.message}", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@MainActivity, ColorTimerService::class.java).apply {
                        action = ColorTimerService.ACTION_START_TIMER
                        putExtra(ColorTimerService.EXTRA_DURATION_MS, 5 * 60 * 1000L)
                    }
                    startService(intent)
                    loadInterstitialAd()
                }
            }
            mInterstitialAd?.show(this)
        } else {
            Toast.makeText(this, "Ad not ready, granting color.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, ColorTimerService::class.java).apply {
                action = ColorTimerService.ACTION_START_TIMER
                putExtra(ColorTimerService.EXTRA_DURATION_MS, 5 * 60 * 1000L)
            }
            startService(intent)
            loadInterstitialAd()
        }
    }
}

// --- Helper Functions for Pro Status (now top-level) ---
fun getIsProUser(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean(IS_PRO_KEY, false)
}

fun setIsProUser(context: Context, isPro: Boolean) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean(IS_PRO_KEY, isPro).apply()
}

// --- Navigation & State ---

@Composable
fun AppNavigation(
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    showInterstitialAd: () -> Unit
) {
    val context = LocalContext.current
    var hasAdbPermission by remember { mutableStateOf(canWriteSecureSettings(context)) }
    val timerState by ColorTimerService.timerState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val isProUser by remember { mutableStateOf(getIsProUser(context)) } // Corrected call

    var currentScreen by remember { mutableStateOf("Home") } // Home, Puzzle, Admin, Social
    var selectedDuration by remember { mutableStateOf(0L) }
    var puzzleDifficulty by remember { mutableStateOf(1) }

    if (!hasAdbPermission) {
        AdbPermissionScreen { hasAdbPermission = canWriteSecureSettings(context) }
    } else {
        when (timerState) {
            is ColorTimerService.TimerState.Running -> {
                TimerActiveScreen((timerState as ColorTimerService.TimerState.Running).timeLeftMs)
            }
            is ColorTimerService.TimerState.Idle -> {
                when (currentScreen) {
                    "Home" -> HomeScreen(
                        onUnlockRequest = { duration, difficulty ->
                            selectedDuration = duration
                            puzzleDifficulty = difficulty
                            currentScreen = "Puzzle"
                        },
                        onNavigateToAdmin = { currentScreen = "Admin" },
                        onNavigateToSocial = { currentScreen = "Social" }, // Placeholder
                        isProUser = isProUser,
                        onTogglePro = { 
                            setIsProUser(context, !isProUser) // Corrected call
                            // To ensure UI updates, force recomposition by changing screen or triggering state change.
                            // For simplicity, we can navigate back to home.
                            currentScreen = "Home"
                        },
                        currentUser = currentUser,
                        showInterstitialAd = showInterstitialAd
                    )
                    "Puzzle" -> PuzzleScreen(
                        difficulty = puzzleDifficulty,
                        onSuccess = {
                            val intent = Intent(context, ColorTimerService::class.java).apply {
                                action = ColorTimerService.ACTION_START_TIMER
                                putExtra(ColorTimerService.EXTRA_DURATION_MS, selectedDuration)
                            }
                            context.startService(intent)
                            currentScreen = "Home"
                        },
                        onCancel = { currentScreen = "Home" }
                    )
                    "Admin" -> AdminActivationScreen { currentScreen = "Home" }
                    // Placeholder Social Screen
                    "Social" -> SocialScreen(
                        currentUser = currentUser,
                        isProUser = isProUser,
                        onSignInGoogle = onSignInGoogle,
                        onSignOut = onSignOut,
                        onNavigateBack = { currentScreen = "Home" }
                    )
                }
            }
        }
    }
}

// --- Screens ---

@Composable
fun TimerActiveScreen(timeLeftMs: Long) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val minutes = (timeLeftMs / 1000) / 60
        val seconds = (timeLeftMs / 1000) % 60
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        Text("Color Timer Running", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(timeString, style = MaterialTheme.typography.displayLarge)
        Spacer(modifier = Modifier.weight(1f))
        DisclaimerCard("Developed and Designed by Jackson Zhuang") // Added credits
    }
}

@Composable
fun AdbPermissionScreen(onCheckClicked: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup Required", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Run this command on your computer:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                // UPDATED: adb command with new package name
                Text("adb shell pm grant com.jzhuang.androidcolorfree android.permission.WRITE_SECURE_SETTINGS")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCheckClicked) { Text("I Ran The Command") }
    }
}

@Composable
fun AdminActivationScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Absolute Color Freedom", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Activating this optional feature will make the app a 'Device Administrator'. This makes it much harder to uninstall, creating a strong commitment to the grayscale lifestyle.",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = {
            val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Activating this is required to prevent easy uninstallation and enforce the grayscale rule.")
            }
            context.startActivity(intent)
        }) { Text("Open Activation Screen") }
        Spacer(modifier = Modifier.height(24.dp))
        DisclaimerCard("Developed and Designed by Jackson Zhuang") // Added credits
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onNavigateBack) { Text("Back to Home") }
    }
}

@Composable
fun HomeScreen(
    onUnlockRequest: (Long, Int) -> Unit,
    onNavigateToAdmin: () -> Unit,
    onNavigateToSocial: () -> Unit,
    isProUser: Boolean,
    onTogglePro: () -> Unit,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    showInterstitialAd: () -> Unit
) {
    var showDurationDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isAdmin by remember(isDeviceAdmin(context)) { mutableStateOf(isDeviceAdmin(context)) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Grayscale is Active", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { showDurationDialog = true }) { Text("Request Color Timer") }
        Spacer(modifier = Modifier.height(16.dp))

        if (!isAdmin) {
            OutlinedButton(onClick = onNavigateToAdmin) { Text("Absolute Color Freedom") }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Monetization and Social Features Section
        Divider(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        Text("Monetization & Social Features", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onTogglePro) { 
            Text(if (isProUser) "Deactivate Pro Mode (Test)" else "Activate Pro Mode (Test)") 
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(onClick = onNavigateToSocial, enabled = isProUser && currentUser != null) { 
            Text("Social Features (Pro & Logged In)")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (currentUser == null) {
            Text("Sign in to enable social features", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.weight(1f))
        DisclaimerCard("Developed and Designed by Jackson Zhuang")
    }

    if (showDurationDialog) {
        AlertDialog(
            onDismissRequest = { showDurationDialog = false },
            title = { Text("Choose Duration") },
            text = {
                Column {
                    DurationOption("5 Minutes", if (isProUser) "Easy Puzzle" else "Watch Ad") { 
                        if (isProUser) {
                            onUnlockRequest(5 * 60 * 1000L, 1)
                        } else {
                            showInterstitialAd()
                        }
                        showDurationDialog = false 
                    }
                    DurationOption("15 Minutes", if (isProUser) "Medium Puzzle" else "Watch Ad") { 
                        if (isProUser) {
                            onUnlockRequest(15 * 60 * 1000L, 2)
                        } else {
                            showInterstitialAd()
                        }
                        showDurationDialog = false
                    }
                    DurationOption("30 Minutes", if (isProUser) "Hard Puzzle" else "Watch Ad") { 
                        if (isProUser) {
                            onUnlockRequest(30 * 60 * 1000L, 3)
                        } else {
                            showInterstitialAd()
                        }
                        showDurationDialog = false
                    }
                    if (isProUser) {
                         DurationOption("1 Hour", "Hard Puzzle") { 
                            onUnlockRequest(60 * 60 * 1000L, 3)
                            showDurationDialog = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDurationDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SocialScreen(
    currentUser: com.google.firebase.auth.FirebaseUser?,
    isProUser: Boolean,
    onSignInGoogle: () -> Unit,
    onSignOut: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var colorlessTime by remember { mutableStateOf("Loading...") }
    var puzzlesSolved by remember { mutableStateOf("Loading...") }

    LaunchedEffect(currentUser, isProUser) {
        if (isProUser && currentUser != null) {
            // Placeholder for fetching user stats
            // In a real app, you would fetch from Firestore here.
            colorlessTime = "123h 45m" // Mock data
            puzzlesSolved = "789" // Mock data
        } else {
            colorlessTime = "N/A"
            puzzlesSolved = "N/A"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Social Features", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (currentUser == null) {
            Text("Login to access social features.")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSignInGoogle) { Text("Sign In with Google") }
        } else {
            Text("Welcome, \${currentUser.displayName ?: currentUser.email}", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(8.dp))
            if (isProUser) {
                Text("You are a Pro user!", color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your Stats:", style = MaterialTheme.typography.headlineSmall)
                Text("Colorless Time: \${colorlessTime}")
                Text("Puzzles Solved: \${puzzlesSolved}")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Leaderboard (Coming Soon)", textAlign = TextAlign.Center)
            } else {
                Text("Upgrade to Pro to see leaderboards and user histories.")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onSignOut) { Text("Sign Out") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onNavigateBack) { Text("Back to Home") }
    }
}

@Composable
fun PuzzleScreen(difficulty: Int, onSuccess: () -> Unit, onCancel: () -> Unit) {
    if (difficulty == 0) {
        LaunchedEffect(Unit) { onSuccess() }
        return
    }
    val puzzle = remember(difficulty) { generatePuzzle(difficulty) }
    var answer by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Solve to Unlock Color", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Text(puzzle.question, style = MaterialTheme.typography.displayMedium)
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = answer, onValueChange = { answer = it; isError = false }, label = { Text("Answer") }, singleLine = true, isError = isError)
        Spacer(modifier = Modifier.height(24.dp))
        Row {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { if (answer == puzzle.answer) onSuccess() else isError = true }) { Text("Submit") }
        }
    }
}

@Composable
fun DurationOption(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun DisclaimerCard(credits: String = "") {
    val context = LocalContext.current
    val isAdmin by remember(isDeviceAdmin(context)) { mutableStateOf(isDeviceAdmin(context)) }
    val baseText = if (isAdmin) {
        "This app is a device administrator and cannot be uninstalled normally. To remove it, you must first go to Settings > Security > Device Admin Apps and deactivate it."
    } else {
        "If you uninstall this app, grayscale mode will remain active. You can disable it in Settings > Accessibility > Color and motion > Color correction."
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = baseText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            if (credits.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = credits, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

data class Puzzle(val question: String, val answer: String)
fun generatePuzzle(difficulty: Int): Puzzle {
    val r = Random.Default
    return when (difficulty) {
        1 -> { val a = r.nextInt(11, 25); val b = r.nextInt(6, 15); val c = r.nextInt(1, 20); Puzzle("($a * $b) - $c = ?", ((a * b) - c).toString()) }
        2 -> { val a = r.nextInt(15, 35); val b = r.nextInt(5, 12); val c = r.nextInt(10, 25); Puzzle("($a * $b) + ($a - $c) = ?", ((a * b) + (a - c)).toString()) }
        else -> { val a = r.nextInt(25, 50); val b = r.nextInt(15, 30); val c = r.nextInt(5, 12); val product = a * b; val remainder = product % c; val adjustedProduct = product - remainder; Puzzle("($adjustedProduct / $c) + $a = ?", ((adjustedProduct / c) + a).toString()) }
    }
}


// Theme function was here

@Composable
fun AndroidColorFreeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(),
        content = content
    )
}



fun isDeviceAdmin(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val componentName = ComponentName(context, DeviceAdminReceiver::class.java)
    return dpm.isAdminActive(componentName)
}

fun canWriteSecureSettings(context: Context): Boolean {
    return context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
