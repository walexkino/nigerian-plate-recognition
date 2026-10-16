import Cocoa

final class FormQA: NSObject, NSApplicationDelegate {
    var parent: NSWindow!
    var controller: AccountFormController?
    var index = 0
    let modes: [AccountFormMode] = [.signIn,.create,.link,.reset]
    func applicationDidFinishLaunching(_ note: Notification) {
        parent=NSWindow(contentRect:NSRect(x:0,y:0,width:800,height:800),styleMask:[.titled],backing:.buffered,defer:false)
        parent.title="PlateLab native account form QA";parent.center();parent.orderFront(nil)
        next()
    }
    func next(){
        if index == modes.count { print("PASS: four native account sheets fit their content and use secure password fields");exit(0) }
        let form=AccountFormController(mode:modes[index],email:index == 2 ? "synthetic@example.test" : "");controller=form;form.present(on:parent)
        DispatchQueue.main.asyncAfter(deadline:.now()+0.25){
            let view=form.window!.contentView!
            func descendants(_ view:NSView)->[NSView]{view.subviews.flatMap{[$0]+descendants($0)}}
            let controls=descendants(view).filter{$0 is NSControl && !$0.isHidden}
            for control in controls { let rect=control.convert(control.bounds,to:view);precondition(rect.minX>=0 && rect.minY>=0 && rect.maxX<=view.bounds.maxX+1 && rect.maxY<=view.bounds.maxY+1,"Control outside sheet") }
            let secure=descendants(view).filter{$0 is NSSecureTextField};precondition(secure.count == (self.index == 3 ? 0 : self.index == 0 ? 1 : 2))
            if let bitmap=view.bitmapImageRepForCachingDisplay(in:view.bounds){view.cacheDisplay(in:view.bounds,to:bitmap);let data=bitmap.representation(using:.png,properties:[:]);try? data?.write(to:URL(fileURLWithPath:CommandLine.arguments[1]).appendingPathComponent("account-form-\(self.index).png"))}
            form.finish();self.controller=nil;self.index+=1;DispatchQueue.main.asyncAfter(deadline:.now()+0.1){self.next()}
        }
    }
}
let app=NSApplication.shared,delegate=FormQA();app.delegate=delegate;app.run()
