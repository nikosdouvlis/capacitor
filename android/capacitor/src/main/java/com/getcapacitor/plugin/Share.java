package com.getcapacitor.plugin;

import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@NativePlugin()
public class Share extends Plugin {
  private static final String FILE_SHARING_TEMP_DIR = "filesharing_dir";

  @PluginMethod()
  public void share(PluginCall call) {
    String title = call.getString("title", "");
    String text = call.getString("text");
    String url = call.getString("url");
    String dialogTitle = call.getString("dialogTitle", "Share");
    JSObject file = call.getObject("file");
    String base64Data = file.getString("fromBase64", "");
    String fileName = file.getString("fileName", "");

    if (text == null && url == null && base64Data.isEmpty()) {
      call.error("Must provide at least one of: url, message, file");
      return;
    }

    Intent intent = new Intent(Intent.ACTION_SEND);
    String type = "text/plain";

    if(!base64Data.isEmpty()) {
      if(fileName.isEmpty()) {
        call.error("Must provide a valid filename for the file");
      }

      byte[] decodedData;
      try {
        base64Data = removeTypePrefixFromBase64String(base64Data);
        decodedData = Base64.decode(base64Data, Base64.DEFAULT);
      } catch (IllegalArgumentException e) {
        call.error("Could not create file - invalid base64 data");
        return;
      }

      File cachedFile = new File(getCacheDir(), fileName);
      try (FileOutputStream fos = new FileOutputStream(cachedFile)) {
        fos.write(decodedData);
        fos.flush();
      } catch (IOException e) {
        call.error("Failed to create file in cache directory");
        return;
      }
      Uri contentUri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", cachedFile);
      intent.putExtra(Intent.EXTRA_STREAM, contentUri);
    }

    if (text != null) {
      // If they supplied both text and url, concat em
      if (url != null && url.startsWith("http")) {
        text = text + " " + url;
      }
      intent.putExtra(Intent.EXTRA_TEXT, text);
    } else if (url != null) {
      if (url.startsWith("http")) {
        intent.putExtra(Intent.EXTRA_TEXT, url);
      } else if (url.startsWith("file:")) {
        type = getMimeType(url);
        intent.setType(type);
        Uri fileUrl = FileProvider.getUriForFile(getActivity(), getContext().getPackageName() + ".fileprovider", new File(Uri.parse(url).getPath()));
        intent.putExtra(Intent.EXTRA_STREAM, fileUrl);
      } else {
        call.error("Unsupported url");
        return;
      }
    }
    intent.setTypeAndNormalize(type);

    if (title != null) {
      intent.putExtra(Intent.EXTRA_SUBJECT, title);
    }

    Intent chooser = Intent.createChooser(intent, dialogTitle);
    chooser.addCategory(Intent.CATEGORY_DEFAULT);

    getActivity().startActivity(chooser);
    call.success();
  }

  private String getMimeType(String url) {
    String type = null;
    String extension = MimeTypeMap.getFileExtensionFromUrl(url);
    if (extension != null) {
      type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }
    return type;
  }

  private String removeTypePrefixFromBase64String(String base64Data) {
    return base64Data.contains(",") ? base64Data.split(",")[1] : base64Data;
  }

  private File getCacheDir() {
    File cacheDir = new File(getContext().getFilesDir(), FILE_SHARING_TEMP_DIR);
    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
      return cacheDir;
    }

    for (File f : cacheDir.listFiles()) {
      f.delete();
    }
    return cacheDir;
  }
}
