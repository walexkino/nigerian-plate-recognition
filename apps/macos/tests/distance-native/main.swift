import Cocoa
import Vision
let bitmap=NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:4096,pixelsHigh:2048,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!
NSGraphicsContext.saveGraphicsState();NSGraphicsContext.current=NSGraphicsContext(bitmapImageRep:bitmap)
NSColor.white.setFill();NSRect(x:0,y:0,width:4096,height:2048).fill()
("ABC 123 DE" as NSString).draw(at:NSPoint(x:2900,y:950),withAttributes:[.font:NSFont.monospacedSystemFont(ofSize:80,weight:.bold),.foregroundColor:NSColor.black])
NSGraphicsContext.restoreGraphicsState()
let data=bitmap.representation(using:.jpeg,properties:[.compressionFactor:0.99])!
let crop=try detailedPlateRegion(imageURL:nil,data:data,region:[2800.0/4096,900.0/2048,1100.0/4096,400.0/2048])
precondition(crop.width==1100 && crop.height==400)
let readings=try recognize(crop.data)
let strings=readings.flatMap{($0["alternatives"] as? [[String:Any]] ?? []).compactMap{$0["text"] as? String}}
precondition(strings.contains{$0.replacingOccurrences(of:" ",with:"").contains("ABC123DE")},"Generated text missing from native detailed crop")
do { _=try detailedPlateRegion(imageURL:nil,data:data,region:[0.9,0.9,0.5,0.5]);fatalError("Invalid crop accepted") }catch{}
print("PASS original 4096px source crop: 1100×400 pixels, generated plate recognized, invalid crop rejected")
