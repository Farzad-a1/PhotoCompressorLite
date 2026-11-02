package com.photocompressorlite.myapp
import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
private val Blue = Color(0xFF0066FF)
private val Navy = Color(0xFF071A3D)
private val Muted = Color(0xFF8290A7)
private val Pale = Color(0xFFEAF3FF)
private val targets = listOf(50L, 100L, 200L, 500L, 1024L)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoApp()
        }
    }
}
@Composable fun PhotoApp(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val dark = s.theme == "Dark" || (s.theme == "System" && isSystemInDarkTheme())
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF78AAFF), background = Color(0xFF0E1728), surface = Color(0xFF142136), onSurface = Color(0xFFEDF3FF), onBackground = Color(0xFFEDF3FF), secondaryContainer = Color(0xFF20344F), onSecondaryContainer = Color(0xFFEDF3FF), primaryContainer = Color(0xFF20344F), secondary = Color(0xFFAFBED4), surfaceVariant = Color(0xFF20344F)) else lightColorScheme(primary = Blue, background = Color.White, surface = Color.White, onSurface = Navy, onBackground = Navy, secondaryContainer = Pale, onSecondaryContainer = Navy, primaryContainer = Pale, onPrimaryContainer = Navy, secondary = Muted, surfaceVariant = Pale)
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        val snackbar = remember {
            SnackbarHostState()
        }
        val context = LocalContext.current
        SideEffect {
            (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) {
            vm.add(it)
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) vm.save() else vm.message("Storage permission is needed to save on Android 8 and 9. You can still share your photos.")
        }
        fun pick() {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        LaunchedEffect(s.message) {
            s.message?.let {
                snackbar.showSnackbar(it)
                vm.message(null)
            }
        }
        BackHandler(s.screen != "home") {
            vm.back()
        }
        Scaffold(snackbarHost = {
            SnackbarHost(snackbar)
        }, containerColor = scheme.background) {
            insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                    androidx.compose.animation.Crossfade(targetState = s.screen, label = "Screen transition") {
                        screen -> Column(Modifier.fillMaxSize()) {
                            when (screen) {
                                "home" -> Home(s, {
                                    vm.navigate("select")
                                }, {
                                    vm.navigate("settings")
                                })
                                "select" -> SelectPhotos(s, vm, ::pick)
                                "options" -> OptionsScreen(s, vm)
                                "processing" -> Processing(s, vm::cancel)
                                "results" -> Results(s, vm, {
                                    if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.save()
                                }, {
                                    val ok = s.results.filter {
                                        it.path != null
                                    }
                                    if (ok.isNotEmpty()) runCatching {
                                        val uris = ArrayList(ok.map(vm.repository::shareUri))
                                        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "image/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            clipData = ClipData.newUri(context.contentResolver, "Compressed photos", uris.first()).apply {
                                                uris.drop(1).forEach {
                                                    addItem(ClipData.Item(it))
                                                }
                                            }
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share compressed photos"))
                                    }.onFailure {
                                        vm.message("No app could share these photos.")
                                    }
                                })
                                "settings" -> Settings(s, vm)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp, lineHeight = 21.sp)
}
@Composable private fun Title(text: String) {
    Text(text, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, lineHeight = 38.sp)
}
@Composable private fun RoundIcon(icon: ImageVector, description: String, action: () -> Unit) {
    FilledTonalIconButton(onClick = action, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.size(44.dp)) {
        Icon(icon, description, Modifier.size(21.dp))
    }
}
@Composable private fun Header(title: String, subtitle: String, back: () -> Unit, extra: @Composable () -> Unit = {
}) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Outlined.ArrowBack, "Back", back)
            Text("PHOTO COMPRESSOR", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            extra()
        }
        Spacer(Modifier.height(24.dp))
        Title(title)
        Spacer(Modifier.height(8.dp))
        Label(subtitle)
    }
}
@Composable private fun Primary(text: String, enabled: Boolean = true, icon: ImageVector? = null, action: () -> Unit) {
    Button(onClick = action, enabled = enabled, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), contentPadding = PaddingValues(16.dp)) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(21.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun PhotoArt(modifier: Modifier = Modifier) {
    Box(modifier.size(118.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(25.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .1f), modifier = Modifier.size(112.dp)) {
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp, modifier = Modifier.size(78.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Landscape, null, Modifier.size(52.dp), tint = Color.White)
            }
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomEnd).size(36.dp), shadowElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
@Composable private fun Home(s: AppState, select: () -> Unit, settings: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Blue, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(46.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhotoSizeSelectLarge, null, tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("Photo Compressor\nLite", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Outlined.Settings, "Settings", settings)
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Text("LESS SPACE. MORE MEMORIES.", fontSize = 11.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("Compress\nYour Photos", fontSize = 44.sp, lineHeight = 49.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2).sp)
            Spacer(Modifier.height(12.dp))
            Label("Reduce image size without the hassle.")
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PhotoArt(Modifier.padding(12.dp))
                    Spacer(Modifier.height(20.dp))
                    Text("Big memories. Smaller files.", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Label("One photo or a whole collection.")
                    Spacer(Modifier.height(24.dp))
                    Primary("Select Photos", icon = Icons.Outlined.AddPhotoAlternate, action = select)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Label("100% offline. Your photos stay yours.")
            }
        }
        item {
            Text("Recent compressions", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        if (s.history.isEmpty()) item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("A little space, a fresh start", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Label("Your recent compression summaries will appear here.")
                }
            }
        }
        items(s.history) {
            entry -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${entry.count} photos compressed", fontWeight = FontWeight.SemiBold)
                    Label(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.time)))
                }
                Text(sizeText(entry.compressed), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }
        }
    }
}
@Composable private fun ColumnScope.SelectPhotos(s: AppState, vm: MainViewModel, pick: () -> Unit) {
    var menu by remember {
        mutableStateOf(false)
    }
    Header("Select Photos", "Choose one or more photos to compress them all at once.", vm::back) {
        Box {
            RoundIcon(Icons.Outlined.MoreHoriz, "More options", {
                menu = true
            })
            DropdownMenu(expanded = menu, onDismissRequest = {
                menu = false
            }) {
                DropdownMenuItem(text = {
                    Text("Settings")
                }, onClick = {
                    menu = false
                    vm.navigate("settings")
                })
                DropdownMenuItem(text = {
                    Text("Deselect all")
                }, onClick = {
                    menu = false
                    vm.deselect()
                })
            }
        }
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp, 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Add Photos", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Label("Select from your gallery")
                        Text("Multiple photos supported", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 5.dp))
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = pick, shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Text("Choose Photos", Modifier.padding(start = 5.dp), fontSize = 13.sp)
                        }
                    }
                    PhotoArt(Modifier.padding(start = 4.dp).widthIn(max = 100.dp))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selected Photos (${s.selected.size})", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::deselect, enabled = s.selected.isNotEmpty()) {
                    Text("Deselect All", fontSize = 12.sp)
                }
            }
        }
        if (s.loading) item {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Label("Reading selected photos…")
        }
        if (s.photos.isEmpty() && !s.loading) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Collections, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(12.dp))
                Text("Your photos belong here", fontWeight = FontWeight.SemiBold)
                Label("Choose photos to get started.")
            }
        }
        items(s.photos.chunked(3)) {
            row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach {
                    photo -> val selected = photo.uri in s.selected
                    val selectionColor by androidx.compose.animation.animateColorAsState(if (selected) Blue else Color.Black.copy(alpha = .25f), label = "Photo selection")
                    Box(Modifier.weight(1f).semantics { this.selected = selected }.aspectRatio(.91f).clip(RoundedCornerShape(18.dp)).border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(18.dp)).clickable(onClickLabel = if (selected) "Deselect ${photo.name}" else "Select ${photo.name}") {
                        vm.toggle(photo.uri)
                    }) {
                        Thumbnail(photo.uri, photo.name, Modifier.fillMaxSize())
                        Surface(color = Color.Black.copy(alpha = .55f), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomStart).padding(7.dp)) {
                            Text(sizeText(photo.bytes), fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(5.dp))
                        }
                        Surface(shape = CircleShape, color = selectionColor, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)) {
                            if (selected) Icon(Icons.Outlined.Check, "Selected", tint = Color.White, modifier = Modifier.padding(4.dp))
                        }
                    }
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    Surface(shadowElevation = 12.dp) {
        Column(Modifier.padding(24.dp, 14.dp)) {
            Primary("Continue (${s.selected.size} photos)", s.selected.isNotEmpty() && !s.loading, Icons.Outlined.ArrowForward) {
                vm.navigate("options")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = pick) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Text("Add More Photos", fontSize = 12.sp)
                }
                TextButton(onClick = vm::remove, enabled = s.selected.isNotEmpty()) {
                    Text("Remove Selected", fontSize = 12.sp)
                }
            }
        }
    }
}
@Composable private fun Thumbnail(uri: Uri, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = CompressionMath.sampleSize(bounds.outWidth, bounds.outHeight, 256)
                }
                val decoded = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: return@runCatching null
                val orientation = context.contentResolver.openInputStream(uri)?.use {
                    androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
                } ?: 1
                val matrix = android.graphics.Matrix().apply {
                    when (orientation) {
                        2 -> setScale(-1f, 1f)
                        3 -> setRotate(180f)
                        4 -> setScale(1f, -1f)
                        5 -> {
                            setRotate(90f)
                            postScale(-1f, 1f)
                        }
                        6 -> setRotate(90f)
                        7 -> {
                            setRotate(-90f)
                            postScale(-1f, 1f)
                        }
                        8 -> setRotate(-90f)
                    }
                }
                if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Outlined.Image, description, tint = MaterialTheme.colorScheme.secondary)
    }
}
@Composable private fun ColumnScope.OptionsScreen(s: AppState, vm: MainViewModel) {
    Header("Make room for more", "Find the right balance of size and detail.", vm::back)
    var custom by remember {
        mutableStateOf(if (s.options.target / 1024 !in targets) (s.options.target / 1024).toString() else "")
    }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.photos.filter {
                    it.uri in s.selected
                }.take(4).forEach {
                    Thumbnail(it.uri, it.name, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${s.selected.size} photos · ${sizeText(s.photos.filter { it.uri in s.selected }.sumOf { it.bytes })} original", fontWeight = FontWeight.SemiBold)
        }
        item {
            Text("Target size per photo", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Label("A best-effort limit. Detailed images may need smaller dimensions; the actual size is always shown.")
            Spacer(Modifier.height(12.dp))
            TargetChips(s.options.target) {
                custom = ""
                vm.options(s.options.copy(target = it))
            }
        }
        item {
            OutlinedTextField(value = custom, onValueChange = {
                value -> custom = value.filter(Char::isDigit).take(6)
                custom.toLongOrNull()?.takeIf {
                    it in 1..102400
                }?.let {
                    vm.options(s.options.copy(target = it * 1024))
                }
            }, label = {
                Text("Custom target (KB)")
            }, supportingText = {
                Text("1–102400 KB")
            }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        }
        item {
            Text("Maximum quality · ${s.options.quality}%", fontWeight = FontWeight.Bold)
            Slider(value = s.options.quality.toFloat(), onValueChange = {
                vm.options(s.options.copy(quality = it.toInt()))
            }, valueRange = 30f..100f, steps = 6)
            Label("Quality and dimensions adapt to meet your target.")
        }
        item {
            Text("Output format", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("JPEG", "WebP").forEach {
                    FilterChip(s.options.format == it, {
                        vm.options(s.options.copy(format = it))
                    }, label = {
                        Text(it)
                    })
                }
            }
            if (s.options.format == "JPEG") Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.options.flatten, {
                    vm.options(s.options.copy(flatten = it))
                })
                Text("Allow a white background for transparent images", fontSize = 13.sp, modifier = Modifier.weight(1f))
            } else Label("WebP preserves transparency and is supported by modern apps.")
        }
    }
    Box(Modifier.padding(24.dp, 14.dp)) {
        Primary("Compress Photos", s.selected.isNotEmpty() && (custom.isEmpty() || custom.toLongOrNull()?.let {
            it in 1..102400
        }
        == true), Icons.Outlined.Compress, vm::start)
    }
}
@Composable private fun TargetChips(target: Long, action: (Long) -> Unit) {
    Column {
        targets.chunked(3).forEach {
            row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach {
                    value -> FilterChip(selected = target == value * 1024, onClick = {
                        action(value * 1024)
                    }, label = {
                        Text(if (value == 1024L) "1 MB" else "$value KB")
                    }, shape = RoundedCornerShape(12.dp))
                }
            }
        }
    }
}
@Composable private fun Processing(s: AppState, cancel: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        PhotoArt()
        Spacer(Modifier.height(36.dp))
        Title("Making space…")
        Spacer(Modifier.height(12.dp))
        Label("Photo ${s.current} of ${s.batchTotal}")
        Spacer(Modifier.height(26.dp))
        LinearProgressIndicator(progress = {
            s.results.size.toFloat() / s.batchTotal.coerceAtLeast(1)
        }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
        Spacer(Modifier.height(14.dp))
        Label("${s.results.count { it.path != null }} successful · ${s.results.count { it.error != null }} failed")
        Spacer(Modifier.height(20.dp))
        Label("Processed locally. Your originals are safe.")
        Spacer(Modifier.height(28.dp))
        OutlinedButton(onClick = cancel, shape = RoundedCornerShape(16.dp)) {
            Text("Cancel compression")
        }
        s.results.forEach {
            Text("${if (it.error == null) "✓" else "!"} ${it.photo.name}", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
@Composable private fun ColumnScope.Results(s: AppState, vm: MainViewModel, save: () -> Unit, share: () -> Unit) {
    val ok = s.results.filter {
        it.path != null
    }
    val original = ok.sumOf {
        it.photo.bytes
    }
    val compressed = ok.sumOf {
        it.bytes
    }
    val percent = CompressionMath.savedPercent(original, compressed)
    var showSavings by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(Unit) {
        showSavings = true
    }
    val displayedPercent by androidx.compose.animation.core.animateFloatAsState(if (showSavings) percent.toFloat() else 0f, label = "Storage saved")
    Header(if (s.cancelled) "Compression stopped" else if (ok.isEmpty()) "No photos compressed" else "Compression Complete", "${ok.size} successful · ${s.results.count { it.error != null }} failed" + if (s.cancelled) " · ${s.batchTotal - s.results.size} unprocessed" else "", vm::back)
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (ok.isNotEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(26.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(if (percent >= 0) "%.1f%%".format(displayedPercent) else "+%.1f%%".format(-displayedPercent), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Label(if (percent >= 0) "less storage used" else "larger than the originals")
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column {
                                Label("Original")
                                Text(sizeText(original), fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Outlined.ArrowForward, null)
                            Column {
                                Label("Compressed")
                                Text(sizeText(compressed), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item {
                Text("Before & after", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Thumbnail(ok.first().photo.uri, "Original photo", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                        Label("Original · ${sizeText(ok.first().photo.bytes)}")
                    }
                    Column(Modifier.weight(1f)) {
                        Thumbnail(Uri.fromFile(File(ok.first().path!!)), "Compressed photo", Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                        Label("After · ${sizeText(ok.first().bytes)}")
                    }
                }
            }
        }
        items(s.results) {
            result -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Thumbnail(result.photo.uri, result.photo.name, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f)) {
                    Text(result.photo.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    if (result.error != null) Text(result.error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) else {
                        Label("${sizeText(result.photo.bytes)} → ${sizeText(result.bytes)}")
                        Text(if (result.targetMet) "Within target" else "Above target · best effort", fontSize = 12.sp, color = if (result.targetMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                }
                Icon(if (result.error == null) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (result.error == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
    Column(Modifier.padding(24.dp, 12.dp)) {
        Primary(if (s.saving) "Saving…" else if (ok.isNotEmpty() && s.saved.size == ok.size) "Photos Saved" else "Save Photos", ok.isNotEmpty() && !s.saving && s.saved.size < ok.size, Icons.Outlined.SaveAlt, save)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = share, enabled = ok.isNotEmpty()) {
                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                Text(" Share")
            }
            TextButton(onClick = vm::reset) {
                Text("Compress More Photos")
            }
        }
    }
}
@Composable private fun Settings(s: AppState, vm: MainViewModel) {
    var dialog by remember {
        mutableStateOf<String?>(null)
    }
    Header("Settings", "A little more control. The same simplicity.", vm::back)
    LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Light", "Dark", "System").forEach {
                    FilterChip(s.theme == it, {
                        vm.theme(it)
                    }, label = {
                        Text(it)
                    })
                }
            }
        }
        item {
            Text("Default compression target", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            TargetChips(vm.repository.defaultTarget, vm::defaultTarget)
        }
        item {
            Text("Default output format", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("JPEG", "WebP").forEach {
                    FilterChip(vm.repository.defaultFormat == it, {
                        vm.defaultFormat(it)
                    }, label = {
                        Text(it)
                    })
                }
            }
        }
        item {
            HorizontalDivider()
            SettingsRow(Icons.Outlined.History, "Clear local history") {
                dialog = "Clear history"
            }
            SettingsRow(Icons.Outlined.PrivacyTip, "Privacy Policy") {
                dialog = "Privacy Policy"
            }
            SettingsRow(Icons.Outlined.Info, "About App") {
                dialog = "About App"
            }
            Label("Version ${BuildConfig.VERSION_NAME}")
            Spacer(Modifier.height(12.dp))
            Label("Made for your photos. Built for your privacy.")
        }
    }
    dialog?.let {
        title -> AlertDialog(onDismissRequest = {
            dialog = null
        }, title = {
            Text(title)
        }, text = {
            Text(when (title) {
                "Clear history" -> "Remove local compression summaries? Your original and saved photos will remain available."
                "Privacy Policy" -> "Photo Compressor Lite works entirely on your device. It has no accounts, ads, analytics, internet access, or photo uploads. Only photos you choose are read. Temporary compressed files are kept in private app storage for saving and sharing until you delete app data or uninstall. Settings and up to 20 compression summaries stay on this device. Clear history removes summaries. Saved photos remain in your gallery. Sharing grants the chosen app access to the compressed photos; that app's privacy policy applies. Android 8 and 9 require storage permission only to save to the gallery. No broad gallery access is requested. App backup is disabled."
                else -> "Photo Compressor Lite ${BuildConfig.VERSION_NAME}\n\nCompress JPEG, PNG, and WebP photos offline. Export JPEG or WebP, save space, and keep your original memories.\n\nTarget sizes are best effort. Smaller files may have less detail."
            })
        }, confirmButton = {
            TextButton(onClick = {
                if (title == "Clear history") vm.clearHistory()
                dialog = null
            }) {
                Text(if (title == "Clear history") "Clear history" else "Done")
            }
        }, dismissButton = {
            if (title == "Clear history") TextButton(onClick = {
                dialog = null
            }) {
                Text("Cancel")
            }
        })
    }
}
@Composable private fun SettingsRow(icon: ImageVector, text: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, modifier = Modifier.weight(1f).padding(start = 14.dp), fontWeight = FontWeight.Medium)
        Icon(Icons.Outlined.ChevronRight, null)
    }
}


