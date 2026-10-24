package com.enterprise.iptv.domain.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.StringReader
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير البث للشاشات والتلفزيونات الذكية (DLNA / UPnP / Cast Manager)
 * يكتشف الشاشات المتصلة على نفس شبكة الـ Wi-Fi المنزلية عبر SSDP و NsdManager
 * ويتحكم في البث التلفزيوني عبر بروتوكولات UPnP AVTransport القياسية.
 */
@Singleton
class DlnaCastManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val localHttpServer = LocalMediaHttpServer()

    private val _discoveredDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _activeCastDevice = MutableStateFlow<CastDevice?>(null)
    val activeCastDevice = _activeCastDevice.asStateFlow()

    private val _castState = MutableStateFlow<CastState>(CastState.Disconnected)
    val castState = _castState.asStateFlow()

    private var scanJob: Job? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    init {
        acquireMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("enterprise_iptv_ssdp_lock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            // Ignore if permission not granted
        }
    }

    /**
     * بدء البحث السريع عن الشاشات وأجهزة البث المتصلة بشبكة الـ Wi-Fi
     */
    fun startDiscovery(timeoutMs: Long = 6000L) {
        if (_isScanning.value) return
        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        scanJob?.cancel()
        scanJob = scope.launch {
            val foundDevices = mutableMapOf<String, CastDevice>()

            // 1. بدء فحص بروتوكول SSDP / UPnP المخصص للشاشات الذكية المدعومة فعلياً (Samsung, LG, Sony, Roku, Philips, etc.)
            val ssdpJob = launch {
                discoverSsdpDevices { device ->
                    if (device.avTransportControlUrl.isNotBlank()) {
                        foundDevices[device.id] = device
                        _discoveredDevices.value = foundDevices.values.toList()
                    }
                }
            }

            delay(timeoutMs)
            ssdpJob.cancel()
            _isScanning.value = false
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        stopNsdDiscovery()
        _isScanning.value = false
    }

    /**
     * إرسال واستقبال حزم SSDP M-SEARCH لاكتشاف مشغلات الميديا UPnP / DLNA
     */
    private suspend fun discoverSsdpDevices(onDeviceFound: (CastDevice) -> Unit) = withContext(Dispatchers.IO) {
        val targets = listOf(
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1"
        )

        val multicastGroup = InetAddress.getByName("239.255.255.250")
        val port = 1900

        for (target in targets) {
            if (!isActive) break
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    soTimeout = 2500
                    reuseAddress = true
                }

                val query = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 3\r\n" +
                        "ST: $target\r\n\r\n"

                val sendData = query.toByteArray()
                val packet = DatagramPacket(sendData, sendData.size, multicastGroup, port)
                socket.send(packet)

                val buffer = ByteArray(4096)
                val startTime = System.currentTimeMillis()

                while (isActive && System.currentTimeMillis() - startTime < 3000) {
                    try {
                        val receivePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)

                        val location = extractHeader(response, "LOCATION")
                        if (!location.isNullOrBlank()) {
                            launch {
                                parseDeviceDescription(location, onDeviceFound)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        break
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                // Ignore network exceptions
            } finally {
                socket?.close()
            }
        }
    }

    /**
     * استخراج بيانات الشاشة من ملف XML الخاص بالـ Device Description
     */
    private suspend fun parseDeviceDescription(locationUrl: String, onDeviceFound: (CastDevice) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val url = URL(locationUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }

            if (connection.responseCode != 200) return@withContext
            val xml = connection.inputStream.bufferedReader().use { it.readText() }

            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentTag = ""
            var friendlyName = ""
            var manufacturer = ""
            var modelName = ""
            var udn = ""
            var inAvTransportService = false
            var avTransportControlUrl = ""
            var inRenderingControlService = false
            var renderingControlUrl = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        if (currentTag.equals("service", ignoreCase = true)) {
                            inAvTransportService = false
                            inRenderingControlService = false
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "friendlyName" -> if (friendlyName.isEmpty()) friendlyName = text
                                "manufacturer" -> if (manufacturer.isEmpty()) manufacturer = text
                                "modelName" -> if (modelName.isEmpty()) modelName = text
                                "UDN" -> if (udn.isEmpty()) udn = text
                                "serviceType" -> {
                                    if (text.contains("AVTransport", ignoreCase = true)) {
                                        inAvTransportService = true
                                    } else if (text.contains("RenderingControl", ignoreCase = true)) {
                                        inRenderingControlService = true
                                    }
                                }
                                "controlURL" -> {
                                    if (inAvTransportService && avTransportControlUrl.isEmpty()) {
                                        avTransportControlUrl = text
                                    } else if (inRenderingControlService && renderingControlUrl.isEmpty()) {
                                        renderingControlUrl = text
                                    }
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (friendlyName.isNotBlank() && avTransportControlUrl.isNotBlank()) {
                val fullAvControlUrl = resolveUrl(url, avTransportControlUrl)
                val fullRenderingUrl = if (renderingControlUrl.isNotBlank()) resolveUrl(url, renderingControlUrl) else ""
                val id = if (udn.isNotBlank()) udn else "${url.host}:${url.port}"

                val deviceType = when {
                    manufacturer.contains("Samsung", ignoreCase = true) -> CastDeviceType.DLNA_SMART_TV
                    manufacturer.contains("LG", ignoreCase = true) -> CastDeviceType.DLNA_SMART_TV
                    manufacturer.contains("Sony", ignoreCase = true) -> CastDeviceType.DLNA_SMART_TV
                    modelName.contains("Roku", ignoreCase = true) -> CastDeviceType.ROKU
                    else -> CastDeviceType.DLNA_SMART_TV
                }

                val device = CastDevice(
                    id = id,
                    name = friendlyName,
                    host = url.host,
                    port = url.port,
                    location = locationUrl,
                    avTransportControlUrl = fullAvControlUrl,
                    renderingControlUrl = fullRenderingUrl,
                    manufacturer = manufacturer,
                    modelName = modelName,
                    deviceType = deviceType
                )
                onDeviceFound(device)
            }
        } catch (e: Exception) {
            // Ignore malformed XML or timeout
        }
    }

    private fun resolveUrl(baseUrl: URL, path: String): String {
        return if (path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)) {
            path
        } else if (path.startsWith("/")) {
            "${baseUrl.protocol}://${baseUrl.host}:${baseUrl.port}$path"
        } else {
            val basePath = baseUrl.path.substringBeforeLast("/", "")
            "${baseUrl.protocol}://${baseUrl.host}:${baseUrl.port}$basePath/$path"
        }
    }

    private fun extractHeader(response: String, headerName: String): String? {
        val lines = response.split("\r\n")
        for (line in lines) {
            if (line.startsWith("$headerName:", ignoreCase = true)) {
                return line.substringAfter(":").trim()
            }
        }
        return null
    }

    /**
     * اكتشاف أجهزة Google Cast / Chromecast عبر NsdManager
     */
    private fun discoverChromecastDevices(onDeviceFound: (CastDevice) -> Unit) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        stopNsdDiscovery()

        nsdDiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("_googlecast._tcp")) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress ?: return
                            val port = serviceInfo.port
                            val name = serviceInfo.serviceName.replace("Google-Cast-", "")
                            val device = CastDevice(
                                id = "chromecast_${serviceInfo.serviceName}",
                                name = if (name.isNotBlank()) name else "Chromecast TV",
                                host = host,
                                port = port,
                                deviceType = CastDeviceType.CHROMECAST
                            )
                            onDeviceFound(device)
                        }
                    })
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, nsdDiscoveryListener)
        } catch (e: Exception) {
            // NsdManager error
        }
    }

    private fun stopNsdDiscovery() {
        val listener = nsdDiscoveryListener ?: return
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            // Ignore
        }
        nsdDiscoveryListener = null
    }

    /**
     * بث مقطع فيديو مسجل من مكتبة التسجيلات إلى الشاشة المحددة عبر الخادم المحلي
     */
    fun castRecording(file: File, device: CastDevice) {
        scope.launch {
            _activeCastDevice.value = device
            _castState.value = CastState.Connecting(device)

            val localStreamUrl = localHttpServer.startServing(file)
            if (localStreamUrl == null) {
                _castState.value = CastState.Error("تعذر تشغيل خادم البث المحلي على شبكة Wi-Fi")
                return@launch
            }

            val success = sendSetAvTransportUri(device, localStreamUrl, file.name)
            if (success) {
                val playSuccess = sendPlayCommand(device)
                if (playSuccess) {
                    _castState.value = CastState.Playing(device, file.name)
                } else {
                    _castState.value = CastState.Connected(device, file.name)
                }
            } else {
                _castState.value = CastState.Error("تعذر إرسال أمر البث للشاشة ${device.name}")
            }
        }
    }

    /**
     * بث قناة تلفزيونية حية إلى الشاشة المحددة عبر رابط البث المباشر
     */
    fun castLiveStream(streamUrl: String, title: String, device: CastDevice) {
        scope.launch {
            _activeCastDevice.value = device
            _castState.value = CastState.Connecting(device)

            val success = sendSetAvTransportUri(device, streamUrl, title)
            if (success) {
                val playSuccess = sendPlayCommand(device)
                if (playSuccess) {
                    _castState.value = CastState.Playing(device, title)
                } else {
                    _castState.value = CastState.Connected(device, title)
                }
            } else {
                _castState.value = CastState.Error("تعذر إرسال رابط البث للشاشة ${device.name}")
            }
        }
    }

    fun play() {
        val device = _activeCastDevice.value ?: return
        val currentMedia = when (val s = _castState.value) {
            is CastState.Paused -> s.mediaTitle
            is CastState.Connected -> s.mediaTitle
            is CastState.Playing -> s.mediaTitle
            else -> "Media"
        }
        scope.launch {
            if (sendPlayCommand(device)) {
                _castState.value = CastState.Playing(device, currentMedia)
            }
        }
    }

    fun pause() {
        val device = _activeCastDevice.value ?: return
        val currentMedia = when (val s = _castState.value) {
            is CastState.Playing -> s.mediaTitle
            else -> "Media"
        }
        scope.launch {
            if (sendPauseCommand(device)) {
                _castState.value = CastState.Paused(device, currentMedia)
            }
        }
    }

    fun stop() {
        val device = _activeCastDevice.value
        scope.launch {
            if (device != null) {
                sendStopCommand(device)
            }
            localHttpServer.stop()
            _activeCastDevice.value = null
            _castState.value = CastState.Disconnected
        }
    }

    // --- أوامر UPnP AVTransport SOAP القياسية ---

    private suspend fun sendSetAvTransportUri(device: CastDevice, uri: String, title: String): Boolean = withContext(Dispatchers.IO) {
        if (device.avTransportControlUrl.isBlank()) return@withContext false

        val didlLite = """&lt;DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"&gt;&lt;item id="1" parentID="0" restricted="1"&gt;&lt;dc:title&gt;$title&lt;/dc:title&gt;&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;&lt;res protocolInfo="http-get:*:video/*:*"&gt;$uri&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;"""

        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <CurrentURI>$uri</CurrentURI>
                        <CurrentURIMetaData>$didlLite</CurrentURIMetaData>
                    </u:SetAVTransportURI>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        postSoapRequest(device.avTransportControlUrl, "SetAVTransportURI", body)
    }

    private suspend fun sendPlayCommand(device: CastDevice): Boolean = withContext(Dispatchers.IO) {
        if (device.avTransportControlUrl.isBlank()) return@withContext false
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                        <Speed>1</Speed>
                    </u:Play>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        postSoapRequest(device.avTransportControlUrl, "Play", body)
    }

    private suspend fun sendPauseCommand(device: CastDevice): Boolean = withContext(Dispatchers.IO) {
        if (device.avTransportControlUrl.isBlank()) return@withContext false
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Pause xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Pause>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        postSoapRequest(device.avTransportControlUrl, "Pause", body)
    }

    private suspend fun sendStopCommand(device: CastDevice): Boolean = withContext(Dispatchers.IO) {
        if (device.avTransportControlUrl.isBlank()) return@withContext false
        val body = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                <s:Body>
                    <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                        <InstanceID>0</InstanceID>
                    </u:Stop>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        postSoapRequest(device.avTransportControlUrl, "Stop", body)
    }

    private fun postSoapRequest(controlUrl: String, action: String, soapBody: String): Boolean {
        return try {
            val url = URL(controlUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:AVTransport:1#$action\"")
            }

            connection.outputStream.use { os ->
                os.write(soapBody.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = connection.responseCode
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }
}
