package com.vibezdating.app

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth

private val Plum = Color(0xFF5D236E)
private val Pink = Color(0xFFFF5A8A)
private val Ink = Color(0xFF25152D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VibezDatingApp() }
    }
}

@Composable
fun VibezDatingApp() {
    var isRegister by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var signedInEmail by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.email.orEmpty()) }

    MaterialTheme(colorScheme = lightColorScheme(primary = Plum, secondary = Pink)) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFFFEEF4), Color(0xFFF5E9FF), Color.White))
            ).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (signedInEmail.isNotBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(64.dp))
                    Text("You're in the right place.", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(Modifier.height(8.dp))
                    Text("Signed in as $signedInEmail", color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        signedInEmail = ""
                        message = "You have signed out."
                    }, colors = ButtonDefaults.buttonColors(containerColor = Plum), modifier = Modifier.fillMaxWidth()) {
                        Text("Sign out")
                    }
                    Text("More dating features are coming next.", color = Plum, modifier = Modifier.padding(top = 18.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.85f), shadowElevation = 10.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(shape = RoundedCornerShape(22.dp), color = Pink.copy(alpha = 0.12f)) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = Pink, modifier = Modifier.padding(16.dp).size(38.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Vibez Dating", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
                            Text("Real connections. Good vibes.", color = Ink, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(26.dp))
                            Text(if (isRegister) "Create your account" else "Welcome back", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = email, onValueChange = { email = it },
                                label = { Text("Email address") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = password, onValueChange = { password = it },
                                label = { Text("Password (6+ characters)") }, singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (message.isNotBlank()) {
                                Text(message, color = if (message.startsWith("Success")) Color(0xFF167A45) else Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                enabled = !busy,
                                onClick = {
                                    val cleanEmail = email.trim()
                                    if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                        message = "Enter a valid email address."
                                    } else if (password.length < 6) {
                                        message = "Password must be at least 6 characters."
                                    } else {
                                        busy = true
                                        message = ""
                                        val auth = FirebaseAuth.getInstance()
                                        val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password) else auth.signInWithEmailAndPassword(cleanEmail, password)
                                        task.addOnCompleteListener { result ->
                                            busy = false
                                            if (result.isSuccessful) {
                                                signedInEmail = result.result?.user?.email.orEmpty()
                                                message = "Success"
                                            } else {
                                                message = result.exception?.localizedMessage ?: "Sign-in failed. Please try again."
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Plum),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                else Text(if (isRegister) "Create account" else "Log in", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { isRegister = !isRegister; message = "" }) {
                                Text(if (isRegister) "Already have an account? Log in" else "New to Vibez? Create account", color = Plum)
                            }
                        }
                    }
                    Text("Please be respectful. Block and report tools will be added before launch.", color = Plum, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }
    }
}
