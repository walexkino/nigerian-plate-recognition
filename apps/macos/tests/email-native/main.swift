import Darwin
import Foundation

// Test-only transport: production requests remain fixed HTTPS URLs. Synthetic local accounts only.
final class AuthEmulatorProtocol: URLProtocol {
    var forwardedTask: URLSessionDataTask?
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "identitytoolkit.googleapis.com" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let original = request.url!
        precondition(original.scheme == "https")
        var parts = URLComponents(url: original,resolvingAgainstBaseURL:false)!
        parts.scheme = "http";parts.host = "127.0.0.1";parts.port = 9099;parts.path = "/identitytoolkit.googleapis.com" + parts.path
        var forwarded = request;forwarded.url = parts.url
        if forwarded.httpBody == nil, let stream = request.httpBodyStream {
            stream.open();defer { stream.close() };var bytes = Data();var buffer = [UInt8](repeating:0,count:4096)
            while stream.hasBytesAvailable { let count = stream.read(&buffer,maxLength:buffer.count);if count <= 0 { break };bytes.append(buffer,count:count) };forwarded.httpBody = bytes
        }
        forwardedTask = URLSession.shared.dataTask(with:forwarded) { [weak self] data,response,error in
            guard let self = self else { return }
            if let error = error { self.client?.urlProtocol(self,didFailWithError:error);return }
            let status = (response as! HTTPURLResponse).statusCode
            self.client?.urlProtocol(self,didReceive:HTTPURLResponse(url:original,statusCode:status,httpVersion:nil,headerFields:["Content-Type":"application/json"])!,cacheStoragePolicy:.notAllowed)
            if let data = data { self.client?.urlProtocol(self,didLoad:data) };self.client?.urlProtocolDidFinishLoading(self)
        };forwardedTask?.resume()
    }
    override func stopLoading() { forwardedTask?.cancel() }
}
func wait(_ reason: String, _ condition: () -> Bool) {
    let end = Date().addingTimeInterval(15)
    while Date() < end { if condition() { return };RunLoop.main.run(until:Date().addingTimeInterval(0.02)) }
    fatalError("Timed out: " + reason)
}
func local(_ path: String,_ body: [String:Any]? = nil) -> [String:Any] {
    var request = URLRequest(url:URL(string:"http://127.0.0.1:9099/"+path)!)
    if let body = body { request.httpMethod="POST";request.setValue("application/json",forHTTPHeaderField:"Content-Type");request.httpBody=try! JSONSerialization.data(withJSONObject:body) }
    var result: [String:Any]?,errorText: String?
    URLSession.shared.dataTask(with:request) { data,response,error in
        DispatchQueue.main.async { if error != nil { errorText="Local emulator request failed";return };result=(try? JSONSerialization.jsonObject(with:data!)) as? [String:Any] }
    }.resume()
    wait("local REST response") { result != nil || errorText != nil };precondition(errorText == nil);return result!
}
func code(_ email: String,_ type: String) -> String {
    let response = local("emulator/v1/projects/demo-platelab/oobCodes")
    let codes = response["oobCodes"] as? [[String:Any]] ?? []
    guard let result = codes.last(where: { $0["email"] as? String == email && $0["requestType"] as? String == type }), let code = result["oobCode"] as? String else { fatalError("Missing local email action") };return code
}
setbuf(stdout,nil)
let config = AccountConfig(dictionary:["BUNDLE_ID":"test.app","PROJECT_ID":"demo-platelab","CLIENT_ID":"123-test.apps.googleusercontent.com","API_KEY":String(repeating:"x",count:39)],bundleID:"test.app")!
let transport = URLSessionConfiguration.ephemeral;transport.protocolClasses=[AuthEmulatorProtocol.self]
let service = AccountService(configuration:config,transport:transport)
let email = "mac-"+UUID().uuidString.lowercased()+"@example.test"
let secret = "Synthetic-"+UUID().uuidString
var output: [[String:Any]]=[];service.changed = { output.append($0) }
func complete() { wait("account request completed") { !service.busy } }
service.submitEmail(.create,AccountCredentials(name:"Synthetic Tester",email:email,password:secret));complete()
precondition(service.snapshot()["signedIn"] as? Bool == true)
precondition(service.snapshot()["hasPassword"] as? Bool == true)
precondition(service.snapshot()["emailVerified"] as? Bool == false)
precondition(service.snapshot()["name"] as? String == "Synthetic Tester")
let verification = code(email,"VERIFY_EMAIL")
let verified = local("identitytoolkit.googleapis.com/v1/accounts:update?key=fixture",["oobCode":verification]);precondition(verified["error"] == nil)
service.refreshProfile();complete();precondition(service.snapshot()["emailVerified"] as? Bool == true)
service.signOut();precondition(service.snapshot()["signedIn"] as? Bool == false)
service.submitEmail(.signIn,AccountCredentials(name:"",email:email,password:"wrong password fixture"));complete();precondition(service.snapshot()["signedIn"] as? Bool == false)
service.submitEmail(.signIn,AccountCredentials(name:"",email:email,password:secret));complete();precondition(service.snapshot()["signedIn"] as? Bool == true)
service.signOut();service.submitEmail(.reset,AccountCredentials(name:"",email:email,password:""));complete()
let reset = code(email,"PASSWORD_RESET"),newSecret = "Synthetic-"+UUID().uuidString
let resetResult = local("identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=fixture",["oobCode":reset,"newPassword":newSecret]);precondition(resetResult["error"] == nil)
service.submitEmail(.signIn,AccountCredentials(name:"",email:email,password:newSecret));complete();precondition(service.snapshot()["signedIn"] as? Bool == true)
service.signOut();service.submitEmail(.reset,AccountCredentials(name:"",email:"missing-"+UUID().uuidString+"@example.test",password:""));complete();precondition((output.last?["message"] as? String)?.hasPrefix("If this address") == true)
// Linking a password must retain the existing Google account UID, not create a second account.
let googleEmail = "google-"+UUID().uuidString+"@example.test",subject=UUID().uuidString
let header=OAuthProof.encode(Data("{\"alg\":\"none\",\"typ\":\"JWT\"}".utf8))
let claims=OAuthProof.encode(try! JSONSerialization.data(withJSONObject:["sub":subject,"email":googleEmail,"email_verified":true,"name":"Synthetic Google Tester"]))
let google=local("identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=fixture",["postBody":"id_token="+header+"."+claims+".&providerId=google.com","requestUri":"http://localhost","returnSecureToken":true])
precondition(google["idToken"] != nil)
let deniedLink=local("identitytoolkit.googleapis.com/v1/accounts:signUp?key=fixture",["idToken":google["idToken"]!,"email":email,"password":secret,"returnSecureToken":true]);precondition(deniedLink["error"] != nil)
try service.accept(google,passwordProvider:false)
let linkedSecret="Synthetic-"+UUID().uuidString
service.submitEmail(.link,AccountCredentials(name:"",email:googleEmail,password:linkedSecret));complete()
precondition(service.snapshot()["hasPassword"] as? Bool == true)
service.signOut();service.submitEmail(.signIn,AccountCredentials(name:"",email:googleEmail,password:linkedSecret));complete();precondition(service.snapshot()["signedIn"] as? Bool == true)
let signed=local("identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=fixture",["email":googleEmail,"password":linkedSecret,"returnSecureToken":true]);precondition(signed["localId"] as? String == google["localId"] as? String)
let exposed=String(data:try! JSONSerialization.data(withJSONObject:output),encoding:.utf8)!
for value in [secret,newSecret,linkedSecret,google["idToken"] as! String] { precondition(!exposed.contains(value)) }
service.stop()
print("PASS: Mac Firebase REST registration, display name, verification, wrong-password denial, sign-out/login, reset, neutral unknown-email response, Google credential linking, and no credentials in page payloads")
