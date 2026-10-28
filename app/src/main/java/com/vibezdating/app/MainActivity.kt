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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

private val Plum = Color(0xFF5D236E)
private val Pink = Color(0xFFFF5A8A)
private val Ink = Color(0xFF25152D)

private data class PublicProfile(
    val uid: String,
    val name: String,
    val age: Long,
    val gender: String,
    val bio: String,
    val interests: String
)

private data class MatchItem(val id: String, val otherUid: String, val otherName: String)
private data class ChatLine(val id: String, val senderUid: String, val text: String)

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
    var showDiscover by remember { mutableStateOf(false) }
    var showChats by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<MatchItem?>(null) }
    var matches by remember { mutableStateOf<List<MatchItem>>(emptyList()) }
    var matchesBusy by remember { mutableStateOf(false) }
    var chatLines by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var chatDraft by remember { mutableStateOf("") }
    var chatMessage by remember { mutableStateOf("") }
    var chatBusy by remember { mutableStateOf(false) }
    var discoverBusy by remember { mutableStateOf(false) }
    var discoverMessage by remember { mutableStateOf("") }
    var publicProfiles by remember { mutableStateOf<List<PublicProfile>>(emptyList()) }
    var swipeIndex by remember { mutableIntStateOf(0) }
    var swipeBusy by remember { mutableStateOf(false) }
    var swipeMessage by remember { mutableStateOf("") }

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

    fun loadDiscoverProfiles() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            discoverMessage = "Please log in again."
            return
        }
        discoverBusy = true
        discoverMessage = ""
        val db = FirebaseFirestore.getInstance()
        db.collection("publicProfiles").limit(50).get()
            .addOnSuccessListener { result ->
                val candidates = result.documents.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: return@mapNotNull null
                    if (uid == user.uid) return@mapNotNull null
                    val profileAge = doc.getLong("age") ?: return@mapNotNull null
                    if (profileAge < 18) return@mapNotNull null
                    PublicProfile(uid, doc.getString("name").orEmpty(), profileAge,
                        doc.getString("gender").orEmpty(), doc.getString("bio").orEmpty(),
                        doc.getString("interests").orEmpty())
                }
                db.collection("swipes").whereEqualTo("fromUid", user.uid).get()
                    .addOnSuccessListener { decisions ->
                        val seen = decisions.documents.mapNotNull { it.getString("toUid") }.toSet()
                        publicProfiles = candidates.filter { it.uid !in seen }
                        swipeIndex = 0
                        discoverBusy = false
                        discoverMessage = if (publicProfiles.isEmpty()) {
                            if (candidates.isEmpty()) "No other profiles yet. Invite people to join Vibez Dating."
                            else "You have seen everyone available. Refresh later for new profiles."
                        } else ""
                    }
                    .addOnFailureListener {
                        publicProfiles = candidates
                        swipeIndex = 0
                        discoverBusy = false
                        discoverMessage = "Could not load your previous choices. Check Firestore rules."
                    }
            }
            .addOnFailureListener {
                discoverBusy = false
                discoverMessage = "Could not load profiles. Check your internet connection and Firestore rules."
            }
    }

    fun loadMatches() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        matchesBusy = true
        FirebaseFirestore.getInstance().collection("matches")
            .whereArrayContains("participants", user.uid).get()
            .addOnSuccessListener { result ->
                val items = result.documents.mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    val otherUid = participants.filterIsInstance<String>().firstOrNull { it != user.uid } ?: return@mapNotNull null
                    MatchItem(doc.id, otherUid, "")
                }
                if (items.isEmpty()) {
                    matches = emptyList()
                    matchesBusy = false
                } else {
                    var remaining = items.size
                    val named = mutableListOf<MatchItem>()
                    items.forEach { item ->
                        FirebaseFirestore.getInstance().collection("publicProfiles").document(item.otherUid).get()
                            .addOnSuccessListener { profileDoc ->
                                named.add(item.copy(otherName = profileDoc.getString("name") ?: "Vibez member"))
                                remaining--
                                if (remaining == 0) {
                                    matches = named.sortedBy { it.otherName.lowercase() }
                                    matchesBusy = false
                                }
                            }
                            .addOnFailureListener {
                                named.add(item.copy(otherName = "Vibez member"))
                                remaining--
                                if (remaining == 0) {
                                    matches = named.sortedBy { it.otherName.lowercase() }
                                    matchesBusy = false
                                }
                            }
                    }
                }
            }
            .addOnFailureListener {
                matchesBusy = false
                chatMessage = "Could not load matches. Check Firestore rules."
            }
    }

    fun loadChat(match: MatchItem) {
        selectedMatch = match
        chatMessage = ""
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("matches").document(match.id)
            .collection("messages").orderBy("createdAt").limit(100).get()
            .addOnSuccessListener { result ->
                chatLines = result.documents.map { doc ->
                    ChatLine(doc.id, doc.getString("senderUid").orEmpty(), doc.getString("text").orEmpty())
                }
            }
            .addOnFailureListener {
                chatMessage = "Could not load messages. Check your connection and Firestore rules."
            }
    }

    fun sendChatMessage() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val match = selectedMatch ?: return
        val clean = chatDraft.trim()
        if (clean.isBlank() || chatBusy) return
        chatBusy = true
        val data = hashMapOf<String, Any>(
            "senderUid" to user.uid,
            "text" to clean.take(1000),
            "createdAt" to FieldValue.serverTimestamp()
        )
        FirebaseFirestore.getInstance().collection("matches").document(match.id)
            .collection("messages").add(data)
            .addOnSuccessListener {
                chatDraft = ""
                chatBusy = false
                loadChat(match)
            }
            .addOnFailureListener {
                chatBusy = false
                chatMessage = "Message could not be sent. Check Firestore rules."
            }
    }

    LaunchedEffect(signedInEmail) {
        if (signedInEmail.isNotBlank()) loadProfile()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Plum, secondary = Pink)) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFFFEEF4), Color(0xFFF5E9FF), Color.White))
            ),
            contentAlignment = Alignment.Center
        ) {
            if (signedInEmail.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp).padding(bottom = 86.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(48.dp))
                    Text("Vibez Dating", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
                    Text(if (showDiscover) "Discover new people" else if (showChats) "Matches & chats" else "Signed in as $signedInEmail", color = Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))


                    if (showDiscover) {
                        Text("Meet the community", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Plum)
                        Text("Only display names and profile details are shown here — never email addresses.", fontSize = 12.sp, color = Ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        if (discoverBusy) CircularProgressIndicator(color = Plum)
                        if (discoverMessage.isNotBlank()) {
                            Text(discoverMessage, color = if (discoverMessage.startsWith("No other")) Plum else Color(0xFFB3261E), textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
                            if (!discoverBusy && !discoverMessage.startsWith("No other")) {
                                OutlinedButton(onClick = { loadDiscoverProfiles() }) { Text("Try again", color = Plum) }
                            }
                        }
                        if (swipeIndex < publicProfiles.size) {
                            val profile = publicProfiles[swipeIndex]
                            Surface(shape = RoundedCornerShape(28.dp), color = Color.White,
                                shadowElevation = 10.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Column(Modifier.padding(22.dp)) {
                                    Box(Modifier.fillMaxWidth().height(145.dp).background(Color(0xFFFFE4EE), RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(58.dp))
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("${profile.name}, ${profile.age}", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Plum)
                                    Text(profile.gender, color = Ink, fontWeight = FontWeight.SemiBold)
                                    if (profile.bio.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(profile.bio, color = Ink, fontSize = 16.sp) }
                                    if (profile.interests.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text("Interests • ${profile.interests}", color = Plum, fontSize = 14.sp) }
                                    Text("Profile ${swipeIndex + 1} of ${publicProfiles.size}", color = Color.Gray, fontSize = 12.sp,
                                        modifier = Modifier.align(Alignment.End).padding(top = 12.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(enabled = !swipeBusy, onClick = {
                                    val current = FirebaseAuth.getInstance().currentUser
                                    if (current != null) {
                                        swipeBusy = true
                                        val data = hashMapOf<String, Any>("fromUid" to current.uid, "toUid" to profile.uid,
                                            "decision" to "pass", "createdAt" to FieldValue.serverTimestamp())
                                        FirebaseFirestore.getInstance().collection("swipes").document("${current.uid}_${profile.uid}")
                                            .set(data).addOnCompleteListener { task ->
                                                swipeBusy = false
                                                if (task.isSuccessful) { swipeIndex++; swipeMessage = "Passed. Next profile!" }
                                                else swipeMessage = "Could not save Pass. Check Firestore rules."
                                            }
                                    }
                                }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(50)) {
                                    Icon(Icons.Default.Close, null, tint = Color(0xFFB3261E))
                                    Spacer(Modifier.width(5.dp)); Text("Pass", color = Color(0xFFB3261E))
                                }
                                Button(enabled = !swipeBusy, onClick = {
                                    val current = FirebaseAuth.getInstance().currentUser
                                    if (current != null) {
                                        swipeBusy = true
                                        val data = hashMapOf<String, Any>("fromUid" to current.uid, "toUid" to profile.uid,
                                            "decision" to "like", "createdAt" to FieldValue.serverTimestamp())
                                        FirebaseFirestore.getInstance().collection("swipes").document("${current.uid}_${profile.uid}")
                                            .set(data).addOnCompleteListener { task ->
                                                if (!task.isSuccessful) {
                                                    swipeBusy = false
                                                    swipeMessage = "Could not save Like. Check Firestore rules."
                                                } else {
                                                    FirebaseFirestore.getInstance().collection("swipes").document("${profile.uid}_${current.uid}").get()
                                                        .addOnCompleteListener { opposite ->
                                                            swipeBusy = false
                                                            if (opposite.isSuccessful && opposite.result?.getString("decision") == "like") {
                                                                val ids = listOf(current.uid, profile.uid).sorted()
                                                                val matchId = ids.joinToString("_")
                                                                val matchData = hashMapOf<String, Any>(
                                                                    "participants" to ids,
                                                                    "createdAt" to FieldValue.serverTimestamp()
                                                                )
                                                                FirebaseFirestore.getInstance().collection("matches").document(matchId)
                                                                    .set(matchData)
                                                                    .addOnSuccessListener {
                                                                        swipeMessage = "It's a match! You both liked each other. Open Matches / Chat below."
                                                                        loadMatches()
                                                                    }
                                                                    .addOnFailureListener {
                                                                        swipeMessage = "It's a mutual Like, but saving the match failed. Check Firestore rules."
                                                                    }
                                                            } else {
                                                                swipeMessage = "Like saved! If they Like you too, it's a match."
                                                            }
                                                            swipeIndex++
                                                        }
                                                }
                                            }
                                    }
                                }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(containerColor = Pink)) {
                                    Icon(Icons.Default.FavoriteBorder, null, tint = Color.White)
                                    Spacer(Modifier.width(5.dp)); Text("Like", color = Color.White)
                                }
                            }
                            if (swipeMessage.isNotBlank()) Text(swipeMessage, color = Plum, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(8.dp))
                        } else if (!discoverBusy && publicProfiles.isNotEmpty()) {
                            Text("You're all caught up!", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Plum)
                            Text("You've made a choice for every profile currently available.", color = Ink, textAlign = TextAlign.Center)
                        }
                        OutlinedButton(onClick = { loadDiscoverProfiles() }, enabled = !discoverBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Refresh profiles", color = Plum)
                        }
                    } else if (showChats) {
                        if (selectedMatch != null) {
                            val currentMatch = selectedMatch!!
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { selectedMatch = null; chatLines = emptyList(); loadMatches() }) {
                                    Text("← Matches", color = Plum)
                                }
                                Text(currentMatch.otherName, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Plum)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (chatMessage.isNotBlank()) Text(chatMessage, color = Color(0xFFB3261E), textAlign = TextAlign.Center)
                            if (chatLines.isEmpty()) {
                                Text("Say hello and start the conversation.", color = Ink, modifier = Modifier.padding(vertical = 24.dp))
                            } else {
                                chatLines.forEach { line ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.senderUid == FirebaseAuth.getInstance().currentUser?.uid) Arrangement.End else Arrangement.Start) {
                                        Surface(color = if (line.senderUid == FirebaseAuth.getInstance().currentUser?.uid) Color(0xFFFFD8E5) else Color.White,
                                            shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 280.dp)) {
                                            Text(line.text, color = Ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = chatDraft, onValueChange = { chatDraft = it.take(1000) },
                                label = { Text("Write a message") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { sendChatMessage() }, enabled = chatDraft.isNotBlank() && !chatBusy,
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Plum)) {
                                Text(if (chatBusy) "Sending…" else "Send message", color = Color.White)
                            }
                        } else {
                            Text("Matches & Chats", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Plum)
                            Text("Only people who Like each other can chat.", color = Ink, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            if (matchesBusy) CircularProgressIndicator(color = Plum)
                            if (chatMessage.isNotBlank()) Text(chatMessage, color = Color(0xFFB3261E), textAlign = TextAlign.Center)
                            if (!matchesBusy && matches.isEmpty()) {
                                Surface(shape = RoundedCornerShape(22.dp), color = Color.White.copy(alpha = 0.94f), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(42.dp))
                                        Spacer(Modifier.height(10.dp))
                                        Text("No matches yet", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Plum)
                                        Text("When you and another member Like each other, your match will appear here.", color = Ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                                        Spacer(Modifier.height(12.dp))
                                        Button(onClick = { showDiscover = true; showChats = false; loadDiscoverProfiles() }, colors = ButtonDefaults.buttonColors(containerColor = Plum)) {
                                            Text("Discover people", color = Color.White)
                                        }
                                    }
                                }
                            }
                            matches.forEach { item ->
                                Surface(onClick = { loadChat(item) }, shape = RoundedCornerShape(18.dp), color = Color.White,
                                    shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Favorite, null, tint = Pink, modifier = Modifier.size(30.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.otherName, color = Plum, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                            Text("Tap to open private chat", color = Ink, fontSize = 12.sp)
                                        }
                                        Text("›", color = Plum, fontSize = 28.sp)
                                    }
                                }
                            }
                            OutlinedButton(onClick = { loadMatches() }, enabled = !matchesBusy, modifier = Modifier.fillMaxWidth()) {
                                Text("Refresh matches", color = Plum)
                            }
                        }
                    } else if (!profileLoaded) {
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
                                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(age, { age = it.filter(Char::isDigit).take(3) }, label = { Text("Age (18+)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(gender, { gender = it.take(40) }, label = { Text("Gender") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                                                val db = FirebaseFirestore.getInstance()
                                                val privateData = hashMapOf<String, Any>(
                                                    "uid" to user.uid,
                                                    "email" to (user.email ?: ""),
                                                    "name" to name.trim(),
                                                    "age" to ageValue,
                                                    "gender" to gender.trim(),
                                                    "bio" to bio.trim(),
                                                    "interests" to interests.trim(),
                                                    "updatedAt" to FieldValue.serverTimestamp()
                                                )
                                                val publicData = hashMapOf<String, Any>(
                                                    "uid" to user.uid,
                                                    "name" to name.trim(),
                                                    "age" to ageValue,
                                                    "gender" to gender.trim(),
                                                    "bio" to bio.trim(),
                                                    "interests" to interests.trim(),
                                                    "updatedAt" to FieldValue.serverTimestamp()
                                                )
                                                db.batch()
                                                    .set(db.collection("profiles").document(user.uid), privateData)
                                                    .set(db.collection("publicProfiles").document(user.uid), publicData)
                                                    .commit()
                                                    .addOnCompleteListener { task ->
                                                        busy = false
                                                        message = if (task.isSuccessful) {
                                                            "Profile saved successfully!"
                                                        } else {
                                                            "Save failed: ${task.exception?.localizedMessage ?: "Check Firestore security rules."}"
                                                        }
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
                        showDiscover = false
                        showChats = false
                        selectedMatch = null
                        matches = emptyList()
                        publicProfiles = emptyList()
                        name = ""; age = ""; gender = ""; bio = ""; interests = ""; message = ""
                    }) { Text("Sign out", color = Plum) }
                    Text("Vibez Dating is for adults 18+. Be respectful and protect your personal information.", color = Plum, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
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
                            if (message.isNotBlank()) Text(message, color = if (message.startsWith("Password reset email sent")) Color(0xFF167A45) else Color(0xFFB3261E), modifier = Modifier.padding(top = 10.dp))
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
                            if (!isRegister) {
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        val cleanEmail = email.trim()
                                        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                                            message = "Enter your email address first, then tap Forgot password."
                                        } else {
                                            busy = true
                                            message = ""
                                            FirebaseAuth.getInstance().sendPasswordResetEmail(cleanEmail)
                                                .addOnCompleteListener { result ->
                                                    busy = false
                                                    message = if (result.isSuccessful) {
                                                        "Password reset email sent. Check your inbox and spam folder."
                                                    } else {
                                                        result.exception?.localizedMessage ?: "Could not send reset email. Please try again."
                                                    }
                                                }
                                        }
                                    }
                                ) {
                                    Text("Forgot password?", color = Plum, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            TextButton(onClick = { isRegister = !isRegister; message = "" }) {
                                Text(if (isRegister) "Already have an account? Log in" else "New to Vibez? Create account", color = Plum)
                            }
                        }
                    }
                }
            }
            if (signedInEmail.isNotBlank()) {
                Surface(color = Color.White.copy(alpha = 0.98f), shadowElevation = 12.dp,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDiscover = false; showChats = false }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FavoriteBorder, null, tint = if (!showDiscover && !showChats) Pink else Plum)
                                Text("Profile", color = if (!showDiscover && !showChats) Pink else Plum, fontSize = 11.sp)
                            }
                        }
                        TextButton(onClick = { showDiscover = true; showChats = false; loadDiscoverProfiles() }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Search, null, tint = if (showDiscover) Pink else Plum)
                                Text("Discover", color = if (showDiscover) Pink else Plum, fontSize = 11.sp)
                            }
                        }
                        TextButton(onClick = { showDiscover = false; showChats = true; selectedMatch = null; loadMatches() }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Favorite, null, tint = if (showChats) Pink else Plum)
                                Text("Matches / Chat", color = if (showChats) Pink else Plum, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
