package com.enterprise.iptv.domain.cast

import kotlinx.coroutines.*
import java.io.*
import java.net.*

/**
 * خادم بث محلي مدمج وخفيف الوزن (Local Media HTTP Server)
 * يتيح بث الفيديوهات والتسجيلات المخزنة محلياً في ذاكرة الهاتف إلى الشاشات الذكية (DLNA / UPnP)
 * عبر شبكة الـ Wi-Fi المنزلية دون استهلاك باقة الإنترنت.
 * يدعم بروتوكول HTTP 206 Partial Content (Range Requests) لتمكين التلفاز من التقديم والترجيع بسلاسة.
 */
class LocalMediaHttpServer {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activeMediaFile: File? = null
    private var serverPort: Int = 8899

    val isRunning: Boolean
        get() = serverSocket != null && !serverSocket!!.isClosed

    /**
     * بدء تشغيل الخادم المحلي لملف محدد
     * يعيد الرابط الكامل للبث على الشبكة المحلية، مثل: http://192.168.1.5:8899/media.ts
     */
    fun startServing(file: File): String? {
        stop()
        if (!file.exists() || !file.canRead()) return null

        activeMediaFile = file
        val localIp = getLocalWifiIpAddress() ?: return null

        try {
            serverSocket = ServerSocket(0).apply {
                reuseAddress = true
                soTimeout = 0
            }
            serverPort = serverSocket!!.localPort
        } catch (e: Exception) {
            try {
                serverSocket = ServerSocket(serverPort)
            } catch (ex: Exception) {
                return null
            }
        }

        serverJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive && serverSocket != null && !serverSocket!!.isClosed) {
                try {
                    val clientSocket = serverSocket!!.accept()
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    // Ignore transient network errors
                }
            }
        }

        val encodedName = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        return "http://$localIp:$serverPort/$encodedName"
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val input = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = input.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val method = parts[0]
                var rangeHeader: String? = null

                var headerLine = input.readLine()
                while (!headerLine.isNullOrBlank()) {
                    if (headerLine.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = headerLine.substringAfter(":").trim()
                    }
                    headerLine = input.readLine()
                }

                val file = activeMediaFile ?: return
                if (!file.exists()) {
                    send404(output)
                    return
                }

                val fileLength = file.length()
                val contentType = if (file.name.endsWith(".ts", ignoreCase = true)) "video/mp2t" else "video/mp4"

                if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                    handleRangeRequest(output, file, fileLength, contentType, rangeHeader, method == "HEAD")
                } else {
                    handleFullRequest(output, file, fileLength, contentType, method == "HEAD")
                }
            } catch (e: Exception) {
                // Client disconnected or closed stream
            }
        }
    }

    private fun handleFullRequest(
        output: BufferedOutputStream,
        file: File,
        fileLength: Long,
        contentType: String,
        isHead: Boolean
    ) {
        val writer = PrintWriter(output, false)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: $fileLength\r\n")
        writer.print("Accept-Ranges: bytes\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()

        if (!isHead) {
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
    }

    private fun handleRangeRequest(
        output: BufferedOutputStream,
        file: File,
        fileLength: Long,
        contentType: String,
        rangeHeader: String,
        isHead: Boolean
    ) {
        val rangeValue = rangeHeader.removePrefix("bytes=").trim()
        val rangeParts = rangeValue.split("-")
        var start: Long = 0
        var end: Long = fileLength - 1

        try {
            if (rangeParts[0].isNotBlank()) {
                start = rangeParts[0].toLong()
            }
            if (rangeParts.size > 1 && rangeParts[1].isNotBlank()) {
                end = rangeParts[1].toLong()
            }
        } catch (e: NumberFormatException) {
            start = 0
            end = fileLength - 1
        }

        if (start >= fileLength) {
            val writer = PrintWriter(output, false)
            writer.print("HTTP/1.1 416 Range Not Satisfiable\r\n")
            writer.print("Content-Range: bytes */$fileLength\r\n\r\n")
            writer.flush()
            return
        }

        if (end >= fileLength) {
            end = fileLength - 1
        }

        val contentLength = end - start + 1

        val writer = PrintWriter(output, false)
        writer.print("HTTP/1.1 206 Partial Content\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: $contentLength\r\n")
        writer.print("Content-Range: bytes $start-$end/$fileLength\r\n")
        writer.print("Accept-Ranges: bytes\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()

        if (!isHead) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val bytesRead = raf.read(buffer, 0, toRead)
                    if (bytesRead == -1) break
                    output.write(buffer, 0, bytesRead)
                    remaining -= bytesRead
                }
                output.flush()
            }
        }
    }

    private fun send404(output: BufferedOutputStream) {
        val writer = PrintWriter(output, false)
        writer.print("HTTP/1.1 404 Not Found\r\n")
        writer.print("Content-Length: 0\r\n\r\n")
        writer.flush()
    }

    fun stop() {
        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore socket close exception
        }
        serverSocket = null
        activeMediaFile = null
    }

    /**
     * استخراج عنوان الـ IP المحلي للهاتف المتصل بالـ Wi-Fi
     */
    private fun getLocalWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = intf.inetAddresses ?: continue
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }
        return null
    }
}
