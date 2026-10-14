import Foundation

/// Keeps only the latest decoded frame; slow OCR never queues old camera frames.
final class LiveFeed {
    private let queue = DispatchQueue(label: "com.akinwale.platelab.live")
    private var process: Process?
    private var output: Pipe?
    private var token = UUID()
    private var buffer = Data()
    private var latest: Data?
    private var sequence = 0
    private var started = Date()
    private var captured = Date()
    private var failure: String?
    private var ready: ((Result<[String: Any], Error>) -> Void)?

    private func error(_ message: String) -> NSError {
        NSError(domain: "PlateLab.Live", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }
    private func complete(_ result: Result<[String: Any], Error>) {
        let callback = ready; ready = nil
        if let callback { DispatchQueue.main.async { callback(result) } }
    }
    private func stopLocked() {
        token = UUID()
        output?.fileHandleForReading.readabilityHandler = nil
        if let process, process.isRunning { process.terminate() }
        process = nil; output = nil; latest = nil; buffer.removeAll(); sequence = 0
        complete(.failure(error("Camera connection canceled.")))
    }
    func stop() { queue.sync { stopLocked() } }

    func start(url: URL, decoder: URL, completion: @escaping (Result<[String: Any], Error>) -> Void) {
        queue.sync {
            stopLocked()
            ready = completion; failure = nil; started = Date()
            guard ["rtsp", "rtsps", "http", "https"].contains(url.scheme?.lowercased() ?? ""),
                  let host = url.host, !host.isEmpty else {
                complete(.failure(error("Enter a valid RTSP or HTTP camera URL."))); return
            }
            guard FileManager.default.isExecutableFile(atPath: decoder.path) else {
                complete(.failure(error("Live camera support is missing. Rebuild the app with the live-feed dependency."))); return
            }
            let connection = token
            let task = Process(), pipe = Pipe()
            task.executableURL = decoder
            var args = ["-nostdin", "-hide_banner", "-loglevel", "error"]
            if ["rtsp", "rtsps"].contains(url.scheme?.lowercased() ?? "") {
                args += ["-rtsp_transport", "tcp", "-timeout", "15000000"]
            } else { args += ["-rw_timeout", "15000000"] }
            args += ["-i", url.absoluteString, "-map", "0:v:0", "-an", "-sn", "-dn",
                     "-vf", "fps=2,scale=1280:1280:force_original_aspect_ratio=decrease",
                     "-c:v", "mjpeg", "-q:v", "4", "-f", "image2pipe", "pipe:1"]
            task.arguments = args
            task.standardInput = FileHandle.nullDevice
            task.standardOutput = pipe
            // Decoder diagnostics can echo credentials from the input URL.
            task.standardError = FileHandle.nullDevice
            process = task; output = pipe
            pipe.fileHandleForReading.readabilityHandler = { [weak self] handle in
                let data = handle.availableData
                guard !data.isEmpty, let self else { return }
                self.queue.async { self.consume(data, connection: connection) }
            }
            task.terminationHandler = { [weak self] _ in
                guard let self else { return }
                self.queue.async {
                    guard self.token == connection else { return }
                    self.failure = "Camera feed ended or could not be opened. Check the URL, credentials, and network."
                    self.process = nil
                    self.complete(.failure(self.error(self.failure!)))
                }
            }
            do { try task.run() }
            catch {
                complete(.failure(self.error("Could not start the camera decoder. Rebuild live-feed support.")))
                stopLocked()
                return
            }
            queue.asyncAfter(deadline: .now() + 20) { [weak self] in
                guard let self, self.token == connection, self.ready != nil else { return }
                self.complete(.failure(self.error("No camera frame arrived. Check the URL, credentials, and network.")))
                self.stopLocked()
            }
        }
    }

    private func consume(_ data: Data, connection: UUID) {
        guard token == connection else { return }
        buffer.append(data)
        let start = Data([0xff, 0xd8]), end = Data([0xff, 0xd9])
        while let begin = buffer.range(of: start),
              let finish = buffer.range(of: end, in: begin.upperBound..<buffer.endIndex) {
            latest = Data(buffer[begin.lowerBound..<finish.upperBound])
            buffer.removeSubrange(buffer.startIndex..<finish.upperBound)
            sequence += 1; captured = Date()
            complete(.success(snapshot()))
        }
        if buffer.count > 6_000_000 {
            complete(.failure(error("Camera frame is too large or unsupported.")))
            failure = "Camera frame is too large or unsupported."
            stopLocked()
        }
    }
    private func snapshot() -> [String: Any] {
        ["image": "data:image/jpeg;base64," + (latest?.base64EncodedString() ?? ""),
         "sequence": sequence, "time": captured.timeIntervalSince(started),
         "captured_at": ISO8601DateFormatter().string(from: captured)]
    }
    func frame(after previous: Int) -> Result<[String: Any], Error> {
        queue.sync {
            if let failure { return .failure(error(failure)) }
            guard process != nil, latest != nil else { return .failure(error("Camera feed is disconnected.")) }
            // Do not reuse a stalled camera's last frame indefinitely.
            guard Date().timeIntervalSince(captured) < 10 else {
                return .failure(error("Camera feed stopped delivering frames. Reconnect to continue."))
            }
            if sequence <= previous { return .success(["changed": false]) }
            return .success(snapshot().merging(["changed": true]) { _, new in new })
        }
    }
    deinit { output?.fileHandleForReading.readabilityHandler = nil; if let process, process.isRunning { process.terminate() } }
}
