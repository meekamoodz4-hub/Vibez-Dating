package com.vibezdating.app

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import com.google.firebase.firestore.FirebaseFirestore

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
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var interests by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    fun loadProfile() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("profiles").document(user.uid).get()
            .addOnSuccessListener { doc ->
                name = doc.getString("name").orEmpty()
                age = doc.getLong("age")?.toString().orEmpty()
                gender = doc.getString("gender").orEmpty()
                bio = doc.getString("bio").orEmpty()
                interests = doc.getString("interests").orEmpty()
                profileLoaded = true
            }
            .addOnFailureListener {
                profileLoaded = true
                message = "Could not load profile: ${it.localizedMessage ?: "Check your connection and Firestore rules."}"
            }
    }

    LaunchedEffect(signedInEmail) {
        if (signedInEmail.isNotBlank()) loadProfile()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Plum, secondary = Pink)) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFFFEEF4), Color(0xFFF5E9FF), Color.White))
            ).padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (signedInEmail.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(52.dp))
                    Text("Your Vibez Profile", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
                    Text("Signed in as $signedInEmail", color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    if (!profileLoaded) {
                        CircularProgressIndicator(color = Plum)
                        Text("Loading your profile…", color = Plum, modifier = Modifier.padding(top = 8.dp))
                    } else {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Create or update your profile", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Ink)
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Age (18+)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(gender, { gender = it }, label = { Text("Gender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(bio, { bio = it.take(300) }, label = { Text("About me (up to 300 characters)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(interests, { interests = it.take(200) }, label = { Text("Interests (e.g. music, travel)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                                Text("${bio.length}/300 characters", fontSize = 11.sp, color = Plum, modifier = Modifier.align(Alignment.End))
                                if (message.isNotBlank()) {
                                    Text(message, color = if (message.startsWith("Profile saved")) Color(0xFF167A45) else Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
                                }
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        val ageValue = age.toIntOrNull()
                                        if (name.trim().length < 2) message = "Enter a name with at least 2 characters."
                                        else if (ageValue == null || ageValue < 18 || ageValue > 120) message = "Vibez Dating is for adults aged 18 and over."
                                        else if (gender.isBlank()) message = "Please enter your gender."
                                        else {
                                            val user = FirebaseAuth.getInstance().currentUser
                                            if (user == null) message = "Please log in again."
                                            else {
                                                busy = true
                                                message = ""
                                                val data = hashMapOf<String, Any>(
                                                    "uid" to user.uid,
                                                    "email" to (user.email ?: ""),
                                                    "name" to name.trim(),
                                                    "age" to ageValue,
                                                    "gender" to gender.trim(),
                                                    "bio" to bio.trim(),
                                                    "interests" to interests.trim(),
                                                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                                                )
                                                FirebaseFirestore.getInstance().collection("profiles").document(user.uid)
                                                    .set(data)
                                                    .addOnCompleteListener { task ->
                                                        busy = false
                                                        message = if (task.isSuccessful) "Profile saved successfully!" else
                                                            "Save failed: ${task.exception?.localizedMessage ?: "Check Firestore security rules."}"
                                                    }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Plum),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                    else Text("Save my profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = {
                        FirebaseAuth.getInstance().signOut()
                        signedInEmail = ""
                        profileLoaded = false
                        name = ""; age = ""; gender = ""; bio = ""; interests = ""; message = ""
                    }) { Text("Sign out", color = Plum) }
                    Text("Vibez Dating is for adults 18+. More features are coming soon.", color = Plum, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Surface(shape = RoundedCornerShape(28.dp), color = Color.White.copy(alpha = 0.9f), shadowElevation = 10.dp) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(shape = RoundedCornerShape(22.dp), color = Pink.copy(alpha = 0.12f)) {
                                Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.padding(16.dp).size(38.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Vibez Dating", fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
                            Text("Real connections. Good vibes.", color = Ink, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(26.dp))
                            Text(if (isRegister) "Create your account" else "Welcome back", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(email, { email = it }, label = { Text("Email address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(password, { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                            if (message.isNotBlank()) Text(message, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
                            Spacer(Modifier.height(16.dp))
                            Button(
                                enabled = !busy,
                                onClick = {
                                    val cleanEmail = email.trim()
                                    if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) message = "Enter a valid email address."
                                    else if (password.length < 6) message = "Password must be at least 6 characters."
                                    else {
                                        busy = true
                                        message = ""
                                        val auth = FirebaseAuth.getInstance()
                                        val task = if (isRegister) auth.createUserWithEmailAndPassword(cleanEmail, password) else auth.signInWithEmailAndPassword(cleanEmail, password)
                                        task.addOnCompleteListener { result ->
                                            busy = false
                                            if (result.isSuccessful) {
                                                signedInEmail = result.result?.user?.email.orEmpty()
                                                profileLoaded = false
                                                message = ""
                                            } else message = result.exception?.localizedMessage ?: "Sign-in failed. Please try again."
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
                }
            }
        }
    }
}
