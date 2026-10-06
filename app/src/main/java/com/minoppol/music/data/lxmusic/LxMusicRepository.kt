package com.minoppol.music.data.lxmusic

import android.content.Context
import android.net.Uri
import com.google.gson.JsonParser
import com.minoppol.music.data.database.AlbumEntity
import com.minoppol.music.data.database.ArtistEntity
import com.minoppol.music.data.database.MusicDao
import com.minoppol.music.data.database.SongArtistCrossRef
import com.minoppol.music.data.database.SongEntity
import com.minoppol.music.data.lxmusic.netease.NeteaseCookieStore
import com.minoppol.music.data.lxmusic.netease.NeteaseOfficialApi
import com.minoppol.music.data.lxmusic.netease.NeteaseWeapiCrypto
import com.minoppol.music.data.lxmusic.platform.KugouOfficialApi
import com.minoppol.music.data.lxmusic.platform.PlatformResult
import com.minoppol.music.data.lxmusic.platform.TencentOfficialApi
import com.minoppol.music.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxMusicRepository @Inject constructor(
    @ApplicationContext internal val context: Context,
    private val engine: LxScriptEngine,
    internal val okHttpClient: OkHttpClient,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val neteaseCookieStore: NeteaseCookieStore,
    private val neteaseOfficialApi: NeteaseOfficialApi,
    private val playbackQualityTracker: LxPlaybackQualityTracker,
    private val tencentOfficialApi: TencentOfficialApi,
    private val kugouOfficialApi: KugouOfficialApi,
    internal val tencentCookieStore: com.minoppol.music.data.lxmusic.tencent.TencentCookieStore,
    internal val kugouCookieStore: com.minoppol.music.data.lxmusic.kugou.KugouCookieStore,
    internal val kuwoCookieStore: com.minoppol.music.data.lxmusic.kuwo.KuwoCookieStore,
) {
    internal val accountExtension: LxAccountExtension = LxAccountExtensionLoader.load(this)

    val accountWritesEnabled: Boolean get() = accountExtension.isAvailable

    private val _scripts = MutableStateFlow<List<LxScriptMeta>>(emptyList())
    val scripts: StateFlow<List<LxScriptMeta>> = _scripts.asStateFlow()

    private val _loaded = MutableStateFlow<Map<String, LxScriptCapabilities>>(emptyMap())
    val loaded: StateFlow<Map<String, LxScriptCapabilities>> = _loaded.asStateFlow()

    private val _runtimePaths = MutableStateFlow<Set<String>>(emptySet())
    val runtimePaths: StateFlow<Set<String>> = _runtimePaths.asStateFlow()


    private val _neteaseLikedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val neteaseLikedSongIds: StateFlow<Set<String>> = _neteaseLikedSongIds.asStateFlow()


    internal val _txLikedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val txLikedSongIds: StateFlow<Set<String>> = _txLikedSongIds.asStateFlow()

    internal val _kgLikedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val kgLikedSongIds: StateFlow<Set<String>> = _kgLikedSongIds.asStateFlow()

    internal val _kwLikedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val kwLikedSongIds: StateFlow<Set<String>> = _kwLikedSongIds.asStateFlow()

    internal val _kwFollowedArtists = MutableStateFlow<List<LxArtist>>(emptyList())
    val kwFollowedArtists: StateFlow<List<LxArtist>> = _kwFollowedArtists.asStateFlow()
    internal val _kwLikedAlbums = MutableStateFlow<List<LxAlbum>>(emptyList())
    val kwLikedAlbums: StateFlow<List<LxAlbum>> = _kwLikedAlbums.asStateFlow()

    private val kwOptimisticPrefs by lazy {
        context.getSharedPreferences("kw_optimistic", Context.MODE_PRIVATE)
    }

    init {
        _kwFollowedArtists.value = loadKwOptimisticArtists()
        _kwLikedAlbums.value = loadKwOptimisticAlbums()
    }

    fun addKwFollowedArtist(artist: LxArtist) {
        val cur = _kwFollowedArtists.value
        if (cur.any { it.id == artist.id }) return
        _kwFollowedArtists.value = listOf(artist) + cur
        saveKwOptimisticArtists(_kwFollowedArtists.value)
    }

    fun removeKwFollowedArtist(artistId: String) {
        _kwFollowedArtists.value = _kwFollowedArtists.value.filterNot { it.id == artistId }
        saveKwOptimisticArtists(_kwFollowedArtists.value)
    }

    fun addKwLikedAlbum(album: LxAlbum) {
        val cur = _kwLikedAlbums.value
        if (cur.any { it.id == album.id }) return
        _kwLikedAlbums.value = listOf(album) + cur
        saveKwOptimisticAlbums(_kwLikedAlbums.value)
    }

    fun removeKwLikedAlbum(albumId: String) {
        _kwLikedAlbums.value = _kwLikedAlbums.value.filterNot { it.id == albumId }
        saveKwOptimisticAlbums(_kwLikedAlbums.value)
    }

    private fun loadKwOptimisticArtists(): List<LxArtist> = runCatching {
        val json = kwOptimisticPrefs.getString("artists", null) ?: return emptyList()
        JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                LxArtist(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    pic = o.get("pic")?.takeIf { it.isJsonPrimitive }?.asString,
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun saveKwOptimisticArtists(list: List<LxArtist>) = runCatching {
        val arr = com.google.gson.JsonArray()
        list.forEach { a ->
            arr.add(com.google.gson.JsonObject().apply {
                addProperty("id", a.id); addProperty("name", a.name); addProperty("pic", a.pic)
            })
        }
        kwOptimisticPrefs.edit().putString("artists", arr.toString()).apply()
    }

    private fun loadKwOptimisticAlbums(): List<LxAlbum> = runCatching {
        val json = kwOptimisticPrefs.getString("albums", null) ?: return emptyList()
        JsonParser.parseString(json).asJsonArray.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                LxAlbum(
                    id = o.get("id").asString,
                    name = o.get("name").asString,
                    pic = o.get("pic")?.takeIf { it.isJsonPrimitive }?.asString,
                    artistName = o.get("artistName")?.takeIf { it.isJsonPrimitive }?.asString,
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun saveKwOptimisticAlbums(list: List<LxAlbum>) = runCatching {
        val arr = com.google.gson.JsonArray()
        list.forEach { a ->
            arr.add(com.google.gson.JsonObject().apply {
                addProperty("id", a.id); addProperty("name", a.name)
                addProperty("pic", a.pic); addProperty("artistName", a.artistName)
            })
        }
        kwOptimisticPrefs.edit().putString("albums", arr.toString()).apply()
    }

    suspend fun fetchKwFollowedArtists(): List<LxArtist>? =
        accountExtension.kwFetchFollowedArtists()?.also { list ->
            _kwFollowedArtists.value = list
            saveKwOptimisticArtists(list)
        }

    suspend fun fetchKwLikedAlbums(): List<LxAlbum>? =
        accountExtension.kwFetchLikedAlbums()?.also { list ->
            _kwLikedAlbums.value = list
            saveKwOptimisticAlbums(list)
        }

    suspend fun fetchTxLikedAlbums(): List<LxAlbum> =
        accountExtension.txFetchLikedAlbums() ?: emptyList()

    @Volatile
    private var neteaseUid: String? = null

    fun clearNeteaseUid() { neteaseUid = null }

    @Volatile
    var neteaseLikedListLoaded: Boolean = false
        private set

    @Volatile
    private var neteaseLikedListLoadingUid: String? = null

    private var preferredScriptPath: String? = null
    private var disabledPaths: Set<String> = emptySet()

    fun setPreferredScript(path: String?) {
        preferredScriptPath = path
    }

    fun setDisabledPaths(paths: Set<String>) {
        val newlyDisabled = paths - disabledPaths
        disabledPaths = paths
        newlyDisabled.forEach { path ->
            engine.unloadScript(path)
            _loaded.value = _loaded.value - path
            _runtimePaths.value = _runtimePaths.value - path
        }
        refreshScripts()
    }

    private val metaRegex = Regex("""@(name|version|author|description)\s+([^\r\n*]+)""")

    private val importedDir: File by lazy {
        File(context.filesDir, "lxmusic_imported").apply { mkdirs() }
    }

    private val capsCacheFile: File by lazy {
        File(context.filesDir, "lxmusic_caps_cache.json")
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        refreshScripts()
        loadCachedCapabilities()
        repoScope.launch {
            userPreferencesRepository.lxMusicDisabledScriptsFlow.collect { setDisabledPaths(it) }
        }
        repoScope.launch {
            runCatching { musicDao.backfillLxSongArtistCrossRefs() }
                .onFailure { Timber.w(it, "backfill LX artist cross refs failed") }
        }
    }

    private fun loadCachedCapabilities() {
        runCatching {
            if (!capsCacheFile.exists()) return
            val json = capsCacheFile.readText(Charsets.UTF_8)
            if (json.isBlank()) return
            val root = JsonParser.parseString(json).asJsonObject
            val caps = mutableMapOf<String, LxScriptCapabilities>()
            for ((path, value) in root.entrySet()) {
                val sourcesObj = value.asJsonObject?.getAsJsonObject("sources") ?: continue
                val sources = mutableMapOf<String, LxSourceInfo>()
                for ((srcName, srcValue) in sourcesObj.entrySet()) {
                    val srcObj = srcValue.asJsonObject
                    sources[srcName] = LxSourceInfo(
                        source = srcName,
                        type = srcObj.get("type")?.asString ?: "music",
                        actions = srcObj.getAsJsonArray("actions")?.map { it.asString } ?: emptyList(),
                        qualitys = srcObj.getAsJsonArray("qualitys")?.map { it.asString } ?: emptyList(),
                    )
                }
                caps[path] = LxScriptCapabilities(sources = sources)
            }
            if (caps.isNotEmpty()) {
                _loaded.value = caps
                Timber.d("LxMusicRepository: loaded cached capabilities for ${caps.size} scripts")
            }
        }.onFailure { Timber.w(it, "LxMusicRepository: failed to load caps cache") }
    }

    private fun saveCachedCapabilities() {
        runCatching {
            val root = com.google.gson.JsonObject()
            for ((path, caps) in _loaded.value) {
                val capsObj = com.google.gson.JsonObject()
                val sourcesObj = com.google.gson.JsonObject()
                for ((srcName, srcInfo) in caps.sources) {
                    val srcObj = com.google.gson.JsonObject().apply {
                        addProperty("type", srcInfo.type)
                        add("actions", com.google.gson.JsonArray().apply { srcInfo.actions.forEach { add(it) } })
                        add("qualitys", com.google.gson.JsonArray().apply { srcInfo.qualitys.forEach { add(it) } })
                    }
                    sourcesObj.add(srcName, srcObj)
                }
                capsObj.add("sources", sourcesObj)
                root.add(path, capsObj)
            }
            capsCacheFile.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { Timber.w(it, "LxMusicRepository: failed to save caps cache") }
    }

    fun refreshScripts() {
        val all = mutableListOf<LxScriptMeta>()
        all += scanAssets()
        all += scanImported()
        _scripts.value = all
    }

    private fun scanAssets(): List<LxScriptMeta> {
        return runCatching {
            context.assets.list("lxmusic/sources").orEmpty()
                .filter { it.endsWith(".js") }
                .sorted()
                .mapNotNull { fileName ->
                    val assetPath = "lxmusic/sources/$fileName"
                    val raw = runCatching {
                        context.assets.open(assetPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }.getOrNull() ?: return@mapNotNull null
                    parseScriptMeta(fileName, assetPath, raw, isImported = false)
                }
        }.getOrElse { emptyList() }
    }

    private fun scanImported(): List<LxScriptMeta> {
        val files = importedDir.listFiles { f -> f.extension == "js" } ?: return emptyList()
        return files.sortedBy { it.name }.mapNotNull { file ->
            val raw = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            parseScriptMeta(file.name, file.absolutePath, raw, isImported = true)
        }
    }

    private fun parseScriptMeta(fileName: String, path: String, raw: String, isImported: Boolean): LxScriptMeta {
        val fields = metaRegex.findAll(raw).associate { it.groupValues[1] to it.groupValues[2].trim() }
        return LxScriptMeta(
            assetPath = path,
            name = fields["name"]?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".js"),
            version = fields["version"] ?: "",
            author = fields["author"] ?: "",
            description = fields["description"] ?: "",
            isImported = isImported,
        )
    }

    suspend fun load(assetPath: String): Result<LxScriptCapabilities> {
        val meta = _scripts.value.firstOrNull { it.assetPath == assetPath }
            ?: return Result.failure(IllegalStateException("Script not found: $assetPath"))
        return engine.loadScript(meta).onSuccess { caps ->
            _loaded.value = _loaded.value + (assetPath to caps)
            _runtimePaths.value = _runtimePaths.value + assetPath
            saveCachedCapabilities()
        }.onFailure { e ->
            Timber.w(e, "LxMusicRepository: failed to load $assetPath")
        }
    }

    fun unload(assetPath: String) {
        engine.unloadScript(assetPath)
        _loaded.value = _loaded.value - assetPath
        _runtimePaths.value = _runtimePaths.value - assetPath
    }

    suspend fun searchMusic(assetPath: String, source: String, keyword: String, page: Int = 1): List<LxSong> {
        if (keyword.isBlank()) return emptyList()
        return when (source) {
            "wy" -> searchNetease(keyword, page)
            "kw" -> searchKuwo(keyword, page)
            "kg" -> searchKugou(keyword, page)
            "tx" -> searchQQ(keyword, page)
            "mg" -> searchMigu(keyword, page)
            else -> emptyList()
        }
    }

    // 酷狗先搜 v2 网页接口，空了再退 v3
    private suspend fun searchKugou(keyword: String, page: Int): List<LxSong> {
        val primary = runCatching { searchKugouV2(keyword, page) }
            .onFailure { Timber.w(it, "kugou search v2 failed") }
            .getOrElse { emptyList() }
        if (primary.isNotEmpty()) return primary
        return runCatching { searchKugouV3(keyword, page) }
            .onFailure { Timber.w(it, "kugou search v3 fallback failed") }
            .getOrElse { emptyList() }
    }

    private suspend fun searchKugouV2(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=$encoded&page=$page&pagesize=30&userid=0&clientver=&platform=WebFilter&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val errorCode = root.get("error_code")?.asInt ?: -1
                if (errorCode != 0) {
                    Timber.w("kugou search error_code=$errorCode")
                    return@use emptyList()
                }
                val lists = root.getAsJsonObject("data")
                    ?.getAsJsonArray("lists") ?: return@use emptyList()
                val seen = mutableSetOf<String>()
                val songs = mutableListOf<LxSong>()
                for (el in lists) {
                    val item = el.asJsonObject
                    val audioId = item.get("Audioid")?.asString ?: continue
                    val fileHash = item.get("FileHash")?.asString ?: ""
                    val duration = item.get("Duration")?.asLong ?: 0L
                    val hqHash = item.get("HQFileHash")?.asString ?: ""
                    val sqHash = item.get("SQFileHash")?.asString ?: ""
                    val resHash = item.get("ResFileHash")?.asString ?: ""
                    val dedupeKey = "$audioId$fileHash"
                    if (dedupeKey in seen) continue
                    seen.add(dedupeKey)
                    val oriName = item.get("OriSongName")?.asString ?: ""
                    val suffix = item.get("Suffix")?.asString ?: ""
                    val name = if (suffix.isNotBlank()) "$oriName $suffix" else oriName
                    val singersArr = item.getAsJsonArray("Singers")
                    val singer = singersArr?.map { it.asJsonObject.get("name")?.asString ?: "" }
                        ?.filter { it.isNotBlank() }
                        ?.joinToString("、") ?: ""
                    val album = item.get("AlbumName")?.asString ?: ""
                    val realAlbumId = item.get("AlbumID")?.asString ?: ""
                    val realMixSongId = item.get("MixSongID")?.asString ?: ""
                    val extra = buildKugouExtraFields(fileHash, hqHash, sqHash, resHash) +
                        ("albumId" to realAlbumId) +
                        ("mixSongId" to realMixSongId)
                    val img = item.get("Image")?.asString
                    val pic = img?.takeIf { it.isNotBlank() }
                        ?.replace("{size}", "400")
                        ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it }
                    songs.add(LxSong(
                        source = "kg",
                        songmid = audioId,
                        name = name,
                        singer = singer,
                        albumName = album,
                        interval = duration,
                        pic = pic,
                        hash = fileHash,
                        extraFields = extra,
                    ))
                    for (grpEl in item.getAsJsonArray("Grp") ?: emptyList()) {
                        val grp = grpEl.asJsonObject
                        val grpAudioId = grp.get("Audioid")?.asString ?: continue
                        val grpHash = grp.get("FileHash")?.asString ?: ""
                        val grpDuration = grp.get("Duration")?.asLong ?: 0L
                        val grpHqHash = grp.get("HQFileHash")?.asString ?: ""
                        val grpSqHash = grp.get("SQFileHash")?.asString ?: ""
                        val grpResHash = grp.get("ResFileHash")?.asString ?: ""
                        val grpKey = "$grpAudioId$grpHash"
                        if (grpKey in seen) continue
                        seen.add(grpKey)
                        val grpOriName = grp.get("OriSongName")?.asString ?: ""
                        val grpSuffix = grp.get("Suffix")?.asString ?: ""
                        val grpName = if (grpSuffix.isNotBlank()) "$grpOriName $grpSuffix" else grpOriName
                        val grpSingers = grp.getAsJsonArray("Singers")
                        val grpSinger = grpSingers?.map { it.asJsonObject.get("name")?.asString ?: "" }
                            ?.filter { it.isNotBlank() }
                            ?.joinToString("、") ?: ""
                        val grpAlbum = grp.get("AlbumName")?.asString ?: ""
                        val grpRealAlbumId = grp.get("AlbumID")?.asString
                            ?: item.get("AlbumID")?.asString ?: ""
                        val grpRealMixSongId = grp.get("MixSongID")?.asString ?: ""
                        val grpExtra = buildKugouExtraFields(grpHash, grpHqHash, grpSqHash, grpResHash) +
                            ("albumId" to grpRealAlbumId) +
                            ("mixSongId" to grpRealMixSongId)
                        songs.add(LxSong(
                            source = "kg",
                            songmid = grpAudioId,
                            name = grpName,
                            singer = grpSinger,
                            albumName = grpAlbum,
                            interval = grpDuration,
                            pic = null,
                            hash = grpHash,
                            extraFields = grpExtra,
                        ))
                    }
                }
                songs
            }
        }.getOrElse {
            Timber.w(it, "kugou search failed")
            emptyList()
        }
    }

    private suspend fun searchKugouV3(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "http://mobilecdnbj.kugou.com/api/v3/search/song?version=9108&keyword=$encoded&page=$page&pagesize=30&sver=2&area_code=1&plat=0"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("kugou v3 search HTTP ${resp.code}")
            val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            if (root.get("status")?.takeIf { it.isJsonPrimitive }?.asInt != 1) {
                throw IllegalStateException("kugou v3 search status=${root.get("status")}")
            }
            val info = root.getAsJsonObject("data")?.getAsJsonArray("info")
                ?: throw IllegalStateException("kugou v3 search missing data.info")
            val seen = mutableSetOf<String>()
            val songs = mutableListOf<LxSong>()
            for (el in info) {
                val item = el.asJsonObject
                val audioId = item.get("audio_id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                val fileHash = item.get("hash")?.asString ?: continue
                val dedupeKey = "$audioId$fileHash"
                if (dedupeKey in seen) continue
                seen.add(dedupeKey)
                val name = item.get("songname")?.asString ?: ""
                val singer = item.get("singername")?.asString ?: ""
                val album = item.get("album_name")?.asString ?: ""
                val duration = item.get("duration")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
                val hqHash = item.get("320hash")?.asString ?: ""
                val sqHash = item.get("sqhash")?.asString ?: ""
                val realAlbumId = item.get("album_id")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val realMixSongId = item.get("album_audio_id")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                val extra = buildKugouExtraFields(fileHash.uppercase(), hqHash.uppercase(), sqHash.uppercase(), "") +
                    ("albumId" to realAlbumId) +
                    ("mixSongId" to realMixSongId)
                val cover = item.getAsJsonObject("trans_param")?.get("union_cover")?.asString
                val pic = cover?.takeIf { it.isNotBlank() }
                    ?.replace("{size}", "400")
                    ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it }
                songs.add(LxSong(
                    source = "kg",
                    songmid = audioId,
                    name = name,
                    singer = singer,
                    albumName = album,
                    interval = duration,
                    pic = pic,
                    hash = fileHash.uppercase(),
                    extraFields = extra,
                ))
            }
            songs
        }
    }

    private suspend fun searchQQ(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&format=json&p=$page&n=30"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://y.qq.com/")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val songs = root.getAsJsonObject("data")
                    ?.getAsJsonObject("song")
                    ?.getAsJsonArray("list") ?: return@use emptyList()
                songs.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("songmid")?.asString ?: return@mapNotNull null
                    val name = obj.get("songname")?.asString ?: ""
                    val singerPairs = obj.getAsJsonArray("singer")
                        ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                        ?.map { (it.get("name")?.asString ?: "") to (it.get("mid")?.asString ?: "") }
                        ?.filter { it.first.isNotBlank() }
                        .orEmpty()
                    val singer = singerPairs.joinToString("/") { it.first }
                    val albumObj = obj.getAsJsonObject("album")
                    val album = albumObj?.get("name")?.asString
                        ?: obj.get("albumname")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: ""
                    val albumMid = albumObj?.get("mid")?.asString
                        ?: obj.get("albummid")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: ""
                    val pic = albumMid.takeIf { it.isNotBlank() }
                        ?.let { "https://y.qq.com/music/photo_new/T002R300x300M000${it}.jpg" }
                    val interval = obj.get("interval")?.asLong ?: 0L
                    val strMediaMid = obj.getAsJsonObject("file")?.get("media_mid")?.asString ?: ""
                    val numericId = obj.get("id")?.asString ?: ""
                    val extra = mutableMapOf<String, String>()
                    if (strMediaMid.isNotBlank()) extra["strMediaMid"] = strMediaMid
                    if (albumMid.isNotBlank()) extra["albumMid"] = albumMid
                    if (numericId.isNotBlank()) extra["songId"] = numericId
                    singerPairs.joinToString(",") { it.second }
                        .takeIf { singerPairs.any { p -> p.second.isNotBlank() } }
                        ?.let { extra["artistIds"] = it }
                    LxSong(
                        source = "tx",
                        songmid = id,
                        name = name,
                        singer = singer,
                        albumName = album,
                        interval = interval,
                        pic = pic,
                        extraFields = extra,
                    )
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private suspend fun searchMigu(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val time = System.currentTimeMillis().toString()
        val deviceId = "963B7AA0D21511ED807EE5846EC87D20"
        val signMd5 = "6cdc72a439cef99a3418d2a78aa28c73"
        val signInput = "${keyword}${signMd5}yyapp2d16148780a1dcc7408e06336b98cfd50${deviceId}${time}"
        val sign = md5Hex(signInput)
        val searchSwitch = "%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D"
        val url = "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1&searchSwitch=$searchSwitch&pageSize=30&text=$encoded&pageNo=$page&sort=0&sid=USS"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30")
            .header("uiVersion", "A_music_3.6.1")
            .header("deviceId", deviceId)
            .header("timestamp", time)
            .header("sign", sign)
            .header("channel", "0146921")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val code = root.get("code")?.asString
                if (code != "000000") {
                    Timber.w("migu search error code=$code info=${root.get("info")?.asString}")
                    return@use emptyList()
                }
                val songResultData = root.getAsJsonObject("songResultData") ?: return@use emptyList()
                val resultList = songResultData.getAsJsonArray("resultList") ?: return@use emptyList()
                val seen = mutableSetOf<String>()
                val songs = mutableListOf<LxSong>()
                for (groupEl in resultList) {
                    val group = groupEl.asJsonArray ?: continue
                    for (dataEl in group) {
                        val data = dataEl.asJsonObject
                        val songId = data.get("songId")?.asString ?: continue
                        val copyrightId = data.get("copyrightId")?.asString ?: continue
                        if (songId in seen) continue
                        seen.add(songId)
                        val name = data.get("name")?.asString ?: ""
                        val singerList = data.getAsJsonArray("singerList")
                        val singer = singerList?.map { it.asJsonObject.get("name")?.asString ?: "" }
                            ?.filter { it.isNotBlank() }
                            ?.joinToString("、") ?: ""
                        val album = data.get("album")?.asString ?: ""
                        val img = data.get("img3")?.asString?.takeIf { it.isNotBlank() }
                            ?: data.get("img2")?.asString?.takeIf { it.isNotBlank() }
                            ?: data.get("img1")?.asString?.takeIf { it.isNotBlank() }
                        val pic = img?.let {
                            if (it.startsWith("http://") || it.startsWith("https://")) it
                            else "http://d.musicapp.migu.cn$it"
                        }
                        songs.add(LxSong(
                            source = "mg",
                            songmid = songId,
                            name = name,
                            singer = singer,
                            albumName = album,
                            pic = pic,
                            copyrightId = copyrightId,
                        ))
                    }
                }
                songs
            }
        }.getOrElse {
            Timber.w(it, "migu search failed")
            emptyList()
        }
    }

    internal fun md5Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun searchKuwo(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=${page - 1}&rn=30&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://www.kuwo.cn/")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val abslist = root.getAsJsonArray("abslist") ?: return@use emptyList()
                abslist.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val rid = obj.get("MUSICRID")?.asString?.removePrefix("MUSIC_")
                        ?: obj.get("DC_TARGETID")?.asString
                        ?: return@mapNotNull null
                    val name = decodeHtmlEntities(obj.get("SONGNAME")?.asString ?: obj.get("NAME")?.asString ?: "")
                    val singer = decodeHtmlEntities(obj.get("ARTIST")?.asString ?: "").replace("&", "、")
                    val album = decodeHtmlEntities(obj.get("ALBUM")?.asString ?: "")
                    val durationSec = obj.get("DURATION")?.asLong
                        ?: obj.get("SONGTIME")?.asLong
                        ?: 0L
                    val artistId = obj.get("ARTISTID")?.asString?.takeIf { it.isNotBlank() }
                    LxSong(
                        source = "kw",
                        songmid = rid,
                        name = name,
                        singer = singer,
                        albumName = album,
                        interval = durationSec,
                        pic = null,
                        extraFields = if (artistId != null) mapOf("artistIds" to artistId) else emptyMap(),
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "searchKuwo failed for keyword=$keyword")
            emptyList()
        }
    }


    private suspend fun kwRsSearch(keyword: String, ft: String, pn: Int, rn: Int): com.google.gson.JsonObject? =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=$pn&rn=$rn" +
                "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1&ft=$ft" +
                "&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .header("Referer", "https://www.kuwo.cn/")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    runCatching { JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject }.getOrNull()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "kwRsSearch failed ft=$ft keyword=$keyword")
                null
            }
        }

    suspend fun searchKwArtists(keyword: String, page: Int = 1, limit: Int = 30): Pair<List<LxArtist>, Int> =
        withContext(Dispatchers.IO) {
            val root = kwRsSearch(keyword, "artist", pn = page - 1, rn = limit)
                ?: return@withContext emptyList<LxArtist>() to 0
            val total = root.get("TOTAL")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            val list = root.getAsJsonArray("abslist")?.mapNotNull { el ->
                runCatching {
                    val o = el.asJsonObject
                    val id = jsonStr(o, "ARTISTID") ?: return@runCatching null
                    val picPath = jsonStr(o, "PICPATH")
                    val base = jsonStr(o, "BASEPICPATH")
                    val pic = picPath?.takeIf { it.isNotBlank() }
                        ?.let { (base ?: "http://img1.kuwo.cn/star/starheads/") + it }
                        ?.replace("http://", "https://")
                    LxArtist(
                        id = id,
                        name = decodeHtmlEntities(jsonStr(o, "ARTIST").orEmpty()),
                        pic = pic,
                        briefDesc = jsonStr(o, "desc")?.takeIf { it.isNotBlank() },
                    )
                }.getOrNull()
            } ?: emptyList()
            list to total
        }

    suspend fun searchKwAlbums(keyword: String, page: Int = 1, limit: Int = 30): Pair<List<LxAlbum>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://search.kuwo.cn/r.s?all=$encoded&ft=album&itemset=web_2016&pn=${page - 1}&rn=$limit" +
                "&rformat=json&encoding=utf8&vipver=MUSIC_8.0.3.0&mobi=1"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .header("Referer", "https://www.kuwo.cn/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxAlbum>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val list = root.getAsJsonArray("albumlist")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val id = jsonStr(o, "albumid") ?: return@runCatching null
                            LxAlbum(
                                id = id,
                                name = decodeHtmlEntities(jsonStr(o, "name").orEmpty()),
                                pic = (jsonStr(o, "hts_img") ?: jsonStr(o, "img"))
                                    ?.takeIf { it.isNotBlank() }
                                    ?.replace("http://", "https://"),
                                artistName = decodeHtmlEntities(jsonStr(o, "artist").orEmpty()),
                                size = jsonStr(o, "musiccnt")?.toIntOrNull() ?: 0,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    val total = root.get("total")?.takeIf { it.isJsonPrimitive }?.asInt ?: list.size
                    list to total
                }
            }.getOrElse {
                Timber.w(it, "searchKwAlbums failed keyword=$keyword")
                emptyList<LxAlbum>() to 0
            }
        }

    private fun kwSongFromAbslist(o: com.google.gson.JsonObject): LxSong? {
        val rid = jsonStr(o, "MUSICRID")?.removePrefix("MUSIC_")
            ?: jsonStr(o, "DC_TARGETID")
            ?: return null
        val artistId = jsonStr(o, "ARTISTID")
        val artistIds = artistId?.takeIf { it.isNotBlank() }
        return LxSong(
            source = "kw",
            songmid = rid,
            name = decodeHtmlEntities(jsonStr(o, "SONGNAME") ?: jsonStr(o, "NAME") ?: ""),
            singer = decodeHtmlEntities(jsonStr(o, "ARTIST") ?: "").replace("&", "、"),
            albumName = decodeHtmlEntities(jsonStr(o, "ALBUM") ?: ""),
            interval = o.get("DURATION")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
            pic = null,
            extraFields = if (artistIds != null) mapOf("artistIds" to artistIds) else emptyMap(),
        )
    }

    suspend fun fetchKwArtistSongs(artistId: String, artistName: String, offset: Int = 0, limit: Int = 30): Pair<List<LxSong>, Int> =
        withContext(Dispatchers.IO) {
            val root = kwRsSearch(artistName, "music", pn = offset / limit, rn = limit)
                ?: return@withContext emptyList<LxSong>() to 0
            val raw = root.getAsJsonArray("abslist")?.mapNotNull { el ->
                runCatching { el.asJsonObject }.getOrNull()
            } ?: emptyList()
            val songs = raw.mapNotNull { o ->
                // 合作曲的 ARTISTID 里只有主歌手查 allartistid
                val allIds = jsonStr(o, "allartistid")?.split('&').orEmpty()
                if (artistId !in allIds && jsonStr(o, "ARTISTID") != artistId) return@mapNotNull null
                runCatching { kwSongFromAbslist(o) }.getOrNull()
            }
            val total = if (raw.size >= limit) offset + limit + 1 else offset + songs.size
            songs to total
        }

    suspend fun fetchKwArtistDetail(artistId: String, artistName: String): LxArtistDetail? =
        withContext(Dispatchers.IO) {
            val root = kwRsSearch(artistName, "artist", pn = 0, rn = 30) ?: return@withContext null
            val o = root.getAsJsonArray("abslist")?.firstOrNull { el ->
                runCatching { jsonStr(el.asJsonObject, "ARTISTID") == artistId }.getOrDefault(false)
            }?.asJsonObject ?: return@withContext null
            runCatching {
                val picPath = jsonStr(o, "PICPATH")
                val base = jsonStr(o, "BASEPICPATH")
                LxArtistDetail(
                    artist = LxArtist(
                        id = artistId,
                        name = decodeHtmlEntities(jsonStr(o, "ARTIST").orEmpty()),
                        pic = picPath?.takeIf { it.isNotBlank() }
                            ?.let { (base ?: "http://img1.kuwo.cn/star/starheads/") + it }
                            ?.replace("http://", "https://"),
                        briefDesc = jsonStr(o, "desc")?.takeIf { it.isNotBlank() },
                    ),
                    followed = null,
                )
            }.getOrNull()
        }

    suspend fun fetchKwArtistAlbums(artistId: String, artistName: String, offset: Int = 0, limit: Int = 30): List<LxAlbum> =
        withContext(Dispatchers.IO) {
            val url = "https://search.kuwo.cn/r.s?artistid=$artistId&stype=albumlist&pn=${offset / limit}&rn=$limit" +
                "&rformat=json&encoding=utf8&mobi=1"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .header("Referer", "https://www.kuwo.cn/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    root.getAsJsonArray("albumlist")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            LxAlbum(
                                id = jsonStr(o, "albumid") ?: return@runCatching null,
                                name = decodeHtmlEntities(jsonStr(o, "name").orEmpty()),
                                pic = (jsonStr(o, "hts_img") ?: jsonStr(o, "img"))
                                    ?.takeIf { it.isNotBlank() }
                                    ?.replace("http://", "https://"),
                                artistName = decodeHtmlEntities(jsonStr(o, "artist").orEmpty()),
                                size = jsonStr(o, "musiccnt")?.toIntOrNull() ?: 0,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                }
            }.getOrElse {
                Timber.w(it, "fetchKwArtistAlbums failed artistId=$artistId")
                emptyList()
            }
        }

    suspend fun fetchKwAlbumDetail(albumId: String, albumName: String): Pair<LxAlbum?, List<LxSong>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://search.kuwo.cn/r.s?pn=0&rn=100&albumid=$albumId&stype=albuminfo" +
                    "&rformat=json&encoding=utf8&vipver=MUSIC_9.3.1.2&plat=pc&devid=0&vermerge=1&mobi=1"
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .header("Referer", "https://www.kuwo.cn/")
                    .get()
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val list = root.getAsJsonArray("musiclist")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val rid = jsonStr(o, "musicrid")?.removePrefix("MUSIC_")
                                ?: return@runCatching null
                            val kwArtistId = jsonStr(o, "artistid")?.takeIf { it.isNotBlank() }
                            LxSong(
                                source = "kw",
                                songmid = rid,
                                name = decodeHtmlEntities(jsonStr(o, "name") ?: ""),
                                singer = decodeHtmlEntities(jsonStr(o, "artist") ?: "").replace("&", "、"),
                                albumName = decodeHtmlEntities(jsonStr(o, "album") ?: albumName),
                                interval = o.get("duration")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                                pic = null,
                                extraFields = if (kwArtistId != null) mapOf("artistIds" to kwArtistId) else emptyMap(),
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    if (list.isNotEmpty()) return@use (null to list)
                    null
                }
            }.getOrNull()?.let { return@withContext it }

            val query = albumName.take(24).ifBlank { albumId }
            var root = kwRsSearch(query, "music", pn = 0, rn = 100)
            var songs = emptyList<LxSong>()
            if (root != null) {
                songs = root.getAsJsonArray("abslist")?.mapNotNull { el ->
                    runCatching {
                        val o = el.asJsonObject
                        if (jsonStr(o, "ALBUMID") != albumId) return@runCatching null
                        kwSongFromAbslist(o)
                    }.getOrNull()
                } ?: emptyList()
            }
            if (songs.isNotEmpty() && songs.size >= 95) {
                var pn = 1
                while (pn < 5) {
                    root = kwRsSearch(query, "music", pn = pn, rn = 100) ?: break
                    val batch = root.getAsJsonArray("abslist")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            if (jsonStr(o, "ALBUMID") != albumId) return@runCatching null
                            kwSongFromAbslist(o)
                        }.getOrNull()
                    } ?: emptyList()
                    if (batch.isEmpty()) break
                    songs = (songs + batch).distinctBy { it.songmid }
                    pn++
                }
            }
            null to songs
        }

    suspend fun fetchPic(song: LxSong): String? = withContext(Dispatchers.IO) {
        song.pic?.takeIf { it.isNotBlank() } ?: when (song.source) {
            "wy" -> fetchNeteasePic(song.songmid)
            "kw" -> fetchKuwoPic(song.songmid)
            "kg" -> fetchKugouPic(song)
            else -> null
        }
    }

    private suspend fun fetchNeteasePic(songmid: String): String? {
        // 1. v3/song/detail POST
        val v3Result = fetchNeteasePicV3(songmid)
        if (v3Result != null) return v3Result
        return fetchNeteasePicLegacy(songmid)
    }

    private suspend fun fetchNeteasePicV3(songmid: String): String? {
        val cParam = URLEncoder.encode("[{\"id\":\"$songmid\"}]", "UTF-8")
        val idsParam = URLEncoder.encode("[$songmid]", "UTF-8")
        val form = "c=$cParam&ids=$idsParam"
        val req = Request.Builder().url("https://music.163.com/api/v3/song/detail")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("fetchNeteasePicV3: songmid=$songmid HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val song = root.getAsJsonArray("songs")?.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                if (song == null) {
                    Timber.w("fetchNeteasePicV3: songmid=$songmid no songs (body=${body.take(150)})")
                    return@use null
                }
                val albumObj = song.getAsJsonObject("al") ?: song.getAsJsonObject("album")
                val picUrl = jsonStr(albumObj, "picUrl") ?: jsonStr(albumObj, "pic_str")
                picUrl?.takeIf { it.isNotBlank() }?.let { normalizeNeteasePicUrl(it) }
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteasePicV3 failed for songmid=$songmid")
            null
        }
    }

    private suspend fun fetchNeteasePicLegacy(songmid: String): String? {
        val url = "https://music.163.com/api/song/detail/?ids=%5B$songmid%5D"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Timber.w("fetchNeteasePicLegacy: songmid=$songmid HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body?.string().orEmpty()
                val root = JsonParser.parseString(body).asJsonObject
                val song = root.getAsJsonArray("songs")?.firstOrNull()
                    ?.takeIf { it.isJsonObject }?.asJsonObject
                if (song == null) {
                    Timber.w("fetchNeteasePicLegacy: songmid=$songmid no songs (body=${body.take(150)})")
                    return@use null
                }
                val albumObj = song.getAsJsonObject("album") ?: song.getAsJsonObject("al")
                val picUrl = jsonStr(albumObj, "picUrl") ?: jsonStr(albumObj, "pic_str")
                picUrl?.takeIf { it.isNotBlank() }?.let { normalizeNeteasePicUrl(it) }
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteasePicLegacy failed for songmid=$songmid")
            null
        }
    }

    internal fun jsonStr(obj: com.google.gson.JsonObject?, key: String): String? {
        val el = obj?.get(key) ?: return null
        if (el.isJsonNull) return null
        if (!el.isJsonPrimitive) return null
        return runCatching { el.asString }.getOrNull()
    }

    private fun normalizeNeteasePicUrl(raw: String): String? = when {
        raw.startsWith("https://") -> raw
        raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
        raw.startsWith("//") -> "https:$raw"
        else -> null
    }
    private suspend fun fetchKuwoPic(songmid: String): String? {
        val url = "http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=${songmid.removePrefix("MUSIC_")}"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string()?.trim().orEmpty()
                text.takeIf { resp.isSuccessful && it.startsWith("http") }
            }
        }.getOrElse {
            Timber.w(it, "fetchKuwoPic failed for songmid=$songmid")
            null
        }
    }

    private val kgAlbumPicCache = java.util.concurrent.ConcurrentHashMap<String, String?>()
    private suspend fun fetchKugouPic(song: LxSong): String? {
        val albumId = song.extraFields["albumId"]?.takeIf { it.isNotBlank() } ?: return null
        kgAlbumPicCache[albumId]?.let { return it }
        return runCatching {
            val url = "https://mobiles.kugou.com/api/v3/album/info?albumid=$albumId"
            okHttpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val data = root.getAsJsonObject("data") ?: return@use null
                val pic = jsonStr(data, "imgurl")?.takeIf { it.isNotBlank() }
                    ?.replace("{size}", "400")
                    ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it }
                if (pic != null) kgAlbumPicCache[albumId] = pic
                pic
            }
        }.getOrElse {
            Timber.w(it, "fetchKugouPic failed for albumId=$albumId")
            null
        }
    }

    internal fun decodeHtmlEntities(s: String): String {
        if ('&' !in s) return s
        return s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#\\d+;".toRegex()) { match ->
                val code = match.value.removePrefix("&#").removeSuffix(";")
                code.toIntOrNull()?.let { it.toChar().toString() } ?: match.value
            }
    }

    private suspend fun searchNetease(keyword: String, page: Int): List<LxSong> {
        val web = searchNeteaseWeb(keyword, page)
        if (web.isNotEmpty()) {
            Timber.d("searchNetease: used=web keyword=$keyword count=${web.size} withPic=${web.count { !it.pic.isNullOrBlank() }}")
            return web
        }
        val cs = searchNeteaseCloudsearch(keyword, page)
        Timber.d("searchNetease: used=cloudsearch(web empty) keyword=$keyword count=${cs.size} withPic=${cs.count { !it.pic.isNullOrBlank() }}")
        return cs
    }

    private suspend fun searchNeteaseWeb(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val offset = (page - 1) * 30
        val form = "s=$encoded&type=1&offset=$offset&limit=30&total=true"
        val req = Request.Builder().url("https://music.163.com/api/search/get/web")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                parseNeteaseSearchResult(root)
            }
        }.getOrElse {
            Timber.w(it, "searchNeteaseWeb failed keyword=$keyword")
            emptyList()
        }
    }

    private suspend fun searchNeteaseCloudsearch(keyword: String, page: Int): List<LxSong> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val offset = (page - 1) * 30
        val url = "https://music.163.com/api/cloudsearch/pc?s=$encoded&type=1&offset=$offset&limit=30"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                parseNeteaseSearchResult(root)
            }
        }.getOrElse {
            Timber.w(it, "searchNeteaseCloudsearch failed keyword=$keyword")
            emptyList()
        }.also { list ->
            Timber.d("searchNetease: keyword=$keyword results=${list.size}")
        }
    }

    private fun parseNeteaseSearchResult(root: com.google.gson.JsonObject): List<LxSong> {
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val songs = result.getAsJsonArray("songs") ?: return emptyList()
        return songs.mapNotNull { el -> parseNeteaseSongItem(el.asJsonObject) }
    }

    private fun parseNeteaseSongItem(
        obj: com.google.gson.JsonObject,
        payedById: Map<String, Boolean> = emptyMap(),
    ): LxSong? {
        val id = jsonStr(obj, "id") ?: return null
        val name = jsonStr(obj, "name") ?: ""
        val artistPairs = (obj.getAsJsonArray("ar") ?: obj.getAsJsonArray("artists"))
            ?.mapNotNull { el ->
                runCatching { el.asJsonObject }.getOrNull()
            }
            ?.map { (jsonStr(it, "name") ?: "") to (jsonStr(it, "id") ?: "") }
            ?.filter { it.first.isNotBlank() }
            .orEmpty()
        val artists = artistPairs.joinToString("/") { it.first }
        val artistIds = artistPairs.joinToString(",") { it.second }.takeIf { artistPairs.any { p -> p.second.isNotBlank() } }
        val albumObj = obj.getAsJsonObject("al") ?: obj.getAsJsonObject("album")
        val album = jsonStr(albumObj, "name") ?: ""
        val picRaw = jsonStr(albumObj, "picUrl") ?: jsonStr(albumObj, "pic_str") ?: ""
        val pic = normalizeNeteasePicUrl(picRaw)
        val durationMs = (obj.get("dt") ?: obj.get("duration"))?.let {
            if (it.isJsonNull || !it.isJsonPrimitive) 0L
            else runCatching { it.asLong }.getOrDefault(0L)
        } ?: 0L
        val (qualityList, typesJson) = buildNeteaseQualityInfo(obj)
        val extra = buildMap {
            if (typesJson != null) put("_types", typesJson)
            obj.get("fee")?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.let {
                put("fee", it.asInt.toString())
            }
            jsonStr(albumObj, "id")?.let { put("albumId", it) }
            artistIds?.let { put("artistIds", it) }
            obj.get("noCopyrightRcmd")?.takeIf { !it.isJsonNull }?.let { put("noCopyright", "1") }
            if (payedById[id] == true) put("payed", "1")
        }
        return LxSong(
            source = "wy",
            songmid = id,
            name = name,
            singer = artists,
            albumName = album,
            interval = durationMs / 1000,
            pic = pic,
            qualitys = qualityList,
            extraFields = extra,
        )
    }

    private fun parseNeteasePayedMap(privileges: com.google.gson.JsonArray?): Map<String, Boolean> =
        privileges?.mapNotNull { el ->
            runCatching {
                val p = el.asJsonObject
                val pid = jsonStr(p, "id") ?: return@runCatching null
                val payed = p.get("payed")?.takeIf { it.isJsonPrimitive }?.asInt == 1
                pid to payed
            }.getOrNull()
        }?.toMap().orEmpty()


    private val neteaseBoards = listOf(
        LxBoard("wybsb", "飙升榜", "19723756"),
        LxBoard("wyrgb", "热歌榜", "3778678"),
        LxBoard("wyxgb", "新歌榜", "3779629"),
        LxBoard("wyycb", "原创榜", "2884035"),
        LxBoard("wygdb", "古典榜", "71384707"),
        LxBoard("wydianyb", "电音榜", "1978921795"),
        LxBoard("wyhyb", "韩语榜", "745956260"),
        LxBoard("wyktvbb", "KTV唛榜", "21845217"),
        LxBoard("wydouyb", "抖音榜", "2250011882"),
        LxBoard("wydjb", "电竞榜", "2006508653"),
    )

    private val kuwoBoards = listOf(
        LxBoard("kwbsb", "飙升榜", "93"),
        LxBoard("kwxgb", "新歌榜", "17"),
        LxBoard("kwrgb", "热歌榜", "16"),
        LxBoard("kwdyrgb", "抖音热歌榜", "158"),
        LxBoard("kwrpb", "热评榜", "284"),
        LxBoard("kwlsb", "铃声榜", "292"),
        LxBoard("kwacgx", "ACG新歌榜", "290"),
        LxBoard("kwktv", "KTV点唱榜", "255"),
        LxBoard("kwgf", "古风音乐榜", "278"),
        LxBoard("kwvlog", "Vlog音乐榜", "264"),
        LxBoard("kwdyb", "电音榜", "242"),
        LxBoard("kwqsb", "流行趋势榜", "187"),
        LxBoard("kwacgs", "ACG神曲榜", "186"),
        LxBoard("kwqcfcb", "最强翻唱榜", "185"),
        LxBoard("kwhjb", "经典怀旧榜", "26"),
        LxBoard("kwthy", "华语榜", "104"),
        LxBoard("kwyqb", "粤语榜", "182"),
        LxBoard("kwomb", "欧美榜", "22"),
        LxBoard("kwhyb", "韩语榜", "184"),
        LxBoard("kwryb", "日语榜", "183"),
        LxBoard("kwhycb", "会员畅听榜", "145"),
        LxBoard("kwysjq", "影视金曲榜", "64"),
        LxBoard("kwdjdg", "DJ嗨歌榜", "176"),
    )

    private val kugouBoards = listOf(
        LxBoard("kgtop", "TOP500", "8888"),
        LxBoard("kgbsb", "飙升榜", "6666"),
        LxBoard("kgwlhgb", "网络红歌榜", "23784"),
        LxBoard("kgdyrgb", "抖音热歌榜", "52144"),
        LxBoard("kgkshgb", "快手热歌榜", "52767"),
        LxBoard("kgdjrgb", "DJ热歌榜", "24971"),
        LxBoard("kgsxxf", "说唱先锋榜", "44412"),
        LxBoard("kgndb", "内地榜", "31308"),
        LxBoard("kgdyb", "电音榜", "33160"),
        LxBoard("kgxgb", "香港地区榜", "31313"),
        LxBoard("kgmyb", "民谣榜", "51341"),
        LxBoard("kgtwb", "台湾地区榜", "54848"),
        LxBoard("kgomb", "欧美榜", "31310"),
        LxBoard("kgacgx", "ACG新歌榜", "33162"),
        LxBoard("kghgb", "韩国榜", "31311"),
        LxBoard("kgrbb", "日本榜", "31312"),
        LxBoard("kg80h", "80后热歌榜", "49225"),
        LxBoard("kg90h", "90后热歌榜", "49223"),
        LxBoard("kg00h", "00后热歌榜", "49224"),
        LxBoard("kgyyjq", "粤语金曲榜", "33165"),
        LxBoard("kgomjq", "欧美金曲榜", "33166"),
        LxBoard("kgysjq", "影视金曲榜", "33163"),
        LxBoard("kgsgb", "伤感榜", "51340"),
        LxBoard("kggfxgb", "古风新歌榜", "33161"),
        LxBoard("kgrbb2", "R&B榜", "59895"),
        LxBoard("kgygb", "摇滚榜", "59896"),
        LxBoard("kgjjb", "爵士榜", "59897"),
        LxBoard("kgcyyb", "纯音乐榜", "59900"),
    )

    private val tencentBoards = listOf(
        LxBoard("txlxzsb", "流行指数榜", "4"),
        LxBoard("txrgb", "热歌榜", "26"),
        LxBoard("txxgb", "新歌榜", "27"),
        LxBoard("txbsb", "飙升榜", "62"),
        LxBoard("txscb", "说唱榜", "58"),
        LxBoard("txxlb", "喜力电音榜", "57"),
        LxBoard("txwlhgb", "网络歌曲榜", "28"),
        LxBoard("txndb", "内地榜", "5"),
        LxBoard("txomb", "欧美榜", "3"),
        LxBoard("txxgqb", "香港地区榜", "59"),
        LxBoard("txhgb", "韩国榜", "16"),
        LxBoard("txdykb", "抖快榜", "60"),
        LxBoard("txysjqb", "影视金曲榜", "29"),
        LxBoard("txrbb", "日本榜", "17"),
        LxBoard("txkgjqb", "K歌金曲榜", "36"),
        LxBoard("txtwb", "台湾地区榜", "61"),
        LxBoard("txdjb", "DJ舞曲榜", "63"),
        LxBoard("txzyxgb", "综艺新歌榜", "64"),
        LxBoard("txgfrgb", "国风热歌榜", "65"),
        LxBoard("txdmyyb", "动漫音乐榜", "72"),
        LxBoard("txyxb", "游戏音乐榜", "73"),
        LxBoard("txysb2", "有声榜", "75"),
    )

    suspend fun persistLxSong(song: LxSong) = withContext(Dispatchers.IO) {
        val entity = LxSongMapper.toSongEntity(song)
        ensureArtistAndAlbum(entity)
        val existing = musicDao.getSongIdByContentUri(entity.contentUriString)
        if (existing != null) {
            musicDao.updateSongs(listOf(entity))
        } else {
            musicDao.insertSongsIgnoreConflicts(listOf(entity))
        }
        ensureSongArtistCrossRef(entity)
    }

    suspend fun persistLxSongs(songs: List<LxSong>) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        val entities = songs.map { LxSongMapper.toSongEntity(it) }
        entities.forEach { ensureArtistAndAlbum(it) }
        musicDao.insertSongs(entities)
        musicDao.insertSongArtistCrossRefs(
            entities.map {
                SongArtistCrossRef(songId = it.id, artistId = it.artistId, isPrimary = true)
            }
        )
    }

    private suspend fun ensureSongArtistCrossRef(entity: SongEntity) {
        musicDao.insertSongArtistCrossRefs(
            listOf(SongArtistCrossRef(songId = entity.id, artistId = entity.artistId, isPrimary = true))
        )
    }

    private suspend fun ensureArtistAndAlbum(entity: SongEntity) {
        musicDao.insertArtistsIgnoreConflicts(
            listOf(
                ArtistEntity(
                    id = entity.artistId,
                    name = entity.artistName,
                    trackCount = 0,
                )
            )
        )
        musicDao.insertAlbums(
            listOf(
                AlbumEntity(
                    id = entity.albumId,
                    title = entity.albumName,
                    artistName = entity.artistName.ifBlank { "未知艺术家" },
                    artistId = entity.artistId,
                    albumArtUriString = entity.albumArtUriString,
                    songCount = 0,
                    dateAdded = entity.dateAdded,
                    year = 0,
                    albumArtist = entity.albumArtist ?: entity.artistName.ifBlank { null },
                )
            )
        )
    }

    fun getBoards(source: String): List<LxBoard> = when (source) {
        "wy" -> neteaseBoards
        "kw" -> kuwoBoards
        "kg" -> kugouBoards
        "tx" -> tencentBoards
        else -> emptyList()
    }

    suspend fun fetchBoardSongs(source: String, bangid: String): List<LxSong> = when (source) {
        "wy" -> fetchNeteasePlaylistSongs(bangid).first
        "kw" -> fetchKwBoardSongs(bangid)
        "kg" -> fetchKgBoardSongs(bangid)
        "tx" -> fetchTxBoardSongs(bangid)
        else -> emptyList()
    }

    private suspend fun fetchKwBoardSongs(bangid: String): List<LxSong> = withContext(Dispatchers.IO) {
        val url = "http://kbangserver.kuwo.cn/ksong.s?from=pc&fmt=json&pn=0&rn=100&type=bang&data=content" +
            "&id=$bangid&show_copyright_off=0&pcmp4=1&isbang=1"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val musiclist = root.getAsJsonArray("musiclist") ?: return@use emptyList()
                musiclist.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    val durationSec = obj.get("song_duration")?.takeIf { it.isJsonPrimitive }
                        ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
                    LxSong(
                        source = "kw",
                        songmid = id,
                        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        singer = (obj.get("artist")?.takeIf { it.isJsonPrimitive }?.asString ?: "")
                            .replace("&", "、"),
                        albumName = obj.get("album")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        interval = durationSec,
                        pic = null,
                        qualitys = listOf("320k", "128k"),
                        extraFields = buildMap {
                            put("albumId", obj.get("albumid")?.takeIf { it.isJsonPrimitive }?.asString ?: "")
                            obj.get("artistid")?.takeIf { it.isJsonPrimitive }?.asString
                                ?.takeIf { it.isNotBlank() }?.let { put("artistIds", it) }
                        },
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchKwBoardSongs failed bangid=$bangid")
            emptyList()
        }.also { list ->
            Timber.d("fetchKwBoardSongs: bangid=$bangid songs=${list.size}")
        }
    }

    private suspend fun fetchKgBoardSongs(bangid: String): List<LxSong> = withContext(Dispatchers.IO) {
        val url = "http://mobilecdnbj.kugou.com/api/v3/rank/song?version=9108&ranktype=1&plat=0&pagesize=100" +
            "&area_code=1&page=1&rankid=$bangid&with_res_tag=0&show_portrait_mv=1"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("errcode")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0) != 0) return@use emptyList()
                val info = root.getAsJsonObject("data")?.getAsJsonArray("info") ?: return@use emptyList()
                info.mapNotNull { el -> if (el.isJsonObject) parseKgSongInfo(el.asJsonObject) else null }
            }
        }.getOrElse {
            Timber.w(it, "fetchKgBoardSongs failed bangid=$bangid")
            emptyList()
        }.also { list ->
            Timber.d("fetchKgBoardSongs: bangid=$bangid songs=${list.size}")
        }
    }

    private suspend fun fetchTxBoardSongs(bangid: String): List<LxSong> = withContext(Dispatchers.IO) {
        val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?g_tk=1928093487&uin=0&format=json" +
            "&inCharset=utf-8&outCharset=utf-8&notice=0&platform=h5&needNewCode=1&cmd=topinfo&topid=$bangid" +
            "&song_begin=0&song_num=100"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://y.qq.com/")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val songlist = root.getAsJsonArray("songlist") ?: return@use emptyList()
                songlist.mapNotNull { el ->
                    val data = el.asJsonObject?.getAsJsonObject("data") ?: return@mapNotNull null
                    parseTxTopSong(data)
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchTxBoardSongs failed bangid=$bangid")
            emptyList()
        }.also { list ->
            Timber.d("fetchTxBoardSongs: bangid=$bangid songs=${list.size}")
        }
    }

    private fun parseKgSongInfo(item: com.google.gson.JsonObject): LxSong? {
        val hash = item.get("hash")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val audioId = item.get("audio_id")?.takeIf { it.isJsonPrimitive }?.asString
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?: item.get("album_audio_id")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() && it != "0" }
            ?: hash
        var name = item.get("songname")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val authorPairs = item.getAsJsonArray("authors")
            ?.mapNotNull { el -> runCatching { el.asJsonObject }.getOrNull() }
            ?.map { (it.get("author_name")?.asString ?: "") to (it.get("author_id")?.takeIf { a -> a.isJsonPrimitive }?.asString ?: "") }
            ?.filter { it.first.isNotBlank() }
            .orEmpty()
        var singer = authorPairs.joinToString("、") { it.first }
        if (name.isBlank() || singer.isBlank()) {
            val fn = item.get("filename")?.takeIf { it.isJsonPrimitive }?.asString
            if (!fn.isNullOrBlank()) {
                val cleaned = fn.trim()
                    .replace(Regex("\\.(mp3|flac|m4a|aac|ogg|wav|ape)$", RegexOption.IGNORE_CASE), "")
                val sep = cleaned.indexOf(" - ")
                if (sep >= 0) {
                    val front = cleaned.substring(0, sep).trim()
                    val back = cleaned.substring(sep + 3).trim()
                    if (singer.isBlank()) singer = front
                    if (name.isBlank()) name = back
                } else if (name.isBlank()) {
                    name = cleaned
                }
            }
        }
        if (name.isBlank()) return null
        val albumName = item.get("remark")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val albumId = item.get("album_id")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val duration = item.get("duration")?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
        // 酷狗每档音质各带一对 filesize+hash，size 非 0 才算真有这档
        fun hashOf(sizeField: String, hashField: String): String {
            val size = item.get(sizeField)?.takeIf { it.isJsonPrimitive }
                ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
            val h = item.get(hashField)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
            return if (size != 0L && h.isNotBlank()) h else ""
        }
        val hash320 = hashOf("320filesize", "320hash")
        val hashFlac = hashOf("sqfilesize", "sqhash")
        val hashHigh = hashOf("filesize_high", "hash_high")
        val artistIds = authorPairs.joinToString(",") { it.second }
            .takeIf { authorPairs.any { p -> p.second.isNotBlank() } }
        val extra = buildKugouExtraFields(hash, hash320, hashFlac, hashHigh) +
            ("albumId" to albumId) +
            (artistIds?.let { mapOf("artistIds" to it) } ?: emptyMap())
        return LxSong(
            source = "kg",
            songmid = audioId,
            name = name,
            singer = singer,
            albumName = albumName,
            interval = duration,
            pic = null,
            hash = hash,
            extraFields = extra,
        )
    }

    private fun parseTxTopSong(data: com.google.gson.JsonObject): LxSong? {
        val songmid = data.get("songmid")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val name = data.get("songname")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val singerPairs = data.getAsJsonArray("singer")
            ?.mapNotNull { el -> runCatching { el.asJsonObject }.getOrNull() }
            ?.map { (it.get("name")?.asString ?: "") to (it.get("mid")?.asString ?: "") }
            ?.filter { it.first.isNotBlank() }
            .orEmpty()
        val singer = singerPairs.joinToString("/") { it.first }
        val artistIds = singerPairs.joinToString(",") { it.second }
            .takeIf { singerPairs.any { p -> p.second.isNotBlank() } }
        val albumName = data.get("albumname")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val albumMid = data.get("albummid")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val interval = data.get("interval")?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
        val strMediaMid = data.get("strMediaMid")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        fun sizeOf(field: String): Long = data.get(field)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
        val (qualitys, typesJson) = buildTxQualityInfo(
            size128 = sizeOf("size128"),
            size320 = sizeOf("size320"),
            sizeFlac = sizeOf("sizeflac"),
            sizeHires = 0L,
        )
        val extra = buildMap {
            if (strMediaMid.isNotBlank()) put("strMediaMid", strMediaMid)
            if (albumMid.isNotBlank()) put("albumMid", albumMid)
            artistIds?.let { put("artistIds", it) }
            typesJson?.let { put("_types", it) }
        }
        val pic = if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg" else null
        return LxSong(
            source = "tx",
            songmid = songmid,
            name = name,
            singer = singer,
            albumName = albumName,
            interval = interval,
            pic = pic,
            qualitys = qualitys,
            extraFields = extra,
        )
    }

    internal fun parseTxPlaylistSong(item: com.google.gson.JsonObject): LxSong? {
        val songmid = item.get("mid")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val name = item.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        val singerPairs = item.getAsJsonArray("singer")
            ?.mapNotNull { el -> runCatching { el.asJsonObject }.getOrNull() }
            ?.map { (it.get("name")?.asString ?: "") to (it.get("mid")?.asString ?: "") }
            ?.filter { it.first.isNotBlank() }
            .orEmpty()
        val singer = singerPairs.joinToString("/") { it.first }
        val artistIds = singerPairs.joinToString(",") { it.second }
            .takeIf { singerPairs.any { p -> p.second.isNotBlank() } }
        val albumObj = item.getAsJsonObject("album")
        val albumName = albumObj?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            ?: item.get("albumname")?.takeIf { it.isJsonPrimitive }?.asString
            ?: ""
        val albumMid = albumObj?.get("mid")?.takeIf { it.isJsonPrimitive }?.asString
            ?: item.get("albummid")?.takeIf { it.isJsonPrimitive }?.asString
            ?: ""
        val interval = item.get("interval")?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
        val file = item.getAsJsonObject("file")
        val strMediaMid = file?.get("media_mid")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        fun sizeOf(field: String): Long = file?.get(field)?.takeIf { it.isJsonPrimitive }
            ?.let { runCatching { it.asLong }.getOrNull() } ?: 0L
        val (qualitys, typesJson) = buildTxQualityInfo(
            size128 = sizeOf("size_128mp3"),
            size320 = sizeOf("size_320mp3"),
            sizeFlac = sizeOf("size_flac"),
            sizeHires = sizeOf("size_hires"),
        )
        val extra = buildMap {
            if (strMediaMid.isNotBlank()) put("strMediaMid", strMediaMid)
            if (albumMid.isNotBlank()) put("albumMid", albumMid)
            artistIds?.let { put("artistIds", it) }
            typesJson?.let { put("_types", it) }
        }
        val pic = if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000$albumMid.jpg" else null
        return LxSong(
            source = "tx",
            songmid = songmid,
            name = name,
            singer = singer,
            albumName = albumName,
            interval = interval,
            pic = pic,
            qualitys = qualitys,
            extraFields = extra,
        )
    }

    private fun buildTxQualityInfo(size128: Long, size320: Long, sizeFlac: Long, sizeHires: Long): Pair<List<String>, String?> {
        val typesObj = com.google.gson.JsonObject()
        val qualityList = mutableListOf<String>()
        fun addQuality(key: String, size: Long) {
            if (size <= 0L) return
            typesObj.add(key, com.google.gson.JsonObject().apply { addProperty("size", sizeFormate(size)) })
            qualityList.add(key)
        }
        addQuality("128k", size128)
        addQuality("320k", size320)
        addQuality("flac", sizeFlac)
        addQuality("flac24bit", sizeHires)
        qualityList.reverse()
        val typesJson = typesObj.takeIf { it.size() > 0 }?.toString()
        return qualityList.toList() to typesJson
    }


    internal fun extractCookieValue(cookie: String?, key: String): String? {
        if (cookie.isNullOrBlank()) return null
        return Regex("(?:^|;\\s*)" + Regex.escape(key) + "=([^;]+)")
            .find(cookie)?.groupValues?.getOrNull(1)?.trim()
    }

    internal fun jsonStringOf(obj: com.google.gson.JsonObject, vararg keys: String): String? {
        for (key in keys) {
            obj.get(key)?.takeIf { it.isJsonPrimitive && !it.isJsonNull }?.asString
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    val neteasePlaylistTags = listOf(
        "华语", "流行", "摇滚", "民谣", "电子", "说唱", "古风", "ACG",
        "轻音乐", "影视原声", "爵士", "R&B", "金属", "朋克", "校园",
    )

    suspend fun fetchNeteasePlaylistSongs(playlistId: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> = withContext(Dispatchers.IO) {
        val v6 = runCatching { fetchNeteasePlaylistSongsV6(playlistId, page, pageSize) }
            .onFailure { Timber.w(it, "fetchNeteasePlaylistSongsV6 failed id=$playlistId") }
            .getOrDefault(emptyList<LxSong>() to false)
        if (v6.first.isNotEmpty() || page > 1) return@withContext v6
        val legacy = fetchNeteasePlaylistSongsLegacy(playlistId)
        legacy to false
    }

    private data class NeteasePlaylistDetailCache(
        val playlistId: String,
        val trackIds: List<String>,
        val directMap: Map<String, LxSong>,
    )
    @Volatile private var neteasePlaylistDetailCache: NeteasePlaylistDetailCache? = null

    fun invalidateNeteasePlaylistDetailCache() {
        neteasePlaylistDetailCache = null
    }

    fun removeTrackFromNeteasePlaylistCache(playlistId: String, trackId: String) {
        val cached = neteasePlaylistDetailCache ?: return
        if (cached.playlistId != playlistId) return
        if (trackId !in cached.trackIds && trackId !in cached.directMap.keys) return
        neteasePlaylistDetailCache = cached.copy(
            trackIds = cached.trackIds - trackId,
            directMap = cached.directMap - trackId,
        )
    }

    private suspend fun fetchNeteasePlaylistSongsV6(playlistId: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> {
        val cached = neteasePlaylistDetailCache?.takeIf { it.playlistId == playlistId }
        val trackIds: List<String>
        val directMap: Map<String, LxSong>
        if (cached != null) {
            trackIds = cached.trackIds
            directMap = cached.directMap
        } else {
            val url = "https://music.163.com/api/v6/playlist/detail?id=$playlistId&n=1000"
            val cookie = neteaseCookieStore.getCookie()?.takeIf { it.isNotBlank() }
                ?: "os=pc; appver=2.5.2.197409"
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", "https://music.163.com/")
                .header("Cookie", cookie)
                .get()
                .build()
            val playlist = okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList<LxSong>() to false
                JsonParser.parseString(resp.body?.string().orEmpty())
                    .asJsonObject.getAsJsonObject("playlist") ?: return emptyList<LxSong>() to false
            }
            val payedById = parseNeteasePayedMap(playlist.getAsJsonArray("privileges"))
            trackIds = playlist.getAsJsonArray("trackIds")
                ?.mapNotNull { el ->
                    el.asJsonObject.get("id")?.takeIf { it.isJsonPrimitive }?.asString
                }
                .orEmpty()
            if (trackIds.isEmpty()) return emptyList<LxSong>() to false

            directMap = playlist.getAsJsonArray("tracks")
                ?.mapNotNull { el -> if (el.isJsonObject) parseNeteaseSongItem(el.asJsonObject, payedById) else null }
                .orEmpty()
                .associateBy { it.songmid }
            neteasePlaylistDetailCache = NeteasePlaylistDetailCache(playlistId, trackIds, directMap)
        }

        val fromIndex = (page - 1).coerceAtLeast(0) * pageSize
        val toIndex = if (pageSize == Int.MAX_VALUE) trackIds.size else (fromIndex + pageSize).coerceAtMost(trackIds.size)
        val pageIds = trackIds.subList(fromIndex.coerceAtMost(trackIds.size), toIndex.coerceAtMost(trackIds.size))
        val hasMore = toIndex < trackIds.size

        val missingInPage = pageIds.filter { it !in directMap.keys }
        val extra = missingInPage.chunked(NETEASE_SONG_DETAIL_BATCH).flatMap { batch ->
            runCatching { fetchNeteaseSongsByIds(batch) }.getOrElse { emptyList() }
        }
        val extraMap = extra.associateBy { it.songmid }

        val merged = LinkedHashMap<String, LxSong>(pageIds.size)
        pageIds.forEach { id ->
            directMap[id]?.let { merged[id] = it }
            extraMap[id]?.let { merged[id] = it }
        }
        return merged.values.toList() to hasMore
    }

    private suspend fun fetchNeteaseSongsByIds(ids: List<String>): List<LxSong> {
        if (ids.isEmpty()) return emptyList()
        val idsParam = URLEncoder.encode(ids.joinToString(prefix = "[", postfix = "]", separator = ","), "UTF-8")
        val url = "https://music.163.com/api/song/detail?ids=$idsParam"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .get()
            .build()
        return okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            JsonParser.parseString(resp.body?.string().orEmpty())
                .asJsonObject.getAsJsonArray("songs")
                ?.mapNotNull { el -> if (el.isJsonObject) parseNeteaseSongItem(el.asJsonObject) else null }
                .orEmpty()
        }
    }

    private suspend fun fetchNeteasePlaylistSongsLegacy(playlistId: String): List<LxSong> = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/playlist/detail?id=$playlistId&n=1000"
        val cookie = neteaseCookieStore.getCookie()?.takeIf { it.isNotBlank() }
            ?: "os=pc; appver=2.5.2.197409"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", cookie)
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val result = root.getAsJsonObject("result") ?: return@use emptyList()
                val tracks = result.getAsJsonArray("tracks") ?: return@use emptyList()
                tracks.mapNotNull { el ->
                    if (el.isJsonObject) parseNeteaseSongItem(el.asJsonObject) else null
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteasePlaylistSongsLegacy failed for id=$playlistId")
            emptyList()
        }.also { list ->
            Timber.d("fetchNeteasePlaylistSongs(legacy): id=$playlistId tracks=${list.size}")
        }
    }

    suspend fun resolveNeteaseUserId(raw: String): String? = withContext(Dispatchers.IO) {
        val text = raw.trim()
        if (text.isEmpty()) return@withContext null
        if (text.matches(Regex("""^\d+$"""))) return@withContext text
        val url = shareUrlRegex.find(text)?.value ?: return@withContext null
        matchNeteaseUserId(url)?.let { return@withContext it }
        runCatching {
            okHttpClient.newCall(
                Request.Builder().url(url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                    )
                    .get().build(),
            ).execute().use { resp ->
                matchNeteaseUserId(resp.request.url.toString())
                    ?: resp.body?.string().orEmpty().let { body ->
                        shareUrlRegex.findAll(body)
                            .mapNotNull { matchNeteaseUserId(it.value) }
                            .firstOrNull()
                    }
            }
        }.getOrNull()
    }

    private fun matchNeteaseUserId(url: String): String? {
        val lower = url.lowercase()
        if (!lower.contains("163.com") && !lower.contains("163cn.tv")) return null
        if (!lower.contains("user")) return null
        return Regex("""[?&#]id=(\d+)""").find(url)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    suspend fun fetchNeteaseUserPlaylists(uid: String): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/user/playlist?uid=$uid&limit=1000&offset=0&includeVideo=false"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val list = root.getAsJsonArray("playlist") ?: return@use emptyList()
                list.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = id,
                        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("coverImgUrl")?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("playCount")?.takeIf { it.isJsonPrimitive }?.asLong,
                        trackCount = obj.get("trackCount")?.takeIf { it.isJsonPrimitive }?.asInt,
                        creator = obj.getAsJsonObject("creator")?.get("nickname")
                            ?.takeIf { it.isJsonPrimitive }?.asString,
                        specialType = obj.get("specialType")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteaseUserPlaylists failed uid=$uid")
            emptyList()
        }
    }


    suspend fun fetchNeteaseLoginProfile(): LxNeteaseProfile? {
        val res = neteaseOfficialApi.fetchLoginProfile()
        val ok = res as? NeteaseOfficialApi.ProfileResult.Ok ?: return null
        neteaseUid = ok.profile.uid.toString()
        runCatching { refreshNeteaseLikedSongIds() }
            .onFailure { Timber.w(it, "refreshNeteaseLikedSongIds failed") }
        return LxNeteaseProfile(
            uid = ok.profile.uid,
            nickname = ok.profile.nickname,
            avatarUrl = normalizeNeteasePicUrl(ok.profile.avatarUrl.orEmpty()),
            vipType = ok.profile.vipType,
        )
    }

    suspend fun ensureNeteaseLikedListLoaded() {
        val uid = neteaseUid
        if (uid == null) {
            runCatching { fetchNeteaseLoginProfile() }
                .onFailure { Timber.w(it, "ensureNeteaseLikedListLoaded: fetch profile failed") }
            return
        }
        if (neteaseLikedListLoaded || neteaseLikedListLoadingUid == uid) return
        refreshNeteaseLikedSongIds()
    }

    suspend fun refreshNeteaseLikedSongIds() {
        val uid = neteaseUid ?: return
        if (neteaseLikedListLoadingUid == uid) return
        neteaseLikedListLoadingUid = uid
        try {
            val ok = neteaseWeapiOkWithRetry("/weapi/song/like/get", mapOf("uid" to uid)) ?: return
            val ids = ok.getAsJsonArray("ids")
                ?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                ?.toSet()
                .orEmpty()
            _neteaseLikedSongIds.value = ids
            neteaseLikedListLoaded = true
        } finally {
            neteaseLikedListLoadingUid = null
        }
    }

    suspend fun neteaseSetSongLike(trackId: String, like: Boolean): Boolean {
        val ok = neteaseWeapiOkWithRetry(
            "/weapi/song/like",
            mapOf("trackId" to trackId, "like" to like, "time" to 3, "alg" to "itembased"),
        ) ?: return false
        if (like) {
            invalidateNeteasePlaylistDetailCache()
        }
        _neteaseLikedSongIds.value = if (like) {
            _neteaseLikedSongIds.value + trackId
        } else {
            _neteaseLikedSongIds.value - trackId
        }
        return true
    }

    suspend fun neteaseAddSongsToPlaylist(playlistId: String, trackIds: List<String>): LxAddToPlaylistResult {
        if (trackIds.isEmpty()) return LxAddToPlaylistResult.FAILED
        val trackIdsJson = trackIds.joinToString(",", "[", "]") { "\"$it\"" }
        val payload = mapOf(
            "op" to "add",
            "pid" to playlistId,
            "trackIds" to trackIdsJson,
            "imme" to "true",
        )
        return when (val res = neteaseOfficialApi.postWeapi("/weapi/playlist/manipulate/tracks", payload)) {
            is NeteaseOfficialApi.WeapiResult.Ok -> LxAddToPlaylistResult.ADDED
            is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                neteaseCookieStore.markCookieInvalid()
                LxAddToPlaylistResult.FAILED
            }
            is NeteaseOfficialApi.WeapiResult.Error ->
                if (res.apiCode == 512 || res.apiCode == 502) LxAddToPlaylistResult.ALREADY_EXISTS
                else LxAddToPlaylistResult.FAILED
        }
    }

    suspend fun neteaseRemoveSongsFromPlaylist(playlistId: String, trackIds: List<String>): Boolean {
        if (trackIds.isEmpty()) return false
        val trackIdsJson = trackIds.joinToString(",", "[", "]") { "\"$it\"" }
        val payload = mapOf(
            "op" to "del",
            "pid" to playlistId,
            "trackIds" to trackIdsJson,
            "imme" to "true",
        )
        val res = neteaseOfficialApi.postWeapi("/weapi/playlist/manipulate/tracks", payload)
        return when (res) {
            is NeteaseOfficialApi.WeapiResult.Ok -> true
            is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                neteaseCookieStore.markCookieInvalid()
                false
            }
            else -> false
        }
    }

    suspend fun fetchTxProfile(): Pair<String?, String?> =
        accountExtension.txFetchProfile() ?: (null to null)


    suspend fun kgFollowSinger(singerId: String, follow: Boolean, source: Int = 1): Boolean =
        accountExtension.kgApplyFollowSinger(singerId, follow, source)

    suspend fun kgGetSingerFollowInfo(singerId: String): Boolean? =
        accountExtension.kgQueryFollowInfo(singerId)

    suspend fun kgSendSmsCode(mobile: String): Boolean = accountExtension.kgSendSmsCode(mobile)

    suspend fun kgLoginBySmsCode(mobile: String, code: String): Boolean =
        accountExtension.kgLoginBySmsCode(mobile, code)

    suspend fun fetchKgProfile(): Pair<String?, String?> =
        accountExtension.kgFetchProfile() ?: (null to null)

    suspend fun fetchKwProfile(): Pair<String?, String?> =
        accountExtension.kwFetchProfile() ?: (null to null)

    suspend fun fetchTxLikedSongIds(): Set<String> = withContext(Dispatchers.IO) {
        fetchTxLikedSongs().map { it.songmid }.toSet()
    }

    suspend fun fetchKgLikedSongIds(): Set<String> =
        accountExtension.kgFetchLikedSongIds() ?: emptySet()

    suspend fun fetchKwLikedSongIds(): Set<String> =
        accountExtension.kwFetchLikedSongIds() ?: emptySet()

    internal fun kwUserSession(cookie: String): Pair<String, String>? {
        val uid = kuwoCookieStore.getAccountId().takeIf { it >= 0 }?.toString()
            ?: extractCookieValue(cookie, "userid")
            ?: extractCookieValue(cookie, "uid")
            ?: return null
        var sid = extractCookieValue(cookie, "websid")
        if (sid.isNullOrBlank()) sid = extractCookieValue(cookie, "sid")?.substringBefore("_")
        if (sid.isNullOrBlank()) return null
        return uid to sid
    }

    fun clearKwLikedListCache() {
        accountExtension.clearReadCaches()
    }

    suspend fun fetchKwUserPlaylists(): List<LxPlaylist> =
        accountExtension.kwFetchUserPlaylists() ?: emptyList()

    suspend fun fetchTxLikedSongs(): List<LxSong> =
        accountExtension.txFetchLikedSongs() ?: emptyList()

    suspend fun fetchTxUserPlaylists(): List<LxPlaylist> =
        accountExtension.txFetchUserPlaylists() ?: emptyList()

    suspend fun fetchTxUserPlaylistSongs(disstid: String): List<LxSong> =
        accountExtension.txFetchPlaylistSongs(disstid) ?: emptyList()

    suspend fun txQrCreate(type: LxTxQrType): Pair<ByteArray, String>? =
        accountExtension.txQrCreate(type)

    suspend fun txQrPoll(type: LxTxQrType, session: String): LxTxQrPoll =
        accountExtension.txQrPoll(type, session)


    fun invalidateKgCloudListsCache() {
        accountExtension.clearReadCaches()
    }

    suspend fun fetchKgUserPlaylists(): List<LxPlaylist> =
        accountExtension.kgFetchUserPlaylists() ?: emptyList()

    suspend fun fetchKgCloudPlaylistSongs(listid: String): List<LxSong> =
        accountExtension.kgFetchCloudPlaylistSongs(listid) ?: emptyList()

    suspend fun kgSubscribeSpecial(name: String, gid: String): Boolean =
        accountExtension.kgSubscribePlaylist(name, gid)

    suspend fun kgUnsubscribeCloudList(gid: String? = null, name: String? = null): Boolean =
        accountExtension.kgUnsubscribe(gid, name)

    suspend fun kgDeleteCloudList(listid: String): Boolean =
        accountExtension.kgDeleteCloudList(listid)

    suspend fun kgCollectByCopy(name: String, songs: List<LxSong>): Boolean =
        accountExtension.kgCollectByCopy(name, songs)

    suspend fun kgAddSongsToPlaylist(listid: String, songs: List<LxSong>): LxAddToPlaylistResult =
        accountExtension.kgAddSongsToPlaylist(listid, songs)

    suspend fun kgRemoveSongsFromPlaylist(listid: String, songs: List<LxSong>): Boolean =
        accountExtension.kgRemoveSongsFromPlaylist(listid, songs)

    suspend fun fetchKgLikedSongs(): List<LxSong> =
        accountExtension.kgFetchLikedSongs() ?: emptyList()

    suspend fun fetchKwLikedSongs(): List<LxSong> =
        accountExtension.kwFetchLikedSongs() ?: emptyList()

    suspend fun fetchKwUserPlaylistSongs(playlistId: String): List<LxSong> =
        accountExtension.kwFetchUserPlaylistSongs(playlistId) ?: emptyList()

    fun setTxLikedSongIds(ids: Set<String>) { _txLikedSongIds.value = ids }

    fun setKgLikedSongIds(ids: Set<String>) { _kgLikedSongIds.value = ids }

    fun clearKgCloudCache() {
        accountExtension.clearReadCaches()
    }

    fun setKwLikedSongIds(ids: Set<String>) { _kwLikedSongIds.value = ids }

    suspend fun txSetSongLike(songId: String, like: Boolean): Boolean =
        accountExtension.txApplySongLike(songId, like)

    suspend fun kgSetSongLike(
        songmid: String,
        hash: String?,
        name: String,
        singer: String,
        albumId: String?,
        mixSongId: String? = null,
        like: Boolean,
    ): Boolean = accountExtension.kgApplySongLike(songmid, hash, name, singer, albumId, mixSongId, like)

    suspend fun kwSetSongLike(songId: String, like: Boolean): Boolean =
        accountExtension.kwApplySongLike(songId, like)

    suspend fun kwAddSongsToPlaylist(playlistId: String, songIds: List<String>): LxAddToPlaylistResult =
        accountExtension.kwAddSongsToPlaylist(playlistId, songIds)

    suspend fun kwRemoveSongsFromPlaylist(playlistId: String, songIds: List<String>): Boolean =
        accountExtension.kwRemoveSongsFromPlaylist(playlistId, songIds)

    suspend fun kwSetPlaylistCollect(playlistId: String, collect: Boolean): Boolean =
        accountExtension.kwSetPlaylistCollect(playlistId, collect)

    suspend fun kwFollowArtist(artistId: String, follow: Boolean): Boolean =
        accountExtension.kwApplyArtistFollow(artistId, follow)

    suspend fun kwSetAlbumLike(albumId: String, like: Boolean): Boolean =
        accountExtension.kwApplyAlbumLike(albumId, like)

    sealed interface DailyRecommendOutcome {
        val songs: List<LxSong>
        data class Ok(override val songs: List<LxSong>) : DailyRecommendOutcome
        object CookieInvalid : DailyRecommendOutcome {
            override val songs: List<LxSong> = emptyList()
        }
        data class Failed(val reason: String, override val songs: List<LxSong> = emptyList()) : DailyRecommendOutcome
    }

    suspend fun fetchNeteaseDailyRecommend(): List<LxSong> =
        fetchNeteaseDailyRecommendWithReason().songs

    suspend fun fetchNeteaseDailyRecommendWithReason(): DailyRecommendOutcome {
        if (!neteaseCookieStore.hasCookie()) {
            return DailyRecommendOutcome.CookieInvalid
        }
        val params = mapOf("offset" to 0, "total" to true, "limit" to 30)
        var lastReason = "服务端返回为空"
        var cookieInvalid = false
        repeat(3) { attempt ->
            when (val r = neteaseOfficialApi.postWeapi("/weapi/v3/discovery/recommend/songs", params)) {
                is NeteaseOfficialApi.WeapiResult.Ok -> {
                    val songs = parseDailySongs(r.json)
                    if (songs.isNotEmpty()) return DailyRecommendOutcome.Ok(songs)
                    lastReason = "服务端返回为空（code 200，无 dailySongs）"
                }
                is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                    cookieInvalid = true
                    lastReason = "登录已失效（301）"
                }
                is NeteaseOfficialApi.WeapiResult.Error -> {
                    lastReason = describeWeapiError(r)
                }
            }
            if (attempt < 2) {
                Timber.w("fetchNeteaseDailyRecommend: v3 attempt ${attempt + 1} failed: $lastReason")
                kotlinx.coroutines.delay(if (attempt == 0) 200 else 500)
            }
        }
        Timber.w("fetchNeteaseDailyRecommend: v3 failed 3 times ($lastReason), fallback to v2 endpoint")
        when (val r = neteaseOfficialApi.postWeapi("/weapi/v2/discovery/recommend/songs", params)) {
            is NeteaseOfficialApi.WeapiResult.Ok -> {
                val songs = parseDailySongs(r.json)
                if (songs.isNotEmpty()) return DailyRecommendOutcome.Ok(songs)
            }
            is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                cookieInvalid = true
                lastReason = "登录已失效（301）"
            }
            is NeteaseOfficialApi.WeapiResult.Error -> lastReason = describeWeapiError(r)
        }
        if (cookieInvalid) neteaseCookieStore.markCookieInvalid()
        return if (cookieInvalid) DailyRecommendOutcome.CookieInvalid
        else DailyRecommendOutcome.Failed(lastReason)
    }

    private fun describeWeapiError(e: NeteaseOfficialApi.WeapiResult.Error): String {
        Timber.w("daily recommend error detail: apiCode=${e.apiCode} httpCode=${e.httpCode}")
        return "加载失败，请下拉重试"
    }

    private fun parseDailySongs(root: com.google.gson.JsonObject): List<LxSong> =
        runCatching {
            root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("dailySongs")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseNeteaseSongItem) }
        }.getOrNull().orEmpty()

    suspend fun fetchNeteaseHeartbeatSongs(playlistId: String, seedSongId: String): List<LxSong> {
        val root = neteaseWeapiOk(
            "/weapi/playmode/intelligence/list",
            mapOf(
                "playlistId" to playlistId,
                "songId" to seedSongId,
                "type" to "fromPlayOne",
                "startMusicId" to seedSongId,
                "count" to "150",
            ),
        ) ?: return emptyList()
        val ids = root.getAsJsonArray("data")?.mapNotNull { el ->
            runCatching {
                val obj = el.asJsonObject
                val item = obj.getAsJsonObject("item") ?: obj.getAsJsonObject("songInfo")
                item?.get("id")?.takeIf { it.isJsonPrimitive }?.asString
            }.getOrNull()
        }.orEmpty()
        if (ids.isEmpty()) return emptyList()
        return fetchNeteaseSongsByIdsV3(ids)
    }

    suspend fun fetchNeteaseSimilarSongs(songId: String): List<LxSong> {
        val root = neteaseWeapiOk(
            "/weapi/v1/discovery/simiSong",
            mapOf("songid" to songId, "limit" to 30, "offset" to 0),
        ) ?: return emptyList()
        return root.getAsJsonArray("songs")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseNeteaseSongItem) }
            .orEmpty()
    }

    suspend fun fetchNeteaseRecommendPlaylists(): List<LxPlaylist> {
        val root = neteaseWeapiOk("/weapi/v1/discovery/recommend/resource", emptyMap())
            ?: return emptyList()
        return root.getAsJsonArray("recommend")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseNeteasePlaylistObj) }
            .orEmpty()
    }

    suspend fun fetchNeteaseMyPlaylists(uid: String): List<LxPlaylist> {
        val root = neteaseWeapiOk(
            "/weapi/user/playlist",
            mapOf("uid" to uid, "limit" to 1000, "offset" to 0, "includeVideo" to true),
        ) ?: return emptyList()
        return root.getAsJsonArray("playlist")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseNeteasePlaylistObj) }
            .orEmpty()
    }

    suspend fun fetchNeteaseSubArtists(): List<LxArtist> {
        val result = mutableListOf<LxArtist>()
        var offset = 0
        while (true) {
            val root = neteaseWeapiOk(
                "/weapi/artist/sublist",
                mapOf("limit" to 100, "offset" to offset, "total" to true),
            ) ?: break
            val batch = root.getAsJsonArray("data")
                ?.mapNotNull { el ->
                    runCatching {
                        val obj = el.asJsonObject
                        val id = jsonStr(obj, "id") ?: return@runCatching null
                        LxArtist(
                            id = id,
                            name = jsonStr(obj, "name").orEmpty(),
                            pic = normalizeNeteasePicUrl(
                                jsonStr(obj, "picUrl") ?: jsonStr(obj, "img1v1Url") ?: ""
                            ),
                            albumSize = obj.get("albumSize")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                        )
                    }.getOrNull()
                }
                .orEmpty()
            result.addAll(batch)
            if (batch.size < 100) break
            offset += 100
        }
        return result
    }

    suspend fun fetchNeteaseSubAlbums(): List<LxAlbum> {
        val result = mutableListOf<LxAlbum>()
        var offset = 0
        while (true) {
            val root = neteaseWeapiOk(
                "/weapi/album/sublist",
                mapOf("limit" to 100, "offset" to offset, "total" to true),
            ) ?: break
            val batch = root.getAsJsonArray("data")
                ?.mapNotNull { el -> runCatching { parseNeteaseAlbumObj(el.asJsonObject) }.getOrNull() }
                .orEmpty()
            result.addAll(batch)
            if (batch.size < 100) break
            offset += 100
        }
        return result
    }

    suspend fun fetchNeteasePersonalFm(): List<LxSong> {
        val root = neteaseWeapiOk("/weapi/v1/radio/get", emptyMap()) ?: return emptyList()
        return root.getAsJsonArray("data")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let(::parseNeteaseSongItem) }
            .orEmpty()
    }

    suspend fun fetchNeteaseArtistInfo(artistId: String): LxArtistDetail? {
        val root = neteaseWeapiRead(
            "/weapi/artist/head/info/get",
            mapOf("id" to (artistId.toLongOrNull() ?: artistId)),
        ) ?: return null
        val obj = root.getAsJsonObject("data")?.getAsJsonObject("artist") ?: return null
        val pic = normalizeNeteasePicUrl(
            jsonStr(obj, "picUrl") ?: jsonStr(obj, "avatar")
                ?: jsonStr(obj, "cover") ?: jsonStr(obj, "img1v1Url") ?: ""
        )
        val followed = obj.get("followed")?.takeIf { it.isJsonPrimitive }?.asBoolean
        return LxArtistDetail(
            artist = LxArtist(
                id = artistId,
                name = jsonStr(obj, "name").orEmpty(),
                pic = pic,
                albumSize = obj.get("albumSize")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                briefDesc = jsonStr(obj, "briefDesc"),
            ),
            followed = followed,
        )
    }

    private fun parseArtistSongsArray(arr: com.google.gson.JsonArray?, tag: String, artistId: String): List<LxSong> {
        if (arr == null) return emptyList()
        val hasActiveScript = allScriptPaths().any { it !in disabledPaths }
        var failed = 0
        var firstError: String? = null
        val out = arr.mapNotNull { el ->
            if (!el.isJsonObject) return@mapNotNull null
            runCatching { parseNeteaseSongItem(el.asJsonObject) }
                .onFailure {
                    failed++
                    if (firstError == null) firstError = "${it.javaClass.simpleName}: ${it.message}"
                }
                .getOrNull()
        }.filter { song ->
            hasActiveScript || song.extraFields["noCopyright"] != "1"
        }
        if (failed > 0 || out.isEmpty()) {
            Timber.w("artistSongs $tag artistId=$artistId raw=${arr.size()} parsed=${out.size} failed=$failed firstErr=$firstError")
        }
        return out
    }

    suspend fun fetchNeteaseArtistSongs(artistId: String, offset: Int = 0, limit: Int = 30): Pair<List<LxSong>, Int> {
        val idNum = artistId.toLongOrNull() ?: artistId
        val root = neteaseWeapiRead(
            "/weapi/v1/artist/songs",
            mapOf(
                "id" to idNum,
                "private_cloud" to "true",
                "work_type" to 1,
                "order" to "hot",
                "offset" to offset,
                "limit" to limit,
            ),
        )
        var songs = parseArtistSongsArray(root?.getAsJsonArray("songs"), "main", artistId)
        var total = root?.get("total")?.takeIf { it.isJsonPrimitive }?.asInt ?: songs.size

        if (songs.isEmpty() && offset == 0) {
            val fallback = neteaseWeapiRead(
                "/weapi/v1/artist/songs",
                mapOf(
                    "id" to idNum,
                    "private_cloud" to "true",
                    "order" to "hot",
                    "offset" to 0,
                    "limit" to limit,
                ),
            )
            songs = parseArtistSongsArray(fallback?.getAsJsonArray("songs"), "noWorkType", artistId)
            total = fallback?.get("total")?.takeIf { it.isJsonPrimitive }?.asInt ?: songs.size
        }

        if (songs.isEmpty() && offset == 0) {
            val top = neteaseWeapiRead(
                "/api/artist/top/song",
                mapOf("id" to idNum),
            )
            songs = parseArtistSongsArray(top?.getAsJsonArray("songs"), "topSong", artistId)
            if (songs.isNotEmpty()) total = songs.size
        }

        return songs to total
    }

    suspend fun fetchNeteaseArtistAlbums(artistId: String, offset: Int = 0, limit: Int = 100): List<LxAlbum> {
        val root = neteaseWeapiRead(
            "/weapi/artist/albums/$artistId",
            mapOf("limit" to limit, "offset" to offset, "total" to true),
        ) ?: return emptyList()
        return root.getAsJsonArray("hotAlbums")
            ?.mapNotNull { el -> runCatching { parseNeteaseAlbumObj(el.asJsonObject) }.getOrNull() }
            .orEmpty()
    }

    suspend fun fetchNeteaseAlbumDetail(albumId: String): Pair<LxAlbum?, List<LxSong>> {
        val root = neteaseWeapiRead("/weapi/v1/album/$albumId", emptyMap())
            ?: return null to emptyList()
        val albumObj = root.getAsJsonObject("album")
        val album = albumObj?.let {
            runCatching {
                LxAlbum(
                    id = jsonStr(it, "id") ?: albumId,
                    name = jsonStr(it, "name").orEmpty(),
                    pic = normalizeNeteasePicUrl(jsonStr(it, "picUrl") ?: ""),
                    artistName = it.getAsJsonObject("artist")?.let { a -> jsonStr(a, "name") }
                        ?: it.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.let { a -> jsonStr(a, "name") },
                    publishTime = it.get("publishTime")?.takeIf { v -> v.isJsonPrimitive }?.asLong ?: 0L,
                    size = it.get("size")?.takeIf { v -> v.isJsonPrimitive }?.asInt ?: 0,
                )
            }.getOrNull()
        }
        val songs = root.getAsJsonArray("songs")
            ?.mapNotNull { el ->
                runCatching {
                    val obj = el.asJsonObject
                    parseNeteaseSongItem(obj)?.let { song ->
                        if (song.albumName.isBlank() && album != null) song.copy(albumName = album.name) else song
                    }
                }.getOrNull()
            }
            .orEmpty()
        return album to songs
    }

    private suspend fun neteaseWeapiOk(
        path: String,
        params: Map<String, Any?>,
    ): com.google.gson.JsonObject? =
        when (val r = neteaseOfficialApi.postWeapi(path, params)) {
            is NeteaseOfficialApi.WeapiResult.Ok -> r.json
            is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                Timber.w("neteaseWeapiOk: cookie invalid (301) at $path")
                neteaseCookieStore.markCookieInvalid()
                null
            }
            else -> null
        }

    // 网易 weapi 普通错误重试 3 次（间隔 250/600ms），cookie 失效不重试
    private suspend fun neteaseWeapiOkWithRetry(
        path: String,
        params: Map<String, Any?>,
        attempts: Int = 3,
    ): com.google.gson.JsonObject? {
        repeat(attempts) { i ->
            when (val r = neteaseOfficialApi.postWeapi(path, params)) {
                is NeteaseOfficialApi.WeapiResult.Ok -> return r.json
                is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                    neteaseCookieStore.markCookieInvalid()
                    return null
                }
                is NeteaseOfficialApi.WeapiResult.Error -> {
                    Timber.w("neteaseWeapiOkWithRetry: $path attempt ${i + 1}/$attempts apiCode=${r.apiCode} httpCode=${r.httpCode}")
                    if (i < attempts - 1) delay(if (i == 0) 250L else 600L)
                }
            }
        }
        return null
    }

    private suspend fun neteaseWeapiRead(
        path: String,
        params: Map<String, Any?>,
        attempts: Int = 3,
    ): com.google.gson.JsonObject? {
        suspend fun call(cookie: String?): NeteaseOfficialApi.WeapiResult =
            neteaseOfficialApi.postWeapi(path, params, cookie)

        val hasLoginCookie = !neteaseCookieStore.getCookie().isNullOrBlank()
        repeat(attempts) { i ->
            when (val r = call(null)) {
                is NeteaseOfficialApi.WeapiResult.Ok -> return r.json
                is NeteaseOfficialApi.WeapiResult.CookieInvalid -> {
                    if (hasLoginCookie) neteaseCookieStore.markCookieInvalid()
                    repeat(attempts) { j ->
                        when (val ar = call(NETEASE_ANON_COOKIE)) {
                            is NeteaseOfficialApi.WeapiResult.Ok -> return ar.json
                            is NeteaseOfficialApi.WeapiResult.CookieInvalid -> return null
                            is NeteaseOfficialApi.WeapiResult.Error -> {
                                if (j < attempts - 1) delay(if (j == 0) 250L else 600L)
                            }
                        }
                    }
                    return null
                }
                is NeteaseOfficialApi.WeapiResult.Error -> {
                    Timber.w("neteaseWeapiRead: $path attempt ${i + 1}/$attempts apiCode=${r.apiCode} httpCode=${r.httpCode}")
                    if (i < attempts - 1) delay(if (i == 0) 250L else 600L)
                }
            }
        }
        return null
    }

    suspend fun searchNeteaseArtists(keyword: String, page: Int = 1, limit: Int = 30): Pair<List<LxArtist>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val offset = (page - 1) * limit
            val urls = listOf(
                "https://music.163.com/api/search/get/web?s=$encoded&type=100&offset=$offset&limit=$limit&total=true",
                "https://music.163.com/api/cloudsearch/pc?s=$encoded&type=100&offset=$offset&limit=$limit",
            )
            for (url in urls) {
                val parsed = runCatching {
                    val req = Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
                        .header("Referer", "https://music.163.com/")
                        .header("Cookie", NETEASE_ANON_COOKIE)
                        .get()
                        .build()
                    okHttpClient.newCall(req).execute().use { resp ->
                        val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                        parseNeteaseArtistSearchResult(root)
                    }
                }.onFailure { Timber.w(it, "searchNeteaseArtists failed url=${url.take(48)}") }
                    .getOrNull()
                if (parsed != null && parsed.first.isNotEmpty()) {
                    val (list, total) = parsed
                    Timber.d("searchNeteaseArtists: keyword=$keyword page=$page count=${list.size} total=$total")
                    return@withContext list to total
                }
            }
            emptyList<LxArtist>() to 0
        }

    private fun parseNeteaseArtistSearchResult(root: com.google.gson.JsonObject): Pair<List<LxArtist>, Int>? {
        val result = root.getAsJsonObject("result") ?: return null
        val artists = result.getAsJsonArray("artists") ?: return null
        val total = result.get("artistCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: artists.size()
        val list = artists.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                val id = jsonStr(o, "id") ?: return@runCatching null
                val rawPic = jsonStr(o, "img1v1Url") ?: jsonStr(o, "picUrl") ?: ""
                val pic = normalizeNeteasePicUrl(rawPic)?.let {
                    if (it.contains("?")) it else "$it?param=200y200"
                }
                LxArtist(
                    id = id,
                    name = jsonStr(o, "name").orEmpty(),
                    pic = pic,
                    albumSize = o.get("albumSize")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                )
            }.getOrNull()
        }
        return list to total
    }

    suspend fun searchNeteaseAlbums(keyword: String, page: Int = 1, limit: Int = 30): Pair<List<LxAlbum>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val offset = (page - 1) * limit
            val urls = listOf(
                "https://music.163.com/api/search/get/web?s=$encoded&type=10&offset=$offset&limit=$limit&total=true",
                "https://music.163.com/api/cloudsearch/pc?s=$encoded&type=10&offset=$offset&limit=$limit&total=true",
            )
            for (url in urls) {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://music.163.com/")
                    .header("Cookie", NETEASE_ANON_COOKIE)
                    .get().build()
                val parsed = runCatching {
                    okHttpClient.newCall(req).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        parseNeteaseAlbumSearchResult(JsonParser.parseString(body).asJsonObject)
                    }
                }.onFailure { Timber.w(it, "searchNeteaseAlbums failed url=${url.take(48)}") }
                    .getOrNull()
                if (parsed != null && parsed.first.isNotEmpty()) {
                    val (list, total) = parsed
                    Timber.d("searchNeteaseAlbums: keyword=$keyword page=$page count=${list.size} total=$total")
                    return@withContext list to total
                }
            }
            emptyList<LxAlbum>() to 0
        }

    private fun parseNeteaseAlbumSearchResult(root: com.google.gson.JsonObject): Pair<List<LxAlbum>, Int>? {
        val result = root.getAsJsonObject("result") ?: return null
        val albums = result.getAsJsonArray("albums") ?: return null
        val total = result.get("albumCount")?.takeIf { it.isJsonPrimitive }?.asInt ?: albums.size()
        val list = albums.mapNotNull { el ->
            runCatching {
                val o = el.asJsonObject
                val id = jsonStr(o, "id") ?: return@runCatching null
                val rawPic = jsonStr(o, "picUrl") ?: ""
                val pic = normalizeNeteasePicUrl(rawPic)?.let {
                    if (it.contains("?")) it else "$it?param=200y200"
                }
                LxAlbum(
                    id = id,
                    name = jsonStr(o, "name").orEmpty(),
                    pic = pic,
                    artistName = o.getAsJsonObject("artist")?.let { jsonStr(it, "name") }
                        ?: o.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.let { jsonStr(it, "name") },
                    publishTime = o.get("publishTime")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                    size = o.get("size")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
                )
            }.getOrNull()
        }
        return list to total
    }


    private suspend fun txMusicuPost(payload: com.google.gson.JsonObject): com.google.gson.JsonObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://y.qq.com/")
                    .build()
                okHttpClient.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    root.getAsJsonObject("req_1")?.takeIf { it.get("code")?.asInt == 0 }
                        ?.getAsJsonObject("data")
                }
            }.getOrNull()
        }

    suspend fun searchTxArtists(keyword: String, page: Int = 1): Pair<List<LxArtist>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&t=9&p=$page&n=30&format=json"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Referer", "https://y.qq.com/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxArtist>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val singerObj = root.getAsJsonObject("data")?.getAsJsonObject("singer")
                    val list = singerObj?.getAsJsonArray("list")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val id = jsonStr(o, "singerMID") ?: return@runCatching null
                            LxArtist(
                                id = id,
                                name = jsonStr(o, "singerName").orEmpty(),
                                pic = jsonStr(o, "singerPic")?.takeIf { it.isNotBlank() }
                                    ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it },
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    val total = singerObj?.get("totalnum")?.takeIf { it.isJsonPrimitive }?.asInt ?: list.size
                    list to total
                }
            }.getOrElse {
                Timber.w(it, "searchTxArtists failed keyword=$keyword")
                emptyList<LxArtist>() to 0
            }
        }

    suspend fun searchTxAlbums(keyword: String, page: Int = 1): Pair<List<LxAlbum>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&t=8&p=$page&n=30&format=json"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Referer", "https://y.qq.com/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxAlbum>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val albumObj = root.getAsJsonObject("data")?.getAsJsonObject("album")
                    val list = albumObj?.getAsJsonArray("list")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val mid = jsonStr(o, "albumMID") ?: return@runCatching null
                            val pic = jsonStr(o, "albumPic")?.takeIf { it.isNotBlank() }
                                ?: "https://y.gtimg.cn/music/photo_new/T002R300x300M000$mid.jpg"
                            LxAlbum(
                                id = mid,
                                name = jsonStr(o, "albumName").orEmpty(),
                                pic = if (pic.startsWith("http://")) pic.replace("http://", "https://") else pic,
                                artistName = jsonStr(o, "singerName"),
                                publishTime = jsonStr(o, "publicTime")?.toLongOrNull() ?: 0L,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    val total = albumObj?.get("totalnum")?.takeIf { it.isJsonPrimitive }?.asInt ?: list.size
                    list to total
                }
            }.getOrElse {
                Timber.w(it, "searchTxAlbums failed keyword=$keyword")
                emptyList<LxAlbum>() to 0
            }
        }


    suspend fun fetchTxArtistDetail(artistId: String): LxArtistDetail? = null

    suspend fun fetchTxArtistSongs(
        artistId: String, artistName: String, offset: Int = 0, limit: Int = 30,
    ): Pair<List<LxSong>, Int> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(artistName, "UTF-8")
        val page = offset / limit + 1
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&t=0&p=$page&n=$limit&format=json"
        runCatching {
            okHttpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Referer", "https://y.qq.com/")
                    .get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<LxSong>() to 0
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val songObj = root.getAsJsonObject("data")?.getAsJsonObject("song")
                val songs = songObj?.getAsJsonArray("list")?.mapNotNull { el ->
                    runCatching {
                        val o = el.asJsonObject
                        val songmid = jsonStr(o, "songmid") ?: return@runCatching null
                        val singerArr = o.getAsJsonArray("singer")
                        val belongs = singerArr?.any {
                            runCatching { it.asJsonObject.get("mid")?.asString == artistId }.getOrDefault(false)
                        } ?: false
                        if (!belongs) return@runCatching null
                        val singerPairs = singerArr
                            ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                            ?.map { (jsonStr(it, "name") ?: "") to (jsonStr(it, "mid") ?: "") }
                            ?.filter { it.first.isNotBlank() }
                            .orEmpty()
                        val singerName = singerPairs.takeIf { it.isNotEmpty() }
                            ?.joinToString("、") { it.first } ?: artistName
                        val artistIds = singerPairs.joinToString(",") { it.second }
                            .takeIf { singerPairs.any { p -> p.second.isNotBlank() } }
                        val albumObj = o.getAsJsonObject("album")
                        val albumName = jsonStr(albumObj, "name") ?: jsonStr(o, "albumname") ?: ""
                        val albumMidForPic = jsonStr(o, "albummid") ?: jsonStr(albumObj, "mid")
                        val pic = albumMidForPic?.takeIf { it.isNotBlank() }
                            ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" }
                        LxSong(
                            source = "tx",
                            songmid = songmid,
                            name = jsonStr(o, "songname") ?: "",
                            singer = singerName,
                            albumName = albumName,
                            interval = o.get("interval")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                            pic = pic,
                            extraFields = buildMap {
                                put("strMediaMid", jsonStr(o, "strMediaMid") ?: jsonStr(o, "songmid") ?: "")
                                artistIds?.let { put("artistIds", it) }
                            },
                        )
                    }.getOrNull()
                } ?: emptyList()
                val total = songObj?.get("totalnum")?.takeIf { it.isJsonPrimitive }?.asInt ?: songs.size
                songs to total
            }
        }.getOrElse {
            Timber.w(it, "fetchTxArtistSongs failed artistId=$artistId")
            emptyList<LxSong>() to 0
        }
    }

    suspend fun fetchTxArtistAlbums(artistId: String, artistName: String): List<LxAlbum> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?w=$encoded&t=8&p=1&n=30&format=json"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Referer", "https://y.qq.com/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val albumObj = root.getAsJsonObject("data")?.getAsJsonObject("album")
                    albumObj?.getAsJsonArray("list")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val mid = jsonStr(o, "albumMID") ?: return@runCatching null
                            if (jsonStr(o, "singerMID") != artistId) return@runCatching null
                            val pic = jsonStr(o, "albumPic")?.takeIf { it.isNotBlank() }
                                ?: "https://y.gtimg.cn/music/photo_new/T002R300x300M000$mid.jpg"
                            LxAlbum(
                                id = mid,
                                name = jsonStr(o, "albumName").orEmpty(),
                                pic = if (pic.startsWith("http://")) pic.replace("http://", "https://") else pic,
                                artistName = jsonStr(o, "singerName"),
                                publishTime = jsonStr(o, "publicTime")?.toLongOrNull() ?: 0L,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                }
            }.getOrElse {
                Timber.w(it, "fetchTxArtistAlbums failed artistId=$artistId")
                emptyList()
            }
        }

    suspend fun fetchTxAlbumDetail(albumId: String, albumName: String = ""): Pair<LxAlbum?, List<LxSong>> =
        withContext(Dispatchers.IO) {
            val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg?albummid=$albumId&format=json"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Referer", "https://y.qq.com/")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null to emptyList()
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val data = root.getAsJsonObject("data") ?: return@use null to emptyList()
                    val songs = data.getAsJsonArray("list")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val songmid = jsonStr(o, "songmid") ?: return@runCatching null
                            val singerPairs = o.getAsJsonArray("singer")
                                ?.mapNotNull { runCatching { it.asJsonObject }.getOrNull() }
                                ?.map { (jsonStr(it, "name") ?: "") to (jsonStr(it, "mid") ?: "") }
                                ?.filter { it.first.isNotBlank() }
                                .orEmpty()
                            val singerName = singerPairs.takeIf { it.isNotEmpty() }
                                ?.joinToString("、") { it.first } ?: jsonStr(data, "singername") ?: ""
                            val artistIds = singerPairs.joinToString(",") { it.second }
                                .takeIf { singerPairs.any { p -> p.second.isNotBlank() } }
                            val aMid = jsonStr(o, "albummid") ?: albumId
                            LxSong(
                                source = "tx",
                                songmid = songmid,
                                name = jsonStr(o, "songname") ?: "",
                                singer = singerName,
                                albumName = jsonStr(o, "albumname") ?: jsonStr(data, "name") ?: albumName,
                                interval = o.get("interval")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                                pic = aMid.takeIf { it.isNotBlank() }
                                    ?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" },
                                extraFields = buildMap {
                                    put("strMediaMid", jsonStr(o, "strMediaMid") ?: jsonStr(o, "songmid") ?: "")
                                    artistIds?.let { put("artistIds", it) }
                                },
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    null to songs
                }
            }.getOrElse {
                Timber.w(it, "fetchTxAlbumDetail failed albumId=$albumId")
                null to emptyList()
            }
        }


    suspend fun searchKgArtists(keyword: String, page: Int = 1): Pair<List<LxArtist>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://mobiles.kugou.com/api/v3/search/singer?keyword=$encoded&page=$page&pagesize=30&version=9100"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxArtist>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val artistDataEl = root.get("data")
                    val artistArr = when {
                        artistDataEl?.isJsonArray == true -> artistDataEl.asJsonArray
                        artistDataEl?.isJsonObject == true -> artistDataEl.asJsonObject.getAsJsonArray("info")
                        else -> null
                    }
                    val list = artistArr?.mapNotNull { el ->
                            runCatching {
                                val o = el.asJsonObject
                                val id = jsonStr(o, "singerid") ?: return@runCatching null
                                LxArtist(
                                    id = id,
                                    name = decodeHtmlEntities(jsonStr(o, "singername").orEmpty()),
                                    pic = (jsonStr(o, "imgurl") ?: jsonStr(o, "img"))
                                        ?.takeIf { it.isNotBlank() }
                                        ?.replace("{size}", "400")
                                        ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it },
                                )
                            }.getOrNull()
                        } ?: emptyList()
                    list to list.size
                }
            }.getOrElse {
                Timber.w(it, "searchKgArtists failed keyword=$keyword")
                emptyList<LxArtist>() to 0
            }
        }

    suspend fun searchKgAlbums(keyword: String, page: Int = 1): Pair<List<LxAlbum>, Int> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            val url = "https://mobiles.kugou.com/api/v3/search/album?keyword=$encoded&page=$page&pagesize=30&version=9100"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxAlbum>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val data = root.getAsJsonObject("data") ?: return@use emptyList<LxAlbum>() to 0
                    val list = data.getAsJsonArray("info")?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val id = jsonStr(o, "albumid") ?: return@runCatching null
                            LxAlbum(
                                id = id,
                                name = decodeHtmlEntities(jsonStr(o, "albumname") ?: jsonStr(o, "specialname") ?: ""),
                                pic = jsonStr(o, "imgurl")?.takeIf { it.isNotBlank() }
                                    ?.replace("{size}", "400")
                                    ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it },
                                artistName = decodeHtmlEntities(jsonStr(o, "singername") ?: ""),
                                publishTime = jsonStr(o, "publishtime")?.toLongOrNull() ?: 0L,
                                size = jsonStr(o, "songcount")?.toIntOrNull() ?: 0,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    val total = data.get("total")?.takeIf { it.isJsonPrimitive }?.asInt ?: list.size
                    list to total
                }
            }.getOrElse {
                Timber.w(it, "searchKgAlbums failed keyword=$keyword")
                emptyList<LxAlbum>() to 0
            }
        }

    suspend fun fetchKgArtistPic(artistId: String): String? = withContext(Dispatchers.IO) {
        val url = "https://mobiles.kugou.com/api/v5/singer/info?singerid=$artistId"
        runCatching {
            okHttpClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                    .get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val singer = root.getAsJsonObject("data") ?: root
                (jsonStr(singer, "img") ?: jsonStr(singer, "imgurl"))
                    ?.takeIf { it.isNotBlank() }
                    ?.replace("{size}", "400")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it }
    }


    suspend fun fetchKgArtistDetail(artistId: String): LxArtistDetail? =
        withContext(Dispatchers.IO) {
            val url = "https://mobiles.kugou.com/api/v5/singer/info?singerid=$artistId"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val singer = root.getAsJsonObject("data") ?: root
                    val name = jsonStr(singer, "name") ?: jsonStr(singer, "singername")
                    if (name.isNullOrBlank()) return@use null
                    LxArtistDetail(
                        artist = LxArtist(
                            id = artistId,
                            name = decodeHtmlEntities(name),
                            pic = (jsonStr(singer, "img") ?: jsonStr(singer, "imgurl"))
                                ?.takeIf { it.isNotBlank() }
                                ?.replace("{size}", "400")
                                ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it },
                            briefDesc = (jsonStr(singer, "intro") ?: jsonStr(singer, "desc"))?.takeIf { it.isNotBlank() },
                        ),
                        followed = null,
                    )
                }
            }.getOrNull()
        }

    suspend fun fetchKgArtistSongs(artistId: String, offset: Int = 0, limit: Int = 30): Pair<List<LxSong>, Int> =
        withContext(Dispatchers.IO) {
            val page = offset / limit + 1
            val url = "https://mobiles.kugou.com/api/v5/singer/song?singerid=$artistId&page=$page&pagesize=$limit"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList<LxSong>() to 0
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val dataEl = root.get("data")
                    val songArr = when {
                        dataEl?.isJsonArray == true -> dataEl.asJsonArray
                        dataEl?.isJsonObject == true -> dataEl.asJsonObject.getAsJsonArray("info")
                        else -> null
                    }
                    val songs = songArr?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val hash = jsonStr(o, "hash") ?: return@runCatching null
                            val kgFn = jsonStr(o, "filename") ?: jsonStr(o, "songname") ?: ""
                            val kgParts = kgFn.split(" - ", limit = 2)
                            LxSong(
                                source = "kg",
                                songmid = hash,
                                name = decodeHtmlEntities(if (kgParts.size > 1) kgParts[1] else kgFn),
                                singer = decodeHtmlEntities(jsonStr(o, "singername") ?: if (kgParts.size > 1) kgParts[0] else "").replace("&", "、"),
                                albumName = decodeHtmlEntities(jsonStr(o, "album_name") ?: ""),
                                interval = jsonStr(o, "duration")?.toLongOrNull() ?: 0L,
                                hash = hash,
                                extraFields = mapOf("albumId" to (jsonStr(o, "album_id") ?: "")),
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    val total = root.get("total")?.takeIf { it.isJsonPrimitive }?.asInt
                        ?: if (songs.size >= limit) offset + limit + 1 else offset + songs.size
                    songs to total
                }
            }.getOrElse {
                Timber.w(it, "fetchKgArtistSongs failed artistId=$artistId")
                emptyList<LxSong>() to 0
            }
        }

    suspend fun fetchKgArtistAlbums(artistId: String): List<LxAlbum> =
        withContext(Dispatchers.IO) {
            val url = "https://mobiles.kugou.com/api/v5/singer/album?singerid=$artistId&page=1&pagesize=30"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val albumDataEl = root.get("data")
                    val albumArr = when {
                        albumDataEl?.isJsonArray == true -> albumDataEl.asJsonArray
                        albumDataEl?.isJsonObject == true -> albumDataEl.asJsonObject.getAsJsonArray("info")
                        else -> null
                    }
                    albumArr?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val id = jsonStr(o, "albumid") ?: return@runCatching null
                            LxAlbum(
                                id = id,
                                name = decodeHtmlEntities(jsonStr(o, "specialname") ?: jsonStr(o, "albumname") ?: jsonStr(o, "name") ?: ""),
                                pic = jsonStr(o, "imgurl")?.takeIf { it.isNotBlank() }
                                    ?.replace("{size}", "400")
                                    ?.let { if (it.startsWith("http://")) it.replace("http://", "https://") else it },
                                artistName = decodeHtmlEntities(jsonStr(o, "singername") ?: ""),
                                publishTime = jsonStr(o, "publishtime")?.toLongOrNull() ?: 0L,
                                size = jsonStr(o, "songcount")?.toIntOrNull() ?: 0,
                            )
                        }.getOrNull()
                    } ?: emptyList()
                }
            }.getOrElse {
                Timber.w(it, "fetchKgArtistAlbums failed artistId=$artistId")
                emptyList()
            }
        }

    suspend fun fetchKgAlbumDetail(albumId: String): Pair<LxAlbum?, List<LxSong>> =
        withContext(Dispatchers.IO) {
            val url = "https://mobiles.kugou.com/api/v3/album/song?albumid=$albumId&page=1&pagesize=100"
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                        .get().build()
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null to emptyList()
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val dataEl = root.get("data")
                    val songArr = when {
                        dataEl?.isJsonArray == true -> dataEl.asJsonArray
                        dataEl?.isJsonObject == true -> dataEl.asJsonObject.getAsJsonArray("info")
                        else -> null
                    }
                    val songs = songArr?.mapNotNull { el ->
                        runCatching {
                            val o = el.asJsonObject
                            val hash = jsonStr(o, "hash") ?: return@runCatching null
                            val kgFn = jsonStr(o, "filename") ?: jsonStr(o, "songname") ?: ""
                            val kgParts = kgFn.split(" - ", limit = 2)
                            LxSong(
                                source = "kg",
                                songmid = hash,
                                name = decodeHtmlEntities(if (kgParts.size > 1) kgParts[1] else kgFn),
                                singer = decodeHtmlEntities(jsonStr(o, "singername") ?: if (kgParts.size > 1) kgParts[0] else "").replace("&", "、"),
                                albumName = decodeHtmlEntities(jsonStr(o, "album_name") ?: ""),
                                interval = jsonStr(o, "duration")?.toLongOrNull() ?: 0L,
                                hash = hash,
                                extraFields = mapOf("albumId" to (jsonStr(o, "album_id") ?: "")),
                            )
                        }.getOrNull()
                    } ?: emptyList()
                    null to songs
                }
            }.getOrElse {
                Timber.w(it, "fetchKgAlbumDetail failed albumId=$albumId")
                null to emptyList()
            }
        }

    suspend fun followNeteaseArtist(artistId: String, follow: Boolean): Boolean {
        val idNum = artistId.toLongOrNull() ?: artistId
        val action = if (follow) "sub" else "unsub"
        val root = neteaseWeapiOkWithRetry(
            "/weapi/artist/$action",
            mapOf(
                "artistId" to idNum,
                "artistIds" to "['$artistId']",
            ),
        ) ?: return false
        return root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt == 200
    }

    suspend fun setNeteasePlaylistSubscribed(playlistId: String, subscribe: Boolean): Boolean {
        val action = if (subscribe) "subscribe" else "unsubscribe"
        val root = neteaseWeapiOkWithRetry(
            "/weapi/playlist/$action",
            mapOf("id" to playlistId),
        ) ?: return false
        return root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt == 200
    }

    suspend fun setNeteaseAlbumSubscribed(albumId: String, subscribe: Boolean): Boolean {
        val action = if (subscribe) "sub" else "unsub"
        val root = neteaseWeapiOkWithRetry(
            "/weapi/album/$action",
            mapOf("id" to albumId),
        ) ?: return false
        return root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt == 200
    }

    private suspend fun fetchNeteaseSongsByIdsV3(ids: List<String>): List<LxSong> {
        if (ids.isEmpty()) return emptyList()
        val c = ids.joinToString(prefix = "[", postfix = "]", separator = ",") { "{\"id\":$it}" }
        val idArr = ids.joinToString(prefix = "[", postfix = "]", separator = ",")
        val root = neteaseWeapiOk(
            "/weapi/v3/song/detail",
            mapOf("c" to c, "ids" to idArr),
        ) ?: return emptyList()
        val payedById = parseNeteasePayedMap(root.getAsJsonArray("privileges"))
        return root.getAsJsonArray("songs")
            ?.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.let { parseNeteaseSongItem(it, payedById) } }
            .orEmpty()
    }

    private fun parseNeteasePlaylistObj(obj: com.google.gson.JsonObject): LxPlaylist? {
        val id = jsonStr(obj, "id") ?: return null
        return LxPlaylist(
            id = id,
            name = jsonStr(obj, "name").orEmpty(),
            pic = normalizeNeteasePicUrl(
                jsonStr(obj, "picUrl") ?: jsonStr(obj, "coverImgUrl") ?: ""
            ),
            playCount = (obj.get("playcount") ?: obj.get("playCount"))?.takeIf { it.isJsonPrimitive }?.asLong,
            trackCount = obj.get("trackCount")?.takeIf { it.isJsonPrimitive }?.asInt,
            creator = obj.getAsJsonObject("creator")?.let { jsonStr(it, "nickname") }
                ?: jsonStr(obj, "copywriter"),
            specialType = obj.get("specialType")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
            subscribed = obj.get("subscribed")?.takeIf { it.isJsonPrimitive }?.asBoolean,
            creatorUserId = obj.getAsJsonObject("creator")?.get("userId")?.takeIf { it.isJsonPrimitive }?.asString,
        )
    }

    private fun parseNeteaseAlbumObj(obj: com.google.gson.JsonObject): LxAlbum? {
        val id = jsonStr(obj, "id") ?: return null
        val artistName = obj.getAsJsonArray("artists")
            ?.firstOrNull()?.asJsonObject?.let { jsonStr(it, "name") }
            ?: jsonStr(obj, "artistName")
            ?: obj.getAsJsonObject("artist")?.let { jsonStr(it, "name") }
        return LxAlbum(
            id = id,
            name = jsonStr(obj, "name").orEmpty(),
            pic = normalizeNeteasePicUrl(jsonStr(obj, "picUrl") ?: ""),
            artistName = artistName,
            publishTime = obj.get("publishTime")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
            size = obj.get("size")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0,
        )
    }

    suspend fun fetchNeteasePlaylists(cat: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val offset = (page - 1) * 30
        val url = "https://music.163.com/api/playlist/list?cat=${URLEncoder.encode(cat, "UTF-8")}" +
            "&offset=$offset&limit=30&total=true&order=hot"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val playlists = root.getAsJsonArray("playlists") ?: return@use emptyList()
                playlists.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = id,
                        name = obj.get("name")?.asString ?: "",
                        pic = obj.get("coverImgUrl")?.asString,
                        playCount = obj.get("playCount")?.takeIf { it.isJsonPrimitive }?.asLong,
                        trackCount = obj.get("trackCount")?.takeIf { it.isJsonPrimitive }?.asInt,
                        creator = obj.getAsJsonObject("creator")?.get("nickname")?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteasePlaylists failed for cat=$cat page=$page")
            emptyList()
        }
    }


    suspend fun getPlaylistTags(source: String): List<LxPlaylistTag> = when (source) {
        "wy" -> neteasePlaylistTags.map { LxPlaylistTag(id = it, name = it) }
        "kw" -> fetchKwPlaylistTags()
        "kg" -> fetchKgPlaylistTags()
        "tx" -> fetchTxPlaylistTags()
        else -> emptyList()
    }

    suspend fun fetchPlaylists(source: String, tagId: String, page: Int): List<LxPlaylist> = when (source) {
        "wy" -> fetchNeteasePlaylists(tagId, page)
        "kw" -> fetchKwPlaylists(tagId, page)
        "kg" -> fetchKgPlaylists(tagId, page)
        "tx" -> fetchTxPlaylists(tagId, page)
        else -> emptyList()
    }

    suspend fun fetchPlaylistSongs(source: String, playlistId: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> = when (source) {
        "wy" -> fetchNeteasePlaylistSongs(playlistId, page, pageSize)
        "kw" -> fetchKwPlaylistSongs(playlistId, page, pageSize)
        "kg" -> fetchKgPlaylistSongs(playlistId, page, pageSize)
        "tx" -> fetchTxPlaylistSongs(playlistId, page, pageSize)
        else -> emptyList<LxSong>() to false
    }

    suspend fun fetchPlaylistSongsAll(source: String, playlistId: String): List<LxSong> {
        if (source == "kg") {
            val acc = mutableListOf<LxSong>()
            var page = 1
            while (true) {
                val (batch, hasMore) = fetchKgPlaylistSongs(playlistId, page, 500)
                acc += batch
                if (!hasMore || batch.isEmpty()) break
                page++
            }
            return acc.distinctBy { it.source to it.songmid }
        }
        return fetchPlaylistSongs(source, playlistId, 1, Int.MAX_VALUE).first
    }

    suspend fun searchPlaylists(source: String, keyword: String, page: Int): List<LxPlaylist> = when (source) {
        "wy" -> searchNeteasePlaylists(keyword, page)
        "kw" -> searchKwPlaylists(keyword, page)
        "kg" -> searchKgPlaylists(keyword, page)
        "tx" -> searchTxPlaylists(keyword, page)
        else -> emptyList()
    }

    private val shareUrlRegex = Regex("""https?://[A-Za-z0-9._~:/?#\[\]@!$&'()*+,;=%-]+""")

    // 粘贴分享链接导入歌单：短链先跟跳转，还认不出就扒页面正文找
    suspend fun resolvePlaylistRef(raw: String, fallbackSource: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val text = raw.trim()
            if (text.isEmpty()) return@withContext null
            if (text.matches(Regex("""^\d+$"""))) {
                return@withContext when (fallbackSource) {
                    LxSources.KUWO -> LxSources.KUWO to "kw:8:$text"
                    in LxSources.ALL -> fallbackSource to text
                    else -> null
                }
            }
            val url = shareUrlRegex.find(text)?.value ?: return@withContext null
            matchPlaylistUrl(url)?.let { return@withContext it }
            runCatching {
                okHttpClient.newCall(
                    Request.Builder().url(url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                        )
                        .get().build(),
                ).execute().use { resp ->
                    matchPlaylistUrl(resp.request.url.toString())
                        ?: resp.body?.string().orEmpty().let { body ->
                            shareUrlRegex.findAll(body)
                                .mapNotNull { matchPlaylistUrl(it.value) }
                                .firstOrNull()
                                ?: body.takeIf {
                                    it.contains("playlist", ignoreCase = true) &&
                                        resp.request.url.host.contains("163")
                                }?.let { page ->
                                    Regex("""[?&/]id[=/](\d+)""").find(page)
                                        ?.groupValues?.getOrNull(1)
                                        ?.let { id -> LxSources.NETEASE to id }
                                }
                        }
                }
            }.getOrNull()
        }

    // 各平台歌单 URL 的识别规则在这，加新平台或新链接形式改这里
    private fun matchPlaylistUrl(url: String): Pair<String, String>? {
        val lower = url.lowercase()
        fun pick(patterns: List<String>): String? = patterns.firstNotNullOfOrNull { p ->
            Regex(p).find(url)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
        return when {
            lower.contains("music.163.com") || lower.contains("163cn.tv") ->
                pick(listOf("""[?&#]id=(\d+)""", """/playlist(?:/detail)?/(\d+)"""))
                    ?.let { LxSources.NETEASE to it }
            lower.contains("kuwo.cn") ->
                pick(listOf("""/play_detail/(\d+)""", """/playlist_detail/(\d+)""", """[?&#]pid=(\d+)""", """/playlist/(\d+)"""))
                    ?.let { LxSources.KUWO to "kw:8:$it" }
            lower.contains("qq.com") ->
                // y.qq.com/n/ryqq/playlist/xxx.html、taoge.html?id=xxx、disstid=xxx
                pick(listOf("""/playlist/(\d+)""", """[?&#]disstid=(\d+)""", """[?&#]id=(\d+)"""))
                    ?.let { LxSources.TENCENT to it }
            lower.contains("kugou.com") ->
                // www.kugou.com/yy/special/single/xxx.html、?specialid=xxx
                pick(listOf("""[?&#]specialid=(\d+)""", """/(\d+)\.html"""))
                    ?.let { LxSources.KUGOU to it }
            else -> null
        }
    }

    private suspend fun searchNeteasePlaylists(keyword: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val offset = (page - 1) * 20
        val form = "s=$encoded&type=1000&offset=$offset&limit=20&total=true"
        val req = Request.Builder().url("https://music.163.com/api/search/get/web")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .header("Cookie", "os=pc; appver=2.5.2.197409")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull()))
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val playlists = root.getAsJsonObject("result")?.getAsJsonArray("playlists")
                    ?: return@use emptyList()
                playlists.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = id,
                        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("coverImgUrl")?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("playCount")?.takeIf { it.isJsonPrimitive }?.asLong,
                        trackCount = obj.get("trackCount")?.takeIf { it.isJsonPrimitive }?.asInt,
                        creator = obj.getAsJsonObject("creator")?.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "searchNeteasePlaylists failed keyword=$keyword")
            emptyList()
        }
    }

    private suspend fun searchKwPlaylists(keyword: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://search.kuwo.cn/r.s?all=$encoded&pn=${page - 1}&rn=20&rformat=json&encoding=utf8" +
            "&ver=mbox&vipver=MUSIC_8.7.7.0_BCS37&plat=pc&devid=28156413&ft=playlist&pay=0&needliveshow=0"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val abslist = root.getAsJsonArray("abslist") ?: return@use emptyList()
                abslist.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("playlistid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = "kw:8:$id",
                        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("pic")?.takeIf { it.isJsonPrimitive }?.asString,
                        trackCount = obj.get("songnum")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = obj.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "searchKwPlaylists failed keyword=$keyword")
            emptyList()
        }
    }

    private suspend fun searchKgPlaylists(keyword: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "http://msearchretry.kugou.com/api/v3/search/special?keyword=$encoded&page=$page&pagesize=20" +
            "&showtype=10&filter=0&version=7910&sver=2"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("errcode")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val info = root.getAsJsonObject("data")?.getAsJsonArray("info") ?: return@use emptyList()
                info.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val specialid = obj.get("specialid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = specialid,
                        name = obj.get("specialname")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("imgurl")?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("playcount")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asLong }.getOrNull() },
                        trackCount = obj.get("songcount")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = obj.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString,
                        originGid = obj.get("gid")?.takeIf { it.isJsonPrimitive }?.asString
                            ?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "searchKgPlaylists failed keyword=$keyword")
            emptyList()
        }
    }

    private suspend fun searchTxPlaylists(keyword: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "http://c.y.qq.com/soso/fcgi-bin/client_music_search_songlist?page_no=${page - 1}&num_per_page=20" +
            "&format=json&query=$encoded&remoteplace=txt.yqq.playlist&inCharset=utf8&outCharset=utf-8"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)")
            .header("Referer", "http://y.qq.com/portal/search.html")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val list = root.getAsJsonObject("data")?.getAsJsonArray("list") ?: return@use emptyList()
                list.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val tid = obj.get("dissid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = tid,
                        name = obj.get("dissname")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("imgurl")?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("listennum")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asLong }.getOrNull() },
                        trackCount = obj.get("song_count")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = obj.getAsJsonObject("creator")?.get("name")?.takeIf { it.isJsonPrimitive }?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "searchTxPlaylists failed keyword=$keyword")
            emptyList()
        }
    }

    private suspend fun fetchKwPlaylistTags(): List<LxPlaylistTag> = withContext(Dispatchers.IO) {
        val url = "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmTagList?loginUid=0&loginSid=0&appUid=76039576"
        val tags = runCatching {
            okHttpClient.newCall(Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 200) return@use emptyList()
                val groups = root.getAsJsonArray("data") ?: return@use emptyList()
                groups.take(2).flatMap { gEl ->
                    val items = gEl.asJsonObject.getAsJsonArray("data")
                        ?: return@flatMap emptyList()
                    items.mapNotNull { el ->
                        val obj = el.asJsonObject
                        val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                        val digest = obj.get("digest")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                        val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                        LxPlaylistTag(id = "$id-$digest", name = name)
                    }
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchKwPlaylistTags failed")
            emptyList()
        }
        tags.ifEmpty { listOf(LxPlaylistTag("", "热门推荐")) }
    }

    private suspend fun fetchKgPlaylistTags(): List<LxPlaylistTag> = withContext(Dispatchers.IO) {
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_smarty=1&cdn=cdn&t=5"
        val tags = runCatching {
            okHttpClient.newCall(Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("status")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 1) return@use emptyList()
                val hotTag = root.getAsJsonObject("data")?.getAsJsonObject("hotTag")
                    ?.getAsJsonObject("data") ?: return@use emptyList()
                hotTag.entrySet().mapNotNull { (_, el) ->
                    val obj = el.asJsonObject ?: return@mapNotNull null
                    val name = obj.get("special_name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    val id = obj.get("special_id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylistTag(id = id, name = name)
                }.sortedBy { it.id }
            }
        }.getOrElse {
            Timber.w(it, "fetchKgPlaylistTags failed")
            emptyList()
        }
        tags.ifEmpty { listOf(LxPlaylistTag("", "热门推荐")) }
    }

    private suspend fun fetchTxPlaylistTags(): List<LxPlaylistTag> = withContext(Dispatchers.IO) {
        val body = """{"comm":{"cv":1602,"ct":20},"tags":{"method":"get_all_categories","param":{"qq":""},"module":"playlist.PlaylistAllCategoriesServer"}}"""
        val tags = runCatching {
            okHttpClient.newCall(Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
                .header("Referer", "https://y.qq.com/")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val groups = root.getAsJsonObject("tags")?.getAsJsonObject("data")
                    ?.getAsJsonArray("v_group") ?: return@use emptyList()
                val hotItems = groups.firstOrNull { g ->
                    g.asJsonObject.get("group_id")?.takeIf { it.isJsonPrimitive }?.asInt == 1
                }?.asJsonObject?.getAsJsonArray("v_item")
                    ?: return@use emptyList()
                hotItems.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylistTag(id = id, name = name)
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchTxPlaylistTags failed")
            emptyList()
        }
        tags.ifEmpty { listOf(LxPlaylistTag("3317", "官方歌单")) }
    }

    private suspend fun fetchKwPlaylists(tagId: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val url = if (tagId.isBlank()) {
            "http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList?loginUid=0&loginSid=0&appUid=76039576" +
                "&pn=$page&rn=36&order=hot"
        } else {
            val id = tagId.substringBefore("-")
            "https://wapi.kuwo.cn/api/pc/classify/playlist/getTagPlayList?loginUid=0&loginSid=0&appUid=76039576" +
                "&pn=$page&id=$id&rn=36"
        }
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 200) return@use emptyList()
                val dataArr = root.getAsJsonObject("data")?.getAsJsonArray("data") ?: return@use emptyList()
                dataArr.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    val digest = obj.get("digest")?.takeIf { it.isJsonPrimitive }?.asString ?: "8"
                    LxPlaylist(
                        id = "kw:$digest:$id",
                        name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = obj.get("img")?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("listencnt")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asLong }.getOrNull() },
                        trackCount = obj.get("total")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = obj.get("uname")?.takeIf { it.isJsonPrimitive }?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchKwPlaylists failed tagId=$tagId page=$page")
            emptyList()
        }
    }

    private suspend fun fetchKgPlaylists(tagId: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val url = "http://www2.kugou.kugou.com/yueku/v9/special/getSpecial?is_ajax=1&cdn=cdn&t=5&c=$tagId&p=$page"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("status")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 1) return@use emptyList()
                val list = root.getAsJsonArray("special_db") ?: return@use emptyList()
                list.mapNotNull { el ->
                    val obj = el.asJsonObject
                    val specialid = obj.get("specialid")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: return@mapNotNull null
                    LxPlaylist(
                        id = specialid,
                        name = obj.get("specialname")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = (obj.get("img") ?: obj.get("imgurl"))?.takeIf { it.isJsonPrimitive }?.asString,
                        playCount = obj.get("play_count")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asLong }.getOrNull() },
                        trackCount = obj.get("songcount")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = obj.get("nickname")?.takeIf { it.isJsonPrimitive }?.asString,
                        originGid = obj.get("global_collection_id")?.takeIf { it.isJsonPrimitive }?.asString
                            ?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchKgPlaylists failed tagId=$tagId page=$page")
            emptyList()
        }
    }

    private suspend fun fetchTxPlaylists(tagId: String, page: Int): List<LxPlaylist> = withContext(Dispatchers.IO) {
        val payload = """{"comm":{"cv":1602,"ct":20},"playlist":{"method":"get_category_content",""" +
            """"param":{"titleid":$tagId,"caller":"0","category_id":$tagId,"size":36,"page":${page - 1},"use_page":1},""" +
            """"module":"playlist.PlayListCategoryServer"}}"""
        val req = Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://y.qq.com/")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val playlist = root.getAsJsonObject("playlist") ?: return@use emptyList()
                if ((playlist.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val items = playlist.getAsJsonObject("data")?.getAsJsonObject("content")
                    ?.getAsJsonArray("v_item") ?: return@use emptyList()
                items.mapNotNull { el ->
                    val basic = el.asJsonObject?.getAsJsonObject("basic") ?: return@mapNotNull null
                    val tid = basic.get("tid")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                    LxPlaylist(
                        id = tid,
                        name = basic.get("title")?.takeIf { it.isJsonPrimitive }?.asString ?: "",
                        pic = basic.getAsJsonObject("cover")?.let { c ->
                            (c.get("medium_url") ?: c.get("default_url"))?.takeIf { it.isJsonPrimitive }?.asString
                        },
                        playCount = basic.get("play_cnt")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asLong }.getOrNull() },
                        trackCount = basic.get("song_cnt")?.takeIf { it.isJsonPrimitive }
                            ?.let { runCatching { it.asInt }.getOrNull() },
                        creator = basic.getAsJsonObject("creator")?.get("nick")?.takeIf { it.isJsonPrimitive }?.asString,
                    )
                }
            }
        }.getOrElse {
            Timber.w(it, "fetchTxPlaylists failed tagId=$tagId page=$page")
            emptyList()
        }
    }

    private suspend fun fetchKwPlaylistSongs(playlistId: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> {
        if (page > 1) return emptyList<LxSong>() to false
        val songs = accountExtension.kwFetchPlaylistSquareSongs(playlistId) ?: emptyList()
        return songs to false
    }

    private suspend fun fetchKgPlaylistSongs(specialid: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> = withContext(Dispatchers.IO) {
        val effectiveSize = if (pageSize == Int.MAX_VALUE) 500 else pageSize
        val url = "http://mobilecdnbj.kugou.com/api/v3/special/song?version=9108&specialid=$specialid" +
            "&pagesize=$effectiveSize&page=$page&area_code=1&plat=0&with_res_tag=0"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use (emptyList<LxSong>() to false)
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val data = root.getAsJsonObject("data") ?: return@use (emptyList<LxSong>() to false)
                val info = data.getAsJsonArray("info") ?: return@use (emptyList<LxSong>() to false)
                val total = data.get("total")?.takeIf { it.isJsonPrimitive }?.asInt
                val songs = info.mapNotNull { el -> if (el.isJsonObject) parseKgSongInfo(el.asJsonObject) else null }
                val hasMore = if (total != null) {
                    page * effectiveSize < total
                } else {
                    songs.size >= effectiveSize
                }
                songs to hasMore
            }
        }.getOrElse {
            Timber.w(it, "fetchKgPlaylistSongs failed specialid=$specialid page=$page")
            emptyList<LxSong>() to false
        }.also { (list, hasMore) ->
            Timber.d("fetchKgPlaylistSongs: specialid=$specialid page=$page songs=${list.size} hasMore=$hasMore")
        }
    }

    private suspend fun fetchTxPlaylistSongs(dissTid: String, page: Int = 1, pageSize: Int = Int.MAX_VALUE): Pair<List<LxSong>, Boolean> = withContext(Dispatchers.IO) {
        if (page > 1) return@withContext (emptyList<LxSong>() to false)
        val songs = fetchTxPlaylistSongsAll(dissTid)
        songs to false
    }

    private suspend fun fetchTxPlaylistSongsAll(dissTid: String): List<LxSong> = withContext(Dispatchers.IO) {
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg?type=1&json=1&utf8=1&onlysong=0" +
            "&new_format=1&disstid=$dissTid&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8" +
            "&notice=0&platform=yqq.json&needNewCode=0"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://y.qq.com/n/yqq/playlist/$dissTid.html")
            .header("Origin", "https://y.qq.com")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if ((root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1) != 0) return@use emptyList()
                val cd = root.getAsJsonArray("cdlist")?.firstOrNull()?.asJsonObject ?: return@use emptyList()
                val songlist = cd.getAsJsonArray("songlist") ?: return@use emptyList()
                songlist.mapNotNull { el -> if (el.isJsonObject) parseTxPlaylistSong(el.asJsonObject) else null }
            }
        }.getOrElse {
            Timber.w(it, "fetchTxPlaylistSongs failed tid=$dissTid")
            emptyList()
        }.also { list ->
            Timber.d("fetchTxPlaylistSongs: tid=$dissTid songs=${list.size}")
        }
    }

    private fun buildNeteaseQualityInfo(obj: com.google.gson.JsonObject): Pair<List<String>, String?> {
        val privilege = obj.getAsJsonObject("privilege")
        val maxBrLevel = privilege?.get("maxBrLevel")?.asString
        val maxbr = privilege?.get("maxbr")?.asInt ?: 0
        val typesObj = com.google.gson.JsonObject()
        val qualityList = mutableListOf<String>()
        fun addQuality(key: String, field: String) {
            val size = obj.getAsJsonObject(field)?.get("size")?.asLong
            typesObj.add(key, com.google.gson.JsonObject().apply {
                addProperty("size", size?.let { sizeFormate(it) } ?: "")
            })
            qualityList.add(key)
        }
        if (maxBrLevel == "hires") addQuality("flac24bit", "hr")
        when (maxbr) {
            999000 -> { addQuality("flac", "sq"); addQuality("320k", "h"); addQuality("128k", "l") }
            320000 -> { addQuality("320k", "h"); addQuality("128k", "l") }
            192000, 128000 -> { addQuality("128k", "l") }
        }
        qualityList.reverse()
        val typesJson = typesObj.takeIf { it.size() > 0 }?.toString()
        return qualityList.toList() to typesJson
    }

    private fun sizeFormate(size: Long): String = when {
        size > 1024L * 1024 * 1024 -> "%.2f GB".format(size / (1024.0 * 1024 * 1024))
        size > 1024 * 1024 -> "%.2f MB".format(size / (1024.0 * 1024))
        size > 1024 -> "%.2f KB".format(size / 1024.0)
        else -> "$size B"
    }

    fun activeScriptPath(): String? {
        val loaded = _loaded.value.filterKeys { it !in disabledPaths }
        if (loaded.isEmpty()) return null
        val preferred = preferredScriptPath
        if (preferred != null && preferred in loaded) return preferred
        val withCaps = loaded.keys.firstOrNull { engine.isSourceSupported(it, "kw") || engine.isSourceSupported(it, "kg") || engine.isSourceSupported(it, "tx") || engine.isSourceSupported(it, "wy") || engine.isSourceSupported(it, "mg") }
        return withCaps ?: loaded.keys.firstOrNull()
    }

    fun activeRuntimeScriptPathsOrdered(source: String? = null): List<String> {
        val active = runtimePaths.value.filter { it !in disabledPaths }
        if (active.isEmpty()) return emptyList()
        val preferred = preferredScriptPath?.takeIf { it in active }
        val rest = active.filter { it != preferred }
        val orderedRest = if (source != null) {
            val (supporting, others) = rest.partition { runCatching { engine.isSourceSupported(it, source) }.getOrDefault(false) }
            supporting + others
        } else rest
        return listOfNotNull(preferred) + orderedRest
    }
    fun loadedScriptCount(): Int = _loaded.value.size
    fun allScriptPaths(): Set<String> = _loaded.value.keys

    fun hasActiveScript(): Boolean = allScriptPaths().any { it !in disabledPaths }

    suspend fun ensureDefaultScriptLoaded() {
        val defaultScript = _scripts.value.firstOrNull { !it.isImported && it.assetPath.contains("wy.js", ignoreCase = true) && it.assetPath !in disabledPaths }
            ?: _scripts.value.firstOrNull { !it.isImported && it.assetPath !in disabledPaths }
            ?: run {
                Timber.w("no bundled scripts available to auto-load (imported scripts stay off)")
                return
            }
        if (engine.isLoaded(defaultScript.assetPath)) return
        Timber.d("auto loading default script: ${defaultScript.assetPath}")
        runCatching { load(defaultScript.assetPath) }
            .onFailure { Timber.w(it, "ensureDefaultScriptLoaded: failed to load ${defaultScript.assetPath}") }
    }

    suspend fun loadAllScripts(activeScriptPath: String? = null) {
        val unloaded = _scripts.value.filter { !engine.isLoaded(it.assetPath) && it.assetPath !in disabledPaths }
        if (unloaded.isEmpty()) return
        coroutineScope {
            val ordered = if (activeScriptPath != null) {
                val active = unloaded.filter { it.assetPath == activeScriptPath }
                val rest = unloaded.filter { it.assetPath != activeScriptPath }
                active + rest
            } else unloaded
            ordered.map { meta ->
                async { runCatching { load(meta.assetPath) } }
            }.awaitAll()
        }
    }

    fun allScriptMeta(): List<LxScriptMeta> = _scripts.value

    suspend fun resolveMusicUrl(assetPath: String, song: LxSong, quality: String? = null): String? =
        (resolveMusicUrlDetailed(assetPath, song, quality) as? LxMusicUrlEvent.Success)?.url

    private suspend fun resolveMusicUrlDetailed(
        assetPath: String,
        song: LxSong,
        quality: String? = null,
    ): LxMusicUrlEvent {
        if (!engine.isLoaded(assetPath) && _scripts.value.any { it.assetPath == assetPath }) {
            Timber.d("resolveMusicUrl: runtime not loaded, loading $assetPath on demand")
            runCatching { load(assetPath) }
        }
        val caps = engine.getCapabilities(assetPath) ?: _loaded.value[assetPath]
        if (caps == null) {
            Timber.w("resolveMusicUrl: caps NULL, assetPath=$assetPath, loaded=${_loaded.value.keys}")
            return LxMusicUrlEvent.Unavailable
        }
        val sourceInfo = caps.sources[song.source]
        if (sourceInfo == null) {
            Timber.w("resolveMusicUrl: source '${song.source}' NOT supported by script. supported=${caps.sources.keys}")
            val fallbackPath = engine.findScriptForSource(song.source)
            if (fallbackPath != null && fallbackPath != assetPath) {
                Timber.d("resolveMusicUrl: trying fallback script $fallbackPath for ${song.source}")
                return resolveMusicUrlDetailed(fallbackPath, song, quality)
            }
            return LxMusicUrlEvent.Unavailable
        }
        val internalAvailable = sourceInfo.qualitys.map { normalizeScriptQuality(it) }
        val resolvedInternal: String? = when {
            quality == null -> null
            quality == LxQualities.MASTER ||
                quality == LxQualities.ATMOS ||
                quality == LxQualities.FLAC24 -> quality
            quality in internalAvailable -> quality
            else -> PREFERRED_QUALITY_ORDER.firstOrNull { it in internalAvailable }
                ?: internalAvailable.firstOrNull()
        }
        val resolvedQuality = resolvedInternal?.let { internal ->
            if (internal in sourceInfo.qualitys) internal
            else scriptQualityAliases[internal] ?: internal
        }
        Timber.d("resolveMusicUrl: calling engine source=${song.source} mid=${song.songmid} q=$resolvedQuality")
        val extraInfo = mutableMapOf(
            "name" to song.name,
            "singer" to song.singer,
            "albumName" to song.albumName,
            "interval" to song.interval.toString(),
        )
        song.pic?.let { extraInfo["pic"] = it }
        song.hash?.let { extraInfo["hash"] = it }
        song.copyrightId?.let { extraInfo["copyrightId"] = it }
        song.extraFields.forEach { (k, v) -> extraInfo[k] = v }
        return engine.requestMusicUrlDetailed(
            assetPath = assetPath,
            source = song.source,
            songmid = song.songmid,
            quality = resolvedQuality ?: "320k",
            extraInfo = extraInfo,
            timeoutMs = musicUrlEventTimeoutMs,
        )
    }

    suspend fun resolveMusicUrlStrict(assetPath: String, song: LxSong, quality: String): String? =
        (resolveMusicUrlEvent(assetPath, song, quality) as? LxMusicUrlEvent.Success)?.url

    suspend fun resolveMusicUrlEvent(assetPath: String, song: LxSong, quality: String): LxMusicUrlEvent {
        if (!engine.isLoaded(assetPath) && _scripts.value.any { it.assetPath == assetPath }) {
            runCatching { load(assetPath) }
        }
        val caps = engine.getCapabilities(assetPath) ?: _loaded.value[assetPath]
            ?: return LxMusicUrlEvent.Unavailable
        if (caps.sources[song.source] == null) return LxMusicUrlEvent.Unavailable
        val scriptQuality = scriptQualityAliases[quality] ?: quality
        val extraInfo = mutableMapOf(
            "name" to song.name,
            "singer" to song.singer,
            "albumName" to song.albumName,
            "interval" to song.interval.toString(),
        )
        song.pic?.let { extraInfo["pic"] = it }
        song.hash?.let { extraInfo["hash"] = it }
        song.copyrightId?.let { extraInfo["copyrightId"] = it }
        song.extraFields.forEach { (k, v) -> extraInfo[k] = v }
        return engine.requestMusicUrlDetailed(
            assetPath = assetPath,
            source = song.source,
            songmid = song.songmid,
            quality = scriptQuality,
            extraInfo = extraInfo,
            timeoutMs = musicUrlEventTimeoutMs,
        )
    }


    private val otherSourceCache = android.util.LruCache<String, List<LxSong>>(100)
    private val matchedSongCache = android.util.LruCache<String, LxSong>(200)

    private val scriptUrlDeadAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val scriptUrlDeadTtlMs = 5 * 60 * 1000L

    private val scriptResolveSemaphores = java.util.concurrent.ConcurrentHashMap<String, Semaphore>()
    private fun scriptSemaphore(assetPath: String): Semaphore =
        scriptResolveSemaphores.getOrPut(assetPath) { Semaphore(3) }

    private val musicUrlEventTimeoutMs = 6_000L
    private val fallbackTotalTimeoutMs = 20_000L
    private val crossSourceCandidateTimeoutMs = 8_000L

    data class ResolvedPlayUrl(
        val url: String,
        val fullQuality: Boolean,
        val quality: String? = null,
        val qualityTrusted: Boolean = false,
    )

    suspend fun resolveMusicUrlWithFallback(song: LxSong, quality: String? = null): String? =
        resolveMusicUrlWithFallbackMeta(song, quality)?.url

    // 取链总入口：官方层和脚本层谁先走看 decidePlaybackRoute，两边都拿到就比音质
    suspend fun resolveMusicUrlWithFallbackMeta(
        song: LxSong,
        quality: String? = null,
    ): ResolvedPlayUrl? {
        val cacheKey = "${song.source}/${song.songmid}"
        val effectiveQuality = quality ?: runCatching {
            userPreferencesRepository.lxMusicPreferredQualityFlow.first()
        }.getOrNull()

        val route = decidePlaybackRoute(song, effectiveQuality)

        // 脚本链：先试上次跨源命中的，再试本音源脚本（主脚本音质不够会换别的试），都不行才去其他音源搜
        suspend fun layerB(): Pair<String, String?>? {
            val skipOriginalScript = isScriptUrlDead(song.source, song.songmid)
            if (skipOriginalScript) {
                Timber.d("layerB: script URL dead for $cacheKey, skip original source scripts but still try cross-source")
            }
            val requestedRank = qualityRank(effectiveQuality)
            val matched = matchedSongCache.get(cacheKey)
            if (matched != null) {
                val viaMatched = resolveViaScripts(matched, effectiveQuality)
                if (viaMatched == null) {
                    matchedSongCache.remove(cacheKey)
                } else {
                    val (_, matchedQ) = viaMatched
                    if (qualityRank(matchedQ) >= requestedRank) return viaMatched
                    Timber.d("layerB: cached match ${matched.source}/${matched.songmid} " +
                        "quality=$matchedQ below requested=$effectiveQuality, drop and retry chain for $cacheKey")
                    matchedSongCache.remove(cacheKey)
                }
            }
            if (!skipOriginalScript) {
                resolveViaScripts(song, effectiveQuality)?.let { return it }
            }
            if (allScriptPaths().none { it !in disabledPaths }) {
                Timber.d("layerB: no enabled scripts, skip cross-source search for $cacheKey")
                return null
            }
            val candidates = findOtherSourceSongs(song)
                .filterNot { matched != null && it.source == matched.source && it.songmid == matched.songmid }
                .take(8)
            if (candidates.isEmpty()) return null
            val best = coroutineScope {
                val deferreds = candidates
                    .map { candidate ->
                        async {
                            kotlinx.coroutines.withTimeoutOrNull(crossSourceCandidateTimeoutMs) {
                                resolveViaScripts(candidate, effectiveQuality)?.let { candidate to it }
                            }
                        }
                    }
                try {
                    firstNonNull(deferreds)
                } finally {
                    deferreds.forEach { it.cancel() }
                }
            }
            if (best == null) return null
            matchedSongCache.put(cacheKey, best.first)
            Timber.d("resolveMusicUrlWithFallback: cross-source hit ${best.first.source}/${best.first.songmid} for $cacheKey")
            return best.second
        }

        val requestedRankFinal = qualityRank(effectiveQuality)

        fun pickBest(
            scriptResult: Pair<String, String?>?,
            official: OfficialOutcome,
        ): ResolvedPlayUrl? {
            val degraded = official as? OfficialOutcome.Degraded
            return when {
                scriptResult == null && degraded == null -> null
                scriptResult == null -> ResolvedPlayUrl(
                    degraded!!.url,
                    fullQuality = officialLevelRank(degraded.level) >= requestedRankFinal,
                    quality = officialLevelToQuality(degraded.level),
                    qualityTrusted = true,
                )
                degraded == null -> ResolvedPlayUrl(
                    scriptResult.first,
                    fullQuality = qualityRank(scriptResult.second) >= requestedRankFinal,
                    quality = scriptResult.second,
                )
                qualityRank(scriptResult.second) > officialLevelRank(degraded.level) -> ResolvedPlayUrl(
                    scriptResult.first,
                    fullQuality = qualityRank(scriptResult.second) >= requestedRankFinal,
                    quality = scriptResult.second,
                )
                else -> ResolvedPlayUrl(
                    degraded.url,
                    fullQuality = officialLevelRank(degraded.level) >= requestedRankFinal,
                    quality = officialLevelToQuality(degraded.level),
                    qualityTrusted = true,
                )
            }
        }

        val result = kotlinx.coroutines.withTimeoutOrNull(fallbackTotalTimeoutMs) {
            if (route == PlaybackRoute.OFFICIAL_FIRST) {
                when (val outcome = callOfficialLayer(song, effectiveQuality, cacheKey)) {
                    is OfficialOutcome.Full ->
                        return@withTimeoutOrNull ResolvedPlayUrl(
                            outcome.url, fullQuality = true,
                            quality = officialLevelToQuality(outcome.level),
                            qualityTrusted = true,
                        )
                    else -> return@withTimeoutOrNull pickBest(layerB(), outcome)
                }
            }
            val scriptResult = layerB()
            if (scriptResult != null && qualityRank(scriptResult.second) >= requestedRankFinal) {
                return@withTimeoutOrNull ResolvedPlayUrl(
                    scriptResult.first,
                    fullQuality = true,
                    quality = scriptResult.second,
                )
            }
            when (val outcome = callOfficialLayer(song, effectiveQuality, cacheKey)) {
                is OfficialOutcome.Full ->
                    ResolvedPlayUrl(
                        outcome.url, fullQuality = true,
                        quality = officialLevelToQuality(outcome.level),
                        qualityTrusted = true,
                    )
                else -> pickBest(scriptResult, outcome)
            }
        }
        if (result == null) {
            Timber.w("resolveMusicUrlWithFallback: all sources failed or timed out for $cacheKey")
        } else if (result.quality != null) {
            playbackQualityTracker.report(song.source, song.songmid, result.quality, result.qualityTrusted)
        }
        return result
    }

    suspend fun currentPreferredQuality(): String =
        runCatching { userPreferencesRepository.lxMusicPreferredQualityFlow.first() }
            .getOrNull() ?: LxQualities.Q320

    @Volatile
    var preferredQualitySync: String = LxQualities.Q320
        private set

    init {
        repoScope.launch {
            userPreferencesRepository.lxMusicPreferredQualityFlow.collect { q ->
                preferredQualitySync = q
            }
        }
    }

    private enum class PlaybackRoute { OFFICIAL_FIRST, SCRIPT_FIRST }

    private sealed class OfficialOutcome {
        data class Full(val url: String, val level: String) : OfficialOutcome()
        data class Degraded(val url: String, val level: String) : OfficialOutcome()
        object Unavailable : OfficialOutcome()
        object Failed : OfficialOutcome()
    }

    private val officialUnavailableAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    // VIP/无版权拉黑时长，改这个旋钮即可
    private val officialBlockTtlMs = 10 * 60 * 1000L

    fun clearOfficialUnavailableCache() {
        officialUnavailableAt.clear()
    }

    fun clearCrossSourceCaches() {
        matchedSongCache.evictAll()
        otherSourceCache.evictAll()
    }


    private fun sweepExpiredEntries(
        map: java.util.concurrent.ConcurrentHashMap<String, Long>,
        threshold: Int = 500,
    ) {
        if (map.size <= threshold) return
        val now = System.currentTimeMillis()
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= now) iterator.remove()
        }
    }

    fun markScriptUrlDead(source: String, songmid: String) {
        val key = "$source/$songmid"
        scriptUrlDeadAt[key] = System.currentTimeMillis() + scriptUrlDeadTtlMs
        sweepExpiredEntries(scriptUrlDeadAt)
        Timber.w("markScriptUrlDead: $key blacklisted for ${scriptUrlDeadTtlMs / 1000}s")
    }

    fun isScriptUrlDead(source: String, songmid: String): Boolean {
        val key = "$source/$songmid"
        val until = scriptUrlDeadAt[key] ?: return false
        if (System.currentTimeMillis() < until) return true
        scriptUrlDeadAt.remove(key)
        return false
    }

    fun clearScriptUrlDeadCache() {
        scriptUrlDeadAt.clear()
    }


    fun isOfficialUnavailable(source: String, songmid: String): Boolean {
        val key = "$source/$songmid"
        val until = officialUnavailableAt[key] ?: return false
        if (System.currentTimeMillis() < until) return true
        officialUnavailableAt.remove(key)
        return false
    }

    private val officialTriedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun isOfficialLayerTried(source: String, songmid: String): Boolean {
        val key = "$source/$songmid"
        val until = officialTriedAt[key] ?: return false
        if (System.currentTimeMillis() < until) return true
        officialTriedAt.remove(key)
        return false
    }
    fun markOfficialTried(source: String, songmid: String) {
        officialTriedAt["${source}/${songmid}"] = System.currentTimeMillis() + 60_000L
        sweepExpiredEntries(officialTriedAt)
    }

    // 官方层取链；VIP/无版权的歌记下来拉黑一段时间，期间别再来问
    private suspend fun resolvePlatformOfficialLayer(song: LxSong, quality: String?): OfficialOutcome {
        markOfficialTried(song.source, song.songmid)
        officialUnavailableAt["${song.source}/${song.songmid}"]?.let { until ->
            if (System.currentTimeMillis() < until) {
                Timber.d("resolvePlatformOfficialLayer: ${song.source}/${song.songmid} in VIP block, skip")
                return OfficialOutcome.Unavailable
            }
            officialUnavailableAt.remove("${song.source}/${song.songmid}")
        }
        val result = when (song.source) {
            LxSources.TENCENT -> tencentOfficialApi.resolveUrl(song.songmid, song.extraFields["strMediaMid"])
            LxSources.KUGOU -> kugouOfficialApi.resolveUrl(song.hash ?: song.songmid)
            LxSources.KUWO -> accountExtension.kwResolvePlayUrl(song.songmid) ?: return OfficialOutcome.Failed
            else -> return OfficialOutcome.Failed
        }
        return when (result) {
            is PlatformResult.Success -> {
                Timber.d("resolvePlatformOfficialLayer: standard for ${song.source}/${song.songmid}")
                OfficialOutcome.Full(result.url, result.level)
            }
            PlatformResult.VipOrNoCopyright -> {
                officialUnavailableAt["${song.source}/${song.songmid}"] =
                    System.currentTimeMillis() + officialBlockTtlMs
                sweepExpiredEntries(officialUnavailableAt)
                OfficialOutcome.Unavailable
            }
            PlatformResult.NetworkError -> OfficialOutcome.Failed
        }
    }

    private suspend fun callOfficialLayer(song: LxSong, quality: String?, cacheKey: String): OfficialOutcome {
        if (song.source != LxSources.NETEASE) {
            return resolvePlatformOfficialLayer(song, quality)
        }
        markOfficialTried(song.source, song.songmid)
        officialUnavailableAt[cacheKey]?.let { until ->
            if (System.currentTimeMillis() < until) {
                Timber.d("callOfficialLayer: $cacheKey in no-copyright block, skip")
                return OfficialOutcome.Unavailable
            }
            officialUnavailableAt.remove(cacheKey)
        }
        val requestedRank = qualityRank(quality)
        return when (val r = neteaseOfficialApi.resolveUrl(song.songmid, quality ?: LxQualities.Q320)) {
            is NeteaseOfficialApi.Result.Success -> {
                val actualRank = officialLevelRank(r.level)
                if (actualRank >= requestedRank) {
                    Timber.d("callOfficialLayer: full quality level=${r.level} for $cacheKey")
                    OfficialOutcome.Full(r.url, r.level)
                } else {
                    Timber.d("callOfficialLayer: degraded level=${r.level} < requested rank=$requestedRank for $cacheKey")
                    OfficialOutcome.Degraded(r.url, r.level)
                }
            }
            is NeteaseOfficialApi.Result.VipOrNoCopyright -> {
                officialUnavailableAt[cacheKey] = System.currentTimeMillis() + officialBlockTtlMs
                sweepExpiredEntries(officialUnavailableAt)
                OfficialOutcome.Unavailable
            }
            is NeteaseOfficialApi.Result.CookieInvalid -> {
                neteaseCookieStore.markCookieInvalid()
                OfficialOutcome.Failed
            }
            is NeteaseOfficialApi.Result.NetworkError -> OfficialOutcome.Failed
        }
    }

    // 只有网易要分先后：买过或 VIP 能播的走官方，其他平台一律先脚本
    private fun decidePlaybackRoute(song: LxSong, quality: String?): PlaybackRoute {
        if (song.source != LxSources.NETEASE) return PlaybackRoute.SCRIPT_FIRST
        if (!neteaseCookieStore.hasCookie() || !neteaseCookieStore.cookieValidFlow.value)
            return PlaybackRoute.SCRIPT_FIRST
        val fee = song.extraFields["fee"]?.toIntOrNull()
        val noCopyright = song.extraFields["noCopyright"] == "1"
        val payed = song.extraFields["payed"] == "1"
        val albumPurchased = neteaseCookieStore.isAlbumPurchased(song.extraFields["albumId"])
        if (noCopyright) return PlaybackRoute.SCRIPT_FIRST
        if (payed || albumPurchased) return PlaybackRoute.OFFICIAL_FIRST
        if (fee == 4) return PlaybackRoute.OFFICIAL_FIRST
        val rank = qualityRank(quality)
        return when (val vipType = neteaseCookieStore.getVipType()) {
            13 -> PlaybackRoute.OFFICIAL_FIRST
            11 -> if (rank <= qualityRank(LxQualities.FLAC)) PlaybackRoute.OFFICIAL_FIRST
                  else PlaybackRoute.SCRIPT_FIRST
            else -> {
                if (rank <= qualityRank(LxQualities.Q320) && (fee == 0 || fee == 8))
                    PlaybackRoute.OFFICIAL_FIRST
                else
                    PlaybackRoute.SCRIPT_FIRST
            }
        }
    }

    private fun qualityRank(q: String?): Int = when (q) {
        LxQualities.Q128 -> 0
        LxQualities.Q320 -> 1
        LxQualities.FLAC -> 2
        LxQualities.FLAC24 -> 3
        LxQualities.ATMOS -> 4
        LxQualities.MASTER -> 5
        else -> 1
    }

    private fun officialLevelRank(level: String?): Int = when (level) {
        "standard" -> 0
        "exhigh", "higher" -> 1
        "lossless" -> 2
        "hires" -> 3
        "jyeffect", "jydj", "sky" -> 4
        "jymaster" -> 5
        else -> -1
    }

    private fun officialLevelToQuality(level: String?): String? = when (level) {
        "standard" -> LxQualities.Q128
        "exhigh", "higher" -> LxQualities.Q320
        "lossless" -> LxQualities.FLAC
        "hires" -> LxQualities.FLAC24
        "jyeffect", "jydj", "sky" -> LxQualities.ATMOS
        "jymaster" -> LxQualities.MASTER
        else -> null
    }

    private suspend fun <T> firstNonNull(
        deferreds: List<kotlinx.coroutines.Deferred<T?>>,
    ): T? {
        val pending = deferreds.toMutableList()
        while (pending.isNotEmpty()) {
            val done = kotlinx.coroutines.selects.select<kotlinx.coroutines.Deferred<T?>> {
                pending.forEach { d -> d.onAwait { d } }
            }
            pending.remove(done)
            val r = done.getCompleted()
            if (r != null) return r
        }
        return null
    }

    private suspend fun resolveViaScripts(song: LxSong, quality: String?): Pair<String, String?>? {
        val tried = mutableSetOf<String>()
        val requestedRank = qualityRank(quality)
        val supports: (String) -> Boolean = { p ->
            p !in disabledPaths && engine.isSourceSupported(p, song.source)
        }

        val primaryPath = activeScriptPath()?.takeIf(supports)
        if (primaryPath != null) {
            tried.add(primaryPath)
            when (val primary = resolveViaSingleScript(primaryPath, song, quality)) {
                is ScriptOutcome.Ok -> {
                    if (quality == null || qualityRank(primary.quality) >= requestedRank) return primary.url to primary.quality
                    for (scriptPath in allScriptPaths()) {
                        if (!supports(scriptPath) || scriptPath in tried) continue
                        tried.add(scriptPath)
                        val high = tryScriptAtQuality(scriptPath, song, quality)
                        if (high != null && qualityRank(high.second) >= requestedRank) return high
                    }
                    return primary.url to primary.quality
                }
                ScriptOutcome.Rejected -> return null
                ScriptOutcome.Unavailable -> {}
            }
        }
        for (scriptPath in allScriptPaths()) {
            if (!supports(scriptPath) || scriptPath in tried) continue
            tried.add(scriptPath)
            when (val r = resolveViaSingleScript(scriptPath, song, quality)) {
                is ScriptOutcome.Ok -> return r.url to r.quality
                ScriptOutcome.Rejected -> return null
                ScriptOutcome.Unavailable -> {}
            }
        }
        return null
    }

    private suspend fun tryScriptAtQuality(
        scriptPath: String,
        song: LxSong,
        quality: String,
    ): Pair<String, String?>? {
        val url = runCatching { resolveMusicUrl(scriptPath, song, quality) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrNull() ?: return null
        if (streamUrlValidator?.invoke(url) == false) {
            Timber.w("tryScriptAtQuality: rejected by security (${url.take(80)}) for ${song.source}/${song.songmid}")
            return null
        }
        return url to inferScriptResultQuality(url, quality)
    }

    private val qualityFallbackNext = mapOf(
        LxQualities.MASTER to LxQualities.ATMOS,
        LxQualities.ATMOS to LxQualities.FLAC24,
        LxQualities.FLAC24 to LxQualities.FLAC,
        LxQualities.FLAC to LxQualities.Q320,
        LxQualities.Q320 to LxQualities.Q128,
    )

    private val scriptQualityAliases = mapOf(LxQualities.FLAC24 to "hires")
    private fun normalizeScriptQuality(q: String): String =
        scriptQualityAliases.entries.firstOrNull { it.value == q }?.key ?: q

    private sealed interface ScriptOutcome {
        data class Ok(val url: String, val quality: String?) : ScriptOutcome
        object Rejected : ScriptOutcome
        object Unavailable : ScriptOutcome
    }

    private suspend fun resolveViaSingleScript(
        scriptPath: String,
        song: LxSong,
        quality: String?,
    ): ScriptOutcome = scriptSemaphore(scriptPath).withPermit {
        val event = runCatching { resolveMusicUrlDetailed(scriptPath, song, quality) }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
            .getOrElse { LxMusicUrlEvent.Unavailable }
        when (event) {
            is LxMusicUrlEvent.Success -> {
                if (streamUrlValidator?.invoke(event.url) == false) {
                    Timber.w("resolveViaSingleScript: rejected by security (${event.url.take(80)}) for ${song.source}/${song.songmid}")
                    ScriptOutcome.Unavailable
                } else {
                    val actualQ = inferScriptResultQuality(event.url, quality)
                    if (actualQ != quality) {
                        Timber.d(
                            "resolveViaSingleScript: script returned lossy url for q=$quality, " +
                                "relabel as $actualQ for ${song.source}/${song.songmid}"
                        )
                    }
                    ScriptOutcome.Ok(event.url, actualQ)
                }
            }
            is LxMusicUrlEvent.ScriptRejected -> {
                Timber.d("resolveViaSingleScript: script rejected '${event.message}' for ${song.source}/${song.songmid}, abort downgrade")
                ScriptOutcome.Rejected
            }
            LxMusicUrlEvent.Unavailable -> ScriptOutcome.Unavailable
        }
    }

    private fun inferScriptResultQuality(url: String, requested: String?): String? {
        val path = url.substringBefore('?').lowercase()
        val lossy = path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") ||
            path.endsWith(".ogg") || path.endsWith(".opus") || path.endsWith(".wma")
        return when {
            lossy ->
                if (requested == null || qualityRank(requested) <= qualityRank(LxQualities.Q320)) {
                    requested
                } else {
                    LxQualities.Q320
                }
            path.endsWith(".flac") ->
                if (requested != null && qualityRank(requested) >= qualityRank(LxQualities.FLAC)) {
                    requested
                } else {
                    LxQualities.FLAC
                }
            else -> requested
        }
    }

    internal var streamUrlValidator: ((String) -> Boolean)? = null

    suspend fun findOtherSourceSongs(song: LxSong): List<LxSong> {
        if (song.name.isBlank()) {
            Timber.w("findOtherSourceSongs: skip, song name blank for ${song.source}/${song.songmid}")
            return emptyList()
        }
        val cacheKey = "${song.source}/${song.songmid}"
        otherSourceCache.get(cacheKey)?.let { return it }

        val keyword = "${song.name} ${song.singer}".trim()
        val candidates = coroutineScope {
            LxSources.ALL.filter { it != song.source }
                .map { src ->
                    async {
                        kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                            runCatching { searchMusic("", src, keyword) }
                                .onFailure { Timber.w(it, "findOtherSourceSongs: search $src failed") }
                                .getOrDefault(emptyList())
                        } ?: emptyList()
                    }
                }
                .awaitAll()
                .flatten()
        }
        val matched = rankMatchedSongs(song, candidates)
        if (matched.isNotEmpty()) {
            otherSourceCache.put(cacheKey, matched)
        }
        Timber.d("findOtherSourceSongs: ${matched.size} matches for $cacheKey")
        return matched
    }

    // 打分先卡时长（差 5 秒以上直接排除），再按歌名/歌手/专辑谁完全一致分等级
    private fun rankMatchedSongs(song: LxSong, candidates: List<LxSong>): List<LxSong> {
        val fName = filterMatchStr(song.name)
        val fSinger = filterMatchStr(sortSingerNames(song.singer))
        val fAlbum = filterMatchStr(song.albumName)
        val fInterval = song.interval

        val scored = candidates.mapNotNull { c ->
            val cName = filterMatchStr(c.name)
            val cSinger = filterMatchStr(sortSingerNames(c.singer))
            val cAlbum = filterMatchStr(c.albumName)
            val intvEq = fInterval == 0L || c.interval == 0L || kotlin.math.abs(c.interval - fInterval) <= 5
            if (!intvEq) return@mapNotNull null
            val nameIncl = fName.isNotEmpty() && cName.isNotEmpty() &&
                (fName.contains(cName) || cName.contains(fName))
            val singerIncl = fSinger.isEmpty() || cSinger.isEmpty() ||
                fSinger.contains(cSinger) || cSinger.contains(fSinger)
            val exactName = fName.isNotEmpty() && cName == fName
            val exactSinger = fSinger.isNotEmpty() && cSinger == fSinger
            val exactAlbum = fAlbum.isNotEmpty() && cAlbum == fAlbum
            val pass = (exactName && singerIncl) ||
                (exactSinger && nameIncl) ||
                (exactAlbum && singerIncl && nameIncl)
            if (!pass) return@mapNotNull null
            val tier = when {
                exactSinger && exactName && fInterval != 0L && c.interval == fInterval -> 0
                exactName && exactSinger && exactAlbum -> 1
                exactSinger && exactName -> 2
                exactName -> 3
                exactSinger -> 4
                exactAlbum -> 5
                else -> 6
            }
            tier to c
        }
        return scored.sortedWith(compareBy({ it.first }, { it.second.singer }, { it.second.name })).map { it.second }
    }

    private fun filterMatchStr(s: String?): String =
        (s ?: "").replace(Regex("[\\s'.,，&\"、()（）`~<>|/\\[\\]!！-]"), "").lowercase()

    private fun sortSingerNames(singer: String?): String {
        val s = singer ?: return ""
        val parts = s.split(Regex("、|&|;|；|/|,|，|\\|")).filter { it.isNotBlank() }
        return if (parts.size <= 1) s.trim() else parts.sorted().joinToString("、")
    }

    // endregion

    suspend fun resolveLyric(assetPath: String, song: LxSong): LxLyricResult? {
        val caps = engine.getCapabilities(assetPath) ?: _loaded.value[assetPath]
        if (caps == null) {
            Timber.w("resolveLyric: caps NULL, assetPath=$assetPath")
            return null
        }
        val sourceInfo = caps.sources[song.source]
        if (sourceInfo == null || "lyric" !in sourceInfo.actions) {
            val fallbackPath = engine.findScriptForSource(song.source)
            if (fallbackPath != null && fallbackPath != assetPath) {
                Timber.d("resolveLyric: trying fallback script $fallbackPath for ${song.source}")
                return resolveLyric(fallbackPath, song)
            }
            Timber.w("resolveLyric: source '${song.source}' lyric NOT supported")
            return null
        }
        val extraInfo = buildLyricExtraInfo(song)
        return engine.requestLyric(
            assetPath = assetPath,
            source = song.source,
            songmid = song.songmid,
            extraInfo = extraInfo,
        )
    }

    private fun buildLyricExtraInfo(song: LxSong): Map<String, String> {
        val extraInfo = mutableMapOf(
            "name" to song.name,
            "singer" to song.singer,
            "albumName" to song.albumName,
            "interval" to song.interval.toString(),
        )
        song.pic?.let { extraInfo["pic"] = it }
        song.hash?.let { extraInfo["hash"] = it }
        song.copyrightId?.let { extraInfo["copyrightId"] = it }
        song.extraFields.forEach { (k, v) -> extraInfo[k] = v }
        return extraInfo
    }

    // 歌词走各平台直连接口，不经过脚本
    suspend fun fetchLyric(song: LxSong): LxLyricResult? = withContext(Dispatchers.IO) {
        when (song.source) {
            "wy" -> fetchNeteaseLyric(song.songmid)
            "tx" -> fetchQqLyric(song.songmid)
            "kg" -> fetchKugouLyric(song)
            "kw" -> fetchKuwoLyric(song.songmid)
            else -> null
        }
    }

    private suspend fun fetchQqLyric(songmid: String): LxLyricResult? {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songmid" +
            "&g_tk=5381&loginUin=0&hostUin=0&format=json&inCharset=utf8&outCharset=utf-8&platform=yqq"
        val req = Request.Builder().url(url)
            .header("Referer", "https://y.qq.com/")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                if (root.get("retcode")?.asInt != 0) return@use null
                val lyricB64 = root.get("lyric")?.asString ?: return@use null
                val lyric = String(android.util.Base64.decode(lyricB64, android.util.Base64.DEFAULT), Charsets.UTF_8)
                if (lyric.isBlank()) return@use null
                val transB64 = root.get("trans")?.asString
                val tlyric = transB64?.let {
                    runCatching {
                        String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    }.getOrNull()
                }?.takeIf { it.isNotBlank() }
                LxLyricResult(lyric = lyric, tlyric = tlyric)
            }
        }.getOrElse {
            Timber.w(it, "fetchQqLyric failed for songmid=$songmid")
            null
        }
    }

    private suspend fun fetchKugouLyric(song: LxSong): LxLyricResult? {
        val hash = song.hash ?: return null
        val time = song.interval * 1000
        val headers = mapOf(
            "KG-RC" to "1",
            "KG-THash" to "expand_search_manager.cpp:852736169:451",
            "User-Agent" to "KuGou2012-9020-ExpandSearchManager",
        )
        val searchUrl = "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc" +
            "&keyword=${URLEncoder.encode(song.name, "UTF-8")}&hash=$hash&timelength=$time&lrctxt=1"
        val candidate = runCatching {
            okHttpClient.newCall(Request.Builder().url(searchUrl).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build())
                .execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                    val candidates = root.getAsJsonArray("candidates") ?: return@use null
                    candidates.firstOrNull { it.asJsonObject.get("krctype")?.asInt == 0 }
                        ?: candidates.firstOrNull { it.asJsonObject.get("krctype")?.asInt != 1 }
                }?.asJsonObject
        }.getOrElse {
            Timber.w(it, "fetchKugouLyric search failed for ${song.songmid}")
            null
        } ?: return null
        val id = candidate.get("id")?.asString ?: return null
        val accessKey = candidate.get("accesskey")?.asString ?: return null
        val krctype = candidate.get("krctype")?.asInt ?: 0
        if (krctype == 1) return null

        val downloadUrl = "https://lyrics.kugou.com/download?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=lrc&charset=utf8"
        return runCatching {
            okHttpClient.newCall(
                Request.Builder().url(downloadUrl).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val content = root.get("content")?.asString ?: return@use null
                val lyric = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
                lyric.takeIf { it.isNotBlank() }?.let { LxLyricResult(lyric = it) }
            }
        }.getOrElse {
            Timber.w(it, "fetchKugouLyric download failed for ${song.songmid}")
            null
        }
    }

    private suspend fun fetchKuwoLyric(songmid: String): LxLyricResult? {
        val rid = songmid.removePrefix("MUSIC_")
        val url = "http://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$rid"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val lrclist = root.getAsJsonObject("data")?.getAsJsonArray("lrclist")
                    ?.takeIf { it.size() > 0 } ?: return@use null
                val lines = mutableListOf<String>()
                val seenTimes = mutableSetOf<String>()
                for (el in lrclist) {
                    val obj = el.asJsonObject
                    val timeSec = obj.get("time")?.asString?.toDoubleOrNull() ?: continue
                    val text = obj.get("lineLyric")?.asString ?: ""
                    val m = (timeSec / 60).toInt()
                    val s = timeSec % 60
                    val tag = "[%02d:%05.2f]".format(m, s)
                    if (!seenTimes.add(tag)) continue
                    lines.add("$tag$text")
                }
                val lyric = lines.joinToString("\n")
                lyric.takeIf { it.isNotBlank() }?.let { LxLyricResult(lyric = it) }
            }
        }.getOrElse {
            Timber.w(it, "fetchKuwoLyric failed for songmid=$songmid")
            null
        }
    }

    private suspend fun fetchNeteaseLyric(songmid: String): LxLyricResult? = withContext(Dispatchers.IO) {
        fetchNeteaseLyricV1(songmid) ?: fetchNeteaseLyricLegacy(songmid)
    }

    private suspend fun fetchNeteaseLyricV1(songmid: String): LxLyricResult? = withContext(Dispatchers.IO) {
        val path = "/api/song/lyric/v1"
        val payload = """{"id":"$songmid","cp":false,"tv":0,"lv":0,"rv":0,"kv":0,"yv":0,"ytv":0,"yrv":0}"""
        val hosts = listOf("interface3.music.163.com", "interface.music.163.com")
        repeat(3) { attempt ->
            if (attempt > 0) delay(200)
            val url = "https://${hosts[attempt % hosts.size]}/eapi/song/lyric/v1"
            val encrypted = NeteaseWeapiCrypto.eapiEncrypt(path, payload)
            val body = "params=$encrypted".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.90 Safari/537.36")
                .header("Origin", "https://music.163.com")
                .post(body)
                .build()
            val result = runCatching {
                okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val root = JsonParser.parseString(bodyStr).asJsonObject
                    val code = root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
                    if (code != 200) {
                        Timber.w("fetchNeteaseLyricV1: bad code=$code for songmid=$songmid (attempt=$attempt)")
                        return@use null
                    }
                    val lyric = root.getAsJsonObject("lrc")?.get("lyric")?.asString
                    if (lyric.isNullOrBlank()) return@use null
                    val tlyric = root.getAsJsonObject("tlyric")?.get("lyric")?.asString
                    val rlyric = root.getAsJsonObject("romalrc")?.get("lyric")?.asString
                    LxLyricResult(lyric = lyric, tlyric = tlyric, rlyric = rlyric)
                }
            }.getOrElse {
                Timber.w(it, "fetchNeteaseLyricV1 failed for songmid=$songmid (attempt=$attempt)")
                null
            }
            if (result != null) return@withContext result
        }
        null
    }

    private suspend fun fetchNeteaseLyricLegacy(songmid: String): LxLyricResult? = withContext(Dispatchers.IO) {
        val url = "https://music.163.com/api/song/lyric?os=pc&id=$songmid&lv=1&kv=1&tv=1&rv=1"
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .header("Referer", "https://music.163.com/")
            .get()
            .build()
        runCatching {
            okHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
                val code = root.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: -1
                if (code != 200) {
                    Timber.w("fetchNeteaseLyricLegacy: bad code=$code for songmid=$songmid")
                    return@use null
                }
                val lyric = root.getAsJsonObject("lrc")?.get("lyric")?.asString
                if (lyric.isNullOrBlank()) return@use null
                val tlyric = root.getAsJsonObject("tlyric")?.get("lyric")?.asString
                val rlyric = root.getAsJsonObject("romalrc")?.get("lyric")?.asString
                    ?: root.getAsJsonObject("rlyric")?.get("lyric")?.asString
                LxLyricResult(lyric = lyric, tlyric = tlyric, rlyric = rlyric)
            }
        }.getOrElse {
            Timber.w(it, "fetchNeteaseLyricLegacy failed for songmid=$songmid")
            null
        }
    }

    private val scriptProxyPrefixes = listOf(
        "https://cors.isteed.cc/",
        "https://gh-proxy.com/",
        "https://gh-proxy.net/",
        "https://mirror.ghproxy.com/",
    )

    private fun buildScriptDownloadCandidates(rawUrl: String): List<String> {
        val original = rawUrl.trim()
        val target = scriptProxyPrefixes.firstOrNull { original.startsWith(it) }
            ?.let { original.removePrefix(it) } ?: original
        var decoded = target
        while (decoded.contains("%25")) {
            decoded = decoded.replace("%25", "%")
        }
        val candidates = mutableListOf(original)
        candidates.addAll(scriptProxyPrefixes.map { it + decoded })
        candidates.add(decoded)
        return candidates.distinct()
    }

    suspend fun importFromUrl(url: String): Result<LxScriptMeta> = withContext(Dispatchers.IO) {
        runCatching {
            var lastErr: Exception? = null
            for (candidate in buildScriptDownloadCandidates(url)) {
                try {
                    val client = okHttpClient.newBuilder()
                        .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val req = Request.Builder().url(candidate).get().build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("下载失败：HTTP ${resp.code}，请检查链接是否有效")
                        }
                        val body = resp.body?.string().orEmpty()
                        validateLxScriptBody(body)
                        val fileName = "imported_${System.currentTimeMillis()}.js"
                        val file = File(importedDir, fileName)
                        file.writeText(body)
                        refreshScripts()
                        return@runCatching parseScriptMeta(fileName, file.absolutePath, body, isImported = true)
                    }
                } catch (e: Exception) {
                    lastErr = e
                }
            }
            throw lastErr ?: IllegalStateException("所有下载源均失败，请检查网络或稍后再试")
        }
    }

    suspend fun importFromFile(uri: Uri): Result<LxScriptMeta> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
                ?: throw IllegalStateException("无法读取文件")
            validateLxScriptBody(raw)
            val fileName = "imported_${System.currentTimeMillis()}.js"
            val file = File(importedDir, fileName)
            file.writeText(raw)
            refreshScripts()
            parseScriptMeta(fileName, file.absolutePath, raw, isImported = true)
        }
    }

    private fun validateLxScriptBody(body: String) {
        val s = body.removePrefix("\uFEFF").trim()
        if (s.isBlank()) throw IllegalStateException("文件内容为空")
        if (s.first() == '<') {
            throw IllegalStateException("内容是网页而不是音源脚本（链接可能已失效），请换用直接的 .js 链接")
        }
        val looksLikeLxScript = s.contains("globalThis.lx") || s.contains("EVENT_NAMES") ||
            s.contains("lx.send") || s.contains("lx.on") || s.contains("lx.request")
        if (!looksLikeLxScript) {
            throw IllegalStateException("内容不是有效的音源脚本（缺少洛雪脚本特征），请检查链接或文件")
        }
    }

    suspend fun deleteScript(assetPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            unload(assetPath)
            if (assetPath in disabledPaths) {
                val cur = userPreferencesRepository.lxMusicDisabledScriptsFlow.first()
                userPreferencesRepository.setLxMusicDisabledScripts(cur - assetPath)
            }
            if (preferredScriptPath == assetPath) {
                preferredScriptPath = null
            }
            val savedActive = userPreferencesRepository.lxMusicActiveScriptFlow.first()
            if (savedActive == assetPath) {
                userPreferencesRepository.setLxMusicActiveScript(null)
            }
            runCatching {
                if (capsCacheFile.exists()) {
                    val json = capsCacheFile.readText(Charsets.UTF_8)
                    val root = JsonParser.parseString(json).asJsonObject
                    root.remove(assetPath)
                    capsCacheFile.writeText(root.toString(), Charsets.UTF_8)
                }
            }
            if (assetPath.startsWith(context.filesDir.absolutePath)) {
                File(assetPath).delete()
            }
            refreshScripts()
            true
        }.getOrDefault(false)
    }

    private companion object {
        val PREFERRED_QUALITY_ORDER = LxQualities.ALL
        const val NETEASE_SONG_DETAIL_BATCH = 200
        const val NETEASE_ANON_COOKIE = "os=pc; appver=2.5.2.197409"

        fun buildKugouExtraFields(hash128k: String, hash320k: String, hashFlac: String, hashFlac24: String): Map<String, String> {
            val extra = mutableMapOf<String, String>()
            if (hash320k.isNotBlank()) extra["hash_320"] = hash320k
            if (hashFlac.isNotBlank()) extra["hash_flac"] = hashFlac
            if (hashFlac24.isNotBlank()) extra["hash_high"] = hashFlac24
            val typesObj = com.google.gson.JsonObject()
            if (hash128k.isNotBlank()) {
                typesObj.add("128k", com.google.gson.JsonObject().apply { addProperty("hash", hash128k) })
            }
            if (hash320k.isNotBlank()) {
                typesObj.add("320k", com.google.gson.JsonObject().apply { addProperty("hash", hash320k) })
            }
            if (hashFlac.isNotBlank()) {
                typesObj.add("flac", com.google.gson.JsonObject().apply { addProperty("hash", hashFlac) })
            }
            if (hashFlac24.isNotBlank()) {
                typesObj.add("flac24bit", com.google.gson.JsonObject().apply { addProperty("hash", hashFlac24) })
            }
            if (typesObj.size() > 0) {
                extra["_types"] = typesObj.toString()
            }
            return extra
        }
    }
}
