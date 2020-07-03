import Foundation

/**
 * Implement Sharing text and urls
 */
@objc(CAPSharePlugin)
public class CAPSharePlugin : CAPPlugin {
    
    private static func removeTypePrefixFromBase64String(base64: String) -> String {
        return base64.contains(",") ? base64.components(separatedBy: ",")[1] : base64
    }
    
    @objc func share(_ call: CAPPluginCall) {
        var items = [Any]()
        
        if let text = call.options["text"] as? String {
            items.append(text)
        }
        
        if let url = call.options["url"] as? String {
            let urlObj = URL(string: url)
            items.append(urlObj!)
        }
        
        let fileOptions = call.options["file"] as? [String:String?] ?? [:]
        if var base64Data = fileOptions["fromBase64"] as? String {
            base64Data = CAPSharePlugin.removeTypePrefixFromBase64String(base64: base64Data)
            
            guard let fileObj = Data(base64Encoded: base64Data) else {
                call.error("Could not create file - invalid base64 data")
                return
            }
            
            guard let base64Filename = fileOptions["fileName"] as? String else {
                call.error("Must provide a valid filename for the file")
                return
            }
            
            let fileUri = FileManager.default.temporaryDirectory.appendingPathComponent(base64Filename)
            do {
                try fileObj.write(to: fileUri)
            } catch {
                call.error("Failed to create file in cache directory")
                return
            }
            items.append(fileUri)
        }
        
        let title = call.getString("title")
        
        
        if items.count == 0 {
            call.error("Must provide at least one of: url, message, file")
            return
        }
        
        DispatchQueue.main.async {
            let actionController = UIActivityViewController(activityItems: items, applicationActivities: nil)
            
            if title != nil {
                // https://stackoverflow.com/questions/17020288/how-to-set-a-mail-subject-in-uiactivityviewcontroller
                actionController.setValue(title, forKey: "subject")
            }
            
            actionController.completionWithItemsHandler = { (activityType, completed, _ returnedItems, activityError) in
                if activityError != nil {
                    call.error("Error sharing item", activityError)
                    return
                }
                
                // TODO: Support returnedItems
                
                call.success([
                    "completed": completed,
                    "activityType": activityType?.rawValue ?? ""
                ])
            }
            
            self.setCenteredPopover(actionController)
            self.bridge.viewController.present(actionController, animated: true, completion: nil)
        }
    }
}
