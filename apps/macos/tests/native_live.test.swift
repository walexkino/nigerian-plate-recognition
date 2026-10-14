import Foundation

@main struct LiveFeedSmokeTest {
    static func main() {
        guard CommandLine.arguments.count == 3 else {
            fputs("Usage: live-feed-test DECODER RTSP_URL\n", stderr); exit(1)
        }
        let feed = LiveFeed()
        var finished = false, passed = false
        feed.start(url: URL(string: CommandLine.arguments[2])!, decoder: URL(fileURLWithPath: CommandLine.arguments[1])) { result in
            do {
                let first = try result.get()
                let firstSequence = first["sequence"] as! Int
                guard let image = first["image"] as? String,
                      let data = Data(base64Encoded: String(image.split(separator: ",", maxSplits: 1)[1])) else {
                    throw NSError(domain: "test", code: 1)
                }
                let observations = try recognize(data)
                let texts = observations.flatMap { ($0["alternatives"] as? [[String: Any]] ?? []).compactMap { $0["text"] as? String } }
                let readings = texts.map { $0.uppercased().filter { $0.isASCII && $0.isLetter || $0.isNumber } }
                guard readings.contains("ABC123DE") else {
                    throw NSError(domain: "test", code: 2, userInfo: [NSLocalizedDescriptionKey: "Synthetic plate was not recognized"])
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                    do {
                        let next = try feed.frame(after: firstSequence).get()
                        guard next["changed"] as? Bool == true, (next["sequence"] as? Int ?? 0) > firstSequence,
                              next["captured_at"] as? String != nil else { throw NSError(domain: "test", code: 3) }
                        feed.stop()
                        if case .success = feed.frame(after: 0) { throw NSError(domain: "test", code: 4) }
                        print("PASS: RTSP connection, successive bounded frames, Apple Vision plate recognition, and disconnect")
                        passed = true
                    } catch { fputs("Live-feed test failed: \(error.localizedDescription)\n", stderr) }
                    finished = true
                }
            } catch { fputs("Live-feed test failed: \(error.localizedDescription)\n", stderr); finished = true }
        }
        let deadline = Date().addingTimeInterval(35)
        while !finished && Date() < deadline { RunLoop.main.run(until: Date().addingTimeInterval(0.05)) }
        feed.stop()
        exit(passed ? 0 : 1)
    }
}
