import Foundation
let config = AccountConfig(dictionary: ["BUNDLE_ID":"test.app","PROJECT_ID":"demo-test","CLIENT_ID":"123-test.apps.googleusercontent.com","API_KEY":String(repeating:"x",count:39)], bundleID:"test.app")!
let proof = OAuthProof(verifier:"dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk",state:"expected-state",nonce:"expected-nonce")
precondition(proof.challenge == "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
let code = try proof.code(URL(string:config.redirect+"?state=expected-state&code=fixture")!,config:config)
precondition(code == "fixture")
for url in [config.redirect+"?state=wrong&code=fixture",config.redirect+"?code=fixture",config.redirect+"?state=expected-state&state=bad&code=fixture",config.redirect+"?state=expected-state&code=a&code=b",config.redirect+"?state=expected-state&code=a#fragment","https://attacker.test/oauthredirect?state=expected-state&code=a",config.scheme+"://attacker.test/oauthredirect?state=expected-state&code=a"] {
 do { _ = try proof.code(URL(string:url)!,config:config); fatalError("Untrusted callback accepted") } catch {}
}
let payload = OAuthProof.encode(Data("{\"nonce\":\"expected-nonce\"}".utf8))
precondition(proof.acceptsNonce("header."+payload+".signature"))
precondition(!proof.acceptsNonce("header.e30.signature"))
precondition(!proof.acceptsNonce("malformed"))
let a = try OAuthProof(),b = try OAuthProof()
precondition(a.verifier.count == 43 && a.state.count == 43 && a.nonce.count == 43)
precondition(a.verifier != b.verifier && a.state != b.state && a.nonce != b.nonce)
precondition(AccountConfig(dictionary:["BUNDLE_ID":"wrong"],bundleID:"test.app") == nil)
print("PASS: PKCE standard vector; random proofs; callback state/origin/duplicate rejection; nonce binding; invalid config")

let trustedPage = URL(fileURLWithPath:"/Applications/PlateLab.app/Contents/Resources/index.html")
let policy = NativePagePolicy(page:trustedPage)
precondition(policy.accepts(trustedPage,mainFrame:true))
precondition(!policy.accepts(trustedPage,mainFrame:false))
for url in ["https://attacker.test/index.html","file:///tmp/index.html","file:///Applications/PlateLab.app/Contents/Resources/index.html?injected=1","file://attacker.test/Applications/PlateLab.app/Contents/Resources/index.html","data:text/html,attack"] {
 precondition(!policy.accepts(URL(string:url),mainFrame:true))
}
print("PASS: bridge rejects remote pages, subframes, unexpected local files, query and file-host injection")
