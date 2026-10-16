import Cocoa

enum AccountFormMode { case signIn, create, link, reset }
struct AccountCredentials {
    let name: String
    let email: String
    let password: String
    static func validEmail(_ value: String) -> Bool {
        value.count <= 254 && value.range(of: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", options: .regularExpression) != nil
    }
    static func validate(mode: AccountFormMode, name: String, email: String, password: String, confirmation: String) -> String? {
        if !validEmail(email) { return "Enter a valid email address." }
        if mode == .reset { return nil }
        if password.isEmpty || password.count > 4096 { return "Enter your password." }
        if mode == .create || mode == .link {
            if password.count < 6 || password.count > 128 { return "Use a password of 6–128 characters." }
            if password != confirmation { return "The passwords do not match." }
        }
        if mode == .create && (name.isEmpty || name.count > 100) { return "Choose a display name of 1–100 characters." }
        return nil
    }
}

/// Real native secure fields keep passwords out of the WebView and bridge entirely.
final class AccountFormController: NSWindowController, NSWindowDelegate {
    let mode: AccountFormMode
    private let name = NSTextField()
    private let email = NSTextField()
    private let password = NSSecureTextField()
    private let confirmation = NSSecureTextField()
    private let message = NSTextField(wrappingLabelWithString: "")
    private let submit = NSButton()
    private let cancel = NSButton()
    var submitted: ((AccountCredentials) -> Void)?
    var canceled: (() -> Void)?
    init(mode: AccountFormMode, email address: String = "") {
        self.mode = mode
        let panel = NSPanel(contentRect: NSRect(x: 0, y: 0, width: 440, height: 480), styleMask: [.titled], backing: .buffered, defer: false)
        panel.title = mode == .create ? "Create your PlateLab account" : mode == .link ? "Add a PlateLab password" : mode == .reset ? "Reset your password" : "Sign in to PlateLab"
        panel.sharingType = .none
        super.init(window: panel); panel.delegate = self
        let stack = NSStackView(); stack.orientation = .vertical; stack.alignment = .leading; stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false
        let content = panel.contentView!; content.addSubview(stack)
        NSLayoutConstraint.activate([stack.leadingAnchor.constraint(equalTo: content.leadingAnchor, constant: 24),stack.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: -24),stack.topAnchor.constraint(equalTo: content.topAnchor, constant: 24)])
        let intro = NSTextField(wrappingLabelWithString: mode == .create ? "Your own account. Google sign-in is optional." : mode == .link ? "Set a separate PlateLab password. Your Google sign-in will keep working." : mode == .reset ? "Enter your email and we'll request a reset link." : "Welcome back. Use your email address and PlateLab password.")
        intro.font = .systemFont(ofSize: 15); stack.addArrangedSubview(intro); intro.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
        func addField(_ title: String, _ field: NSTextField) {
            let label = NSTextField(labelWithString: title); label.font = .systemFont(ofSize: 12, weight: .medium); stack.addArrangedSubview(label)
            field.font = .systemFont(ofSize: 15); field.bezelStyle = .roundedBezel; field.setAccessibilityLabel(title)
            stack.addArrangedSubview(field);field.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true;field.heightAnchor.constraint(equalToConstant: 34).isActive = true
        }
        if mode == .create { addField("Display name", name) }
        addField("Email address", email);email.stringValue = address;email.isEditable = mode != .link
        if mode != .reset { addField("Password", password) }
        if mode == .create || mode == .link { addField("Confirm password", confirmation) }
        message.font = .systemFont(ofSize: 12);message.textColor = .secondaryLabelColor;message.stringValue = mode == .create || mode == .link ? "Use 6–128 characters. A long, unique passphrase is a good choice." : "Passwords are sent securely to Firebase Authentication and are not saved in PlateLab."
        stack.addArrangedSubview(message);message.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
        submit.title = mode == .create ? "Create account" : mode == .link ? "Save password" : mode == .reset ? "Send reset link" : "Sign in"
        submit.bezelStyle = .rounded;submit.keyEquivalent = "\r";submit.target = self;submit.action = #selector(send)
        cancel.title = "Cancel";cancel.bezelStyle = .rounded;cancel.keyEquivalent = "\u{1b}";cancel.target = self;cancel.action = #selector(cancelForm)
        let actions = NSStackView(views: [cancel, submit]);actions.orientation = .horizontal;actions.spacing = 12;stack.addArrangedSubview(actions)
        content.layoutSubtreeIfNeeded();panel.setContentSize(NSSize(width: 440, height: stack.fittingSize.height + 48))
    }
    required init?(coder: NSCoder) { fatalError("Not supported") }
    func present(on parent: NSWindow) { parent.beginSheet(window!);window?.makeFirstResponder(mode == .create ? name : mode == .link ? password : email) }
    @objc private func send() {
        let values = AccountCredentials(name: name.stringValue.trimmingCharacters(in: .whitespacesAndNewlines),email: email.stringValue.trimmingCharacters(in: .whitespacesAndNewlines),password: password.stringValue)
        if let error = AccountCredentials.validate(mode: mode,name: values.name,email: values.email,password: values.password,confirmation: confirmation.stringValue) { show(error);return }
        password.stringValue = "";confirmation.stringValue = "";setBusy(true);submitted?(values)
    }
    func setBusy(_ busy: Bool) { for field in [name,email,password,confirmation] { field.isEnabled = !busy };submit.isEnabled = !busy;if busy { message.stringValue = "Please wait…" } }
    func show(_ text: String) { setBusy(false);message.stringValue = text }
    @objc private func cancelForm() { finish();canceled?() }
    func finish() { password.stringValue = "";confirmation.stringValue = "";if let window = window { window.sheetParent?.endSheet(window);window.orderOut(nil) } }
    func windowWillClose(_ notification: Notification) { password.stringValue = "";confirmation.stringValue = "" }
}
