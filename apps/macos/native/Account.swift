import Cocoa
import AuthenticationServices
import CryptoKit
import Security

// Public OAuth client configuration. No client secret or administrator credential is used.
struct AccountConfig {
    let clientID: String
    let apiKey: String
    let scheme: String
    init?(dictionary: [String: Any], bundleID: String) {
        guard dictionary["BUNDLE_ID"] as? String == bundleID,
              let project = dictionary["PROJECT_ID"] as? String, !project.isEmpty,
              let client = dictionary["CLIENT_ID"] as? String, client.hasSuffix(".apps.googleusercontent.com"),
              let key = dictionary["API_KEY"] as? String, key.range(of: "^[A-Za-z0-9_-]{20,100}$", options: .regularExpression) != nil else { return nil }
        clientID = client; apiKey = key
        scheme = client.split(separator: ".").reversed().joined(separator: ".")
    }
    var redirect: String { scheme + ":/oauthredirect" }
}
struct OAuthProof {
    let verifier: String
    let state: String
    let nonce: String
    static func encode(_ data: Data) -> String { data.base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "") }
    static func random() throws -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        guard SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess else { throw AccountFailure.security }
        return encode(Data(bytes))
    }
    init() throws { verifier = try Self.random(); state = try Self.random(); nonce = try Self.random() }
    init(verifier: String, state: String, nonce: String) { self.verifier = verifier; self.state = state; self.nonce = nonce }
    var challenge: String { Self.encode(Data(SHA256.hash(data: Data(verifier.utf8)))) }
    func code(_ url: URL, config: AccountConfig) throws -> String {
        guard let parts = URLComponents(url: url, resolvingAgainstBaseURL: false), parts.scheme == config.scheme,
              parts.host == nil, parts.path == "/oauthredirect", parts.fragment == nil,
              parts.user == nil, parts.password == nil else { throw AccountFailure.security }
        let items = parts.queryItems ?? []
        guard items.filter({ $0.name == "state" }).count == 1,
              items.first(where: { $0.name == "state" })?.value == state,
              items.filter({ $0.name == "code" }).count <= 1,
              items.filter({ $0.name == "error" }).count <= 1 else { throw AccountFailure.security }
        if items.contains(where: { $0.name == "error" }) { throw AccountFailure.canceled }
        guard let code = items.first(where: { $0.name == "code" })?.value, !code.isEmpty, code.count < 8192 else { throw AccountFailure.security }
        return code
    }
    // This check binds the response to the current request. Firebase still verifies the signature.
    func acceptsNonce(_ token: String) -> Bool {
        let pieces = token.split(separator: ".", omittingEmptySubsequences: false)
        guard pieces.count == 3, pieces[1].count < 65536 else { return false }
        var encoded = String(pieces[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let data = Data(base64Encoded: encoded), let claims = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return false }
        return claims["nonce"] as? String == nonce
    }
}
enum AccountFailure: LocalizedError {
    case security, network, canceled, recentLogin
    var errorDescription: String? {
        switch self {
        case .security: return "The sign-in response could not be verified. Please try again."
        case .network: return "Could not reach Google or Firebase. Check your connection and try again. Guest mode is still available."
        case .canceled: return "Sign-in canceled. You can continue as a guest."
        case .recentLogin: return "For your security, sign out and sign in again before deleting your account."
        }
    }
}
/// Tokens remain in native memory only. Nothing is stored in the page, logs, reports, or preferences.
final class AccountService: NSObject, ASWebAuthenticationPresentationContextProviding, URLSessionTaskDelegate {
    private let config: AccountConfig?
    private var browser: ASWebAuthenticationSession?
    private var task: URLSessionDataTask?
    private var expiryTimer: Timer?
    private var timeout: Timer?
    private var generation = 0
    private var idToken: String?
    private var profile: [String: Any] = [:]
    private var expires = Date.distantPast
    private(set) var busy = false
    weak var window: NSWindow?
    var changed: (([String: Any]) -> Void)?
    private lazy var session: URLSession = {
        let settings = URLSessionConfiguration.ephemeral
        settings.timeoutIntervalForRequest = 20; settings.timeoutIntervalForResource = 30
        settings.urlCache = nil; settings.httpCookieStorage = nil
        return URLSession(configuration: settings, delegate: self, delegateQueue: nil)
    }()
    override init() {
        if let url = Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist"),
           let data = try? Data(contentsOf: url), let dict = try? PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any] {
            config = AccountConfig(dictionary: dict, bundleID: Bundle.main.bundleIdentifier ?? "")
        } else { config = nil }
        super.init()
    }
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor { window! }
    func snapshot() -> [String: Any] {
        if idToken != nil && expires <= Date() { clear() }
        return ["configured": config != nil, "signedIn": idToken != nil, "busy": busy,
                "name": profile["name"] ?? "", "email": profile["email"] ?? ""]
    }
    private func update(_ message: String? = nil) {
        var payload = snapshot(); if let message = message { payload["message"] = message }; changed?(payload)
    }
    private func clear() {
        generation += 1; browser?.cancel(); browser = nil; task?.cancel(); task = nil
        timeout?.invalidate(); timeout = nil; expiryTimer?.invalidate(); expiryTimer = nil
        idToken = nil; profile = [:]; expires = .distantPast; busy = false
    }
    func signOut() { clear(); update("Signed out. Local readings remain in this workspace.") }
    func stop() { clear(); session.invalidateAndCancel() }
    private func finish(_ error: Error) { browser = nil; task = nil; timeout?.invalidate(); timeout = nil; busy = false; update(error.localizedDescription) }
    func signIn() {
        guard !busy, idToken == nil, let config = config, window != nil else { update(); return }
        do {
            let proof = try OAuthProof(); generation += 1; let attempt = generation
            var components = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
            components.queryItems = ["client_id": config.clientID, "redirect_uri": config.redirect, "response_type": "code", "scope": "openid email profile", "state": proof.state, "nonce": proof.nonce, "code_challenge": proof.challenge, "code_challenge_method": "S256", "prompt": "select_account"].map { URLQueryItem(name: $0.key, value: $0.value) }
            busy = true; update("Choose your Google account in the secure browser window…")
            let browser = ASWebAuthenticationSession(url: components.url!, callbackURLScheme: config.scheme) { [weak self] url, error in
                DispatchQueue.main.async {
                    guard let self = self, attempt == self.generation else { return }
                    self.browser = nil
                    guard error == nil, let url = url else { self.finish(AccountFailure.canceled); return }
                    do { self.exchange(code: try proof.code(url, config: config), proof: proof, attempt: attempt, config: config) }
                    catch { self.finish(error) }
                }
            }
            browser.presentationContextProvider = self; browser.prefersEphemeralWebBrowserSession = true
            self.browser = browser
            timeout = Timer.scheduledTimer(withTimeInterval: 300, repeats: false) { [weak self] _ in
                guard let self = self, attempt == self.generation else { return }
                self.clear(); self.update("Sign-in timed out. Please try again, or continue as a guest.")
            }
            if !browser.start() { finish(AccountFailure.network) }
        } catch { finish(error) }
    }
    private static func form(_ values: [String: String]) -> String {
        let allowed = CharacterSet(charactersIn: "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~")
        return values.sorted { $0.key < $1.key }.map { $0.key.addingPercentEncoding(withAllowedCharacters: allowed)! + "=" + $0.value.addingPercentEncoding(withAllowedCharacters: allowed)! }.joined(separator: "&")
    }
    private func request(_ url: URL, body: Data, type: String, attempt: Int, completion: @escaping (Result<[String: Any], Error>) -> Void) {
        var request = URLRequest(url: url); request.httpMethod = "POST"; request.httpBody = body
        request.setValue(type, forHTTPHeaderField: "Content-Type"); request.cachePolicy = .reloadIgnoringLocalCacheData
        task = session.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self, attempt == self.generation else { return }
                guard error == nil, let data = data, data.count <= 1_048_576,
                      let response = response as? HTTPURLResponse,
                      let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { completion(.failure(AccountFailure.network)); return }
                guard response.statusCode == 200 else {
                    let message = (object["error"] as? [String: Any])?["message"] as? String
                    completion(.failure(message == "CREDENTIAL_TOO_OLD_LOGIN_AGAIN" ? AccountFailure.recentLogin : AccountFailure.network)); return
                }
                completion(.success(object))
            }
        }; task?.resume()
    }
    private func exchange(code: String, proof: OAuthProof, attempt: Int, config: AccountConfig) {
        let fields = ["client_id": config.clientID, "redirect_uri": config.redirect, "code": code, "code_verifier": proof.verifier, "grant_type": "authorization_code"]
        request(URL(string: "https://oauth2.googleapis.com/token")!, body: Data(Self.form(fields).utf8), type: "application/x-www-form-urlencoded", attempt: attempt) { [weak self] result in
            guard let self = self else { return }
            do {
                let google = try result.get()
                guard let token = google["id_token"] as? String, proof.acceptsNonce(token) else { throw AccountFailure.security }
                let body: [String: Any] = ["postBody": Self.form(["id_token": token, "providerId": "google.com"]), "requestUri": "http://localhost", "returnSecureToken": true]
                self.request(self.endpoint("signInWithIdp", config), body: try JSONSerialization.data(withJSONObject: body), type: "application/json", attempt: attempt) { [weak self] result in
                    guard let self = self else { return }
                    do {
                        let account = try result.get()
                        guard account["needConfirmation"] as? Bool != true, account["providerId"] as? String == "google.com",
                              let id = account["localId"] as? String, !id.isEmpty,
                              let verified = account["idToken"] as? String, !verified.isEmpty,
                              let seconds = Double(account["expiresIn"] as? String ?? ""), seconds > 60 else { throw AccountFailure.security }
                        self.idToken = verified
                        self.expires = Date().addingTimeInterval(min(seconds - 30, 3600))
                        self.profile = ["name": String((account["displayName"] as? String ?? "Signed in").prefix(100)), "email": String((account["email"] as? String ?? "").prefix(254))]
                        self.busy = false; self.timeout?.invalidate(); self.timeout = nil
                        self.expiryTimer = Timer.scheduledTimer(withTimeInterval: self.expires.timeIntervalSinceNow, repeats: false) { [weak self] _ in self?.clear(); self?.update("Your session ended. Sign in again when you need your account.") }
                        self.update("Signed in for this session. Cloud backup is off.")
                    } catch { self.finish(error) }
                }
            } catch { self.finish(error) }
        }
    }
    private func endpoint(_ action: String, _ config: AccountConfig) -> URL {
        var url = URLComponents(string: "https://identitytoolkit.googleapis.com/v1/accounts:" + action)!
        url.queryItems = [URLQueryItem(name: "key", value: config.apiKey)]; return url.url!
    }
    func confirmDelete() {
        guard !busy, idToken != nil, let window = window else { return }
        let alert = NSAlert(); alert.messageText = "Delete your PlateLab account?"
        alert.informativeText = "This permanently deletes your PlateLab sign-in account. Your Google account and local readings remain unchanged."
        alert.addButton(withTitle: "Cancel"); alert.addButton(withTitle: "Delete account")
        alert.beginSheetModal(for: window) { [weak self] response in if response == .alertSecondButtonReturn { self?.deleteAccount() } }
    }
    private func deleteAccount() {
        _ = snapshot()
        guard !busy, let token = idToken, let config = config else { update(); return }
        busy = true; update("Deleting your PlateLab account…"); let attempt = generation
        let body = try! JSONSerialization.data(withJSONObject: ["idToken": token])
        request(endpoint("delete", config), body: body, type: "application/json", attempt: attempt) { [weak self] result in
            guard let self = self else { return }
            switch result { case .success: self.clear(); self.update("PlateLab account deleted. Local readings remain in this workspace.")
            case .failure(let error): self.finish(error) }
        }
    }
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
