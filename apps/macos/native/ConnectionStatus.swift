import Foundation
import Network

/// The path monitor triggers checks; a successful HTTPS response confirms internet access.
final class ConnectionStatus: NSObject, URLSessionTaskDelegate {
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "PlateLab.connection")
    private var timer: DispatchSourceTimer?
    private var task: URLSessionDataTask?
    private var generation = 0
    private var reachable = false
    private var stopped = false
    private var last: String?
    var changed: ((String) -> Void)?
    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        config.timeoutIntervalForResource = 12
        config.urlCache = nil
        config.httpCookieStorage = nil
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()
    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self = self, !self.stopped else { return }
            self.reachable = path.status == .satisfied
            self.check()
        }
        monitor.start(queue: queue)
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 30, repeating: 30)
        timer.setEventHandler { [weak self] in self?.check() }
        self.timer = timer; timer.resume()
    }
    func refresh() { queue.async { self.last = nil; self.check() } }
    private func publish(_ status: String) {
        guard last != status else { return }; last = status
        DispatchQueue.main.async { [weak self] in self?.changed?(status) }
    }
    private func check() {
        guard !stopped else { return }; generation += 1
        let token = generation; task?.cancel(); task = nil
        guard reachable else { publish("offline"); return }
        // No account, location, plate, or device identifier is sent in this empty request.
        var request = URLRequest(url: URL(string: "https://www.gstatic.com/generate_204")!)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        task = session.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            self.queue.async {
                guard token == self.generation, !self.stopped else { return }
                self.publish(error == nil && (response as? HTTPURLResponse)?.statusCode == 204 ? "online" : "offline")
            }
        }; task?.resume()
    }
    func stop() { queue.async { self.stopped = true; self.generation += 1; self.monitor.cancel(); self.timer?.cancel(); self.task?.cancel(); self.session.invalidateAndCancel() } }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
