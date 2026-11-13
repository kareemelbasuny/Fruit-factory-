package ai.raad.personal;

import android.app.Activity;
import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Android wrapper around bundled RAAD UI. Optional ChatGPT OAuth uses the system browser; offline records require no account.
 * App assets are loaded under a stable virtual HTTPS origin so IndexedDB is persistent.
 */
public final class MainActivity extends Activity {
    private static final String APP_HOST = "app.raad.local";
    private static final String HOME = "https://" + APP_HOST + "/index.html";
    private static final int FILE_PICKER = 601;
    private static final int SAVE_BACKUP = 602;
    private static final int SPEECH_REQUEST = 603;
    private static final int WAKE_PERMISSIONS_REQUEST = 604;
    private static final String WAKE_STORE = "raad-wake-session";
    private String pendingLanguage = "ar-EG";
    private boolean webLoaded = false;
    private static java.lang.ref.WeakReference<MainActivity> activeInstance = new java.lang.ref.WeakReference<>(null);
    private boolean visible = false;
    private static final int MAX_BACKUP_BYTES = 15 * 1024 * 1024;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private ChatGPTConnector chatGPT;
    private TextToSpeech tts;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] backupData;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xff080f1c);
        getWindow().setNavigationBarColor(0xff080f1c);
        web = new WebView(this);
        web.setBackgroundColor(0xff080f1c);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        FrameLayout frame = new FrameLayout(this);
        frame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true); // Needed only for user-selected JSON backup imports.
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        chatGPT = new ChatGPTConnector(this, web);
        web.addJavascriptInterface(new AndroidBridge(), "RaadAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                webLoaded = true;
                deliverPendingWake();
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equalsIgnoreCase(uri.getScheme()) || !APP_HOST.equalsIgnoreCase(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
                // Strict path allowlist — no traversal, asset navigation, or arbitrary file reads.
                if (path.contains("..") || path.contains("\\") || !path.matches("/[a-zA-Z0-9_./-]+")) return notFound();
                String assetPath = "www" + path;
                try {
                    InputStream in = getAssets().open(assetPath);
                    String mime = mimeType(path);
                    return new WebResourceResponse(mime, mime.startsWith("text/") || mime.contains("json") || mime.contains("javascript") ? "UTF-8" : null, 200, "OK", Collections.singletonMap("Cache-Control", "no-cache"), in);
                } catch (Exception ignored) { return notFound(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) && APP_HOST.equalsIgnoreCase(uri.getHost())) return false;
                if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && request.isForMainFrame()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) { }
                }
                return true; // Never open untrusted pages inside bridge-enabled WebView.
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    startActivityForResult(intent, FILE_PICKER);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        tts = new TextToSpeech(this, status -> { /* Availability checked when speaking. */ });
        web.loadUrl(HOME);
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", Collections.emptyMap(),
            new ByteArrayInputStream("Not found".getBytes(StandardCharsets.UTF_8)));
    }
    private static String mimeType(String path) {
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".json") || path.endsWith(".webmanifest")) return "application/json";
        if (path.endsWith(".html")) return "text/html";
        return "application/octet-stream";
    }
    private void feedback(boolean ok, String message) {
        main.post(() -> {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            if (web != null) {
                try { web.evaluateJavascript("window.dispatchEvent(new CustomEvent('raad-native-export',{detail:{ok:" + ok + ",message:" + JSONObject.quote(message) + "}}))", null); }
                catch (Exception ignored) { }
            }
        });
    }
    private void beginWakeService() {
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},WAKE_PERMISSIONS_REQUEST);
            return;
        }
        Intent service=new Intent(this,WakeListeningService.class).setAction(WakeListeningService.ACTION_START);
        service.putExtra("language",pendingLanguage.equals("en")?"en":"ar");
        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);}
        catch(Exception e){feedback(false,"Android blocked microphone access. Open RAAD and retry.");}
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==WAKE_PERMISSIONS_REQUEST){
            if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)beginWakeService();
            else feedback(false,"Microphone permission denied. Listening was not started.");
        }
    }
    public static void recordWakeCommand(Context context,String prompt){
        if(prompt==null||prompt.isEmpty()||prompt.length()>500)return;
        context.getSharedPreferences(WAKE_STORE,Context.MODE_PRIVATE).edit().putString("pending",prompt).apply();
        MainActivity activity=activeInstance.get();
        if(activity!=null&&activity.visible)activity.main.post(activity::deliverPendingWake);
    }
    private String consumePendingWake(){
        SharedPreferences sp=getSharedPreferences(WAKE_STORE,MODE_PRIVATE);
        String prompt=sp.getString("pending","");
        if(!prompt.isEmpty())sp.edit().remove("pending").apply();
        return prompt;
    }
    private void deliverPendingWake(){
        if(!webLoaded||web==null||!visible)return;
        String pending=consumePendingWake();
        if(pending.isEmpty())return;
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('raad-wake',{detail:{prompt:"+JSONObject.quote(pending)+"}}))",null);
    }
    @Override protected void onResume(){super.onResume();visible=true;activeInstance=new java.lang.ref.WeakReference<>(this);main.postDelayed(this::deliverPendingWake,700);main.postDelayed(()->{if(web!=null)web.evaluateJavascript("window.dispatchEvent(new Event('raad-wake-check'))",null);},750);}
    @Override protected void onPause(){visible=false;super.onPause();}
    private final class AndroidBridge {
        @JavascriptInterface public void exportBackup(String json, String filename) {
            if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_BACKUP_BYTES || json.length() < 2 || !json.trim().startsWith("{")) {
                feedback(false, "Backup data is invalid or too large (15MB limit)."); return;
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            String safeName = filename != null && filename.matches("raad-backup-[0-9-]{10}\\.json") ? filename : "raad-backup.json";
            main.post(() -> {
                backupData = bytes;
                Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);
                save.setType("application/json");
                save.putExtra(Intent.EXTRA_TITLE, safeName);
                try { startActivityForResult(save, SAVE_BACKUP); }
                catch (ActivityNotFoundException ex) { backupData = null; feedback(false, "No document saving app available."); }
            });
        }
        @JavascriptInterface public void listen(String language) {
            main.post(() -> {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en".equals(language) ? "en-US" : "ar-EG");
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
                try { startActivityForResult(intent, SPEECH_REQUEST); }
                catch (ActivityNotFoundException e) { emitVoice("error", "No speech recognition service installed on this device."); }
            });
        }
        @JavascriptInterface public void speak(String text, String language) {
            if (text == null || text.length() > 1500) return;
            main.post(() -> {
                if (tts == null) return;
                tts.setLanguage("en".equals(language) ? Locale.US : new Locale("ar", "EG"));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "raad-response");
            });
        }
        @JavascriptInterface public void stopSpeaking() { main.post(() -> { if (tts != null) tts.stop(); }); }
        @JavascriptInterface public String getChatGPTStatus() { return chatGPT.status().toString(); }
        @JavascriptInterface public void connectChatGPT() { chatGPT.connect(); }
        @JavascriptInterface public void disconnectChatGPT() { chatGPT.disconnect(); }
        @JavascriptInterface public void askChatGPT(String requestId, String text, String history, String language) {
            chatGPT.ask(requestId, text, history, language);
        }
        @JavascriptInterface public String getWakeStatus() {
            JSONObject result=new JSONObject();
            try{
                boolean mic=Build.VERSION.SDK_INT<23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;
                boolean available=Build.VERSION.SDK_INT>=31 && android.speech.SpeechRecognizer.isOnDeviceRecognitionAvailable(MainActivity.this);
                boolean enabled=WakeListeningService.isRunning();
                String error=getSharedPreferences(WakeListeningService.PREFS,MODE_PRIVATE).getString("wake_error","");
                result.put("enabled",enabled);result.put("onDeviceAvailable",available);
                result.put("microphoneGranted",mic);result.put("error",error);
            } catch(Exception ignored){}
            return result.toString();
        }
        @JavascriptInterface public void startWakeMode(){startWakeSession();}
        @JavascriptInterface public void stopWakeMode(){stopWakeSession();}
        @JavascriptInterface public String takePendingVoiceCommand(){
            SharedPreferences prefs=getSharedPreferences(WakeListeningService.PREFS,MODE_PRIVATE);
            String command=prefs.getString("pending_command","");
            if(!command.isEmpty())prefs.edit().remove("pending_command").apply();
            return command;
        }
        @JavascriptInterface public void performDeviceAction(String args){requestDeviceAction(args);}
        @JavascriptInterface public void requestNativeNotifications(){
            main.post(()->{
                if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},605);
                }else feedback(true,"Notification permission already available.");
            });
        }
        @JavascriptInterface public void startWakeSession() {
            main.post(() -> {pendingLanguage="ar-EG";beginWakeService();});
        }
        @JavascriptInterface public void stopWakeSession() {
            main.post(() -> stopService(new Intent(MainActivity.this,WakeListeningService.class)));
        }
        @JavascriptInterface public String takePendingWakePrompt(){return consumePendingWake();}
        @JavascriptInterface public void requestDeviceAction(String argsJson){
            main.post(() -> DeviceActionHandler.review(MainActivity.this,argsJson));
        }
        @JavascriptInterface public void openChatGPTWithPrompt(String prompt) {
            if (prompt == null || prompt.length() > 6000 || prompt.isEmpty()) return;
            main.post(() -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("RAAD prompt", prompt));
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))); }
                catch (Exception e) { feedback(false, "Could not open ChatGPT. Your prompt is copied."); }
            });
        }
        @JavascriptInterface public void openChatGPT() {
            main.post(() -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/"))); }
                           catch (Exception ignored) { feedback(false, "Could not open ChatGPT."); } });
        }

    }
    private void emitVoice(String status, String text) {
        if (web == null) return;
        web.evaluateJavascript("window.dispatchEvent(new CustomEvent('raad-native-voice',{detail:{status:" + JSONObject.quote(status) + ",text:" + JSONObject.quote(text == null ? "" : text) + "}}))", null);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FILE_PICKER) {
            ValueCallback<Uri[]> cb = fileCallback; fileCallback = null;
            if (cb != null) cb.onReceiveValue(result == RESULT_OK && data != null && data.getData() != null ? new Uri[] {data.getData()} : null);
        } else if (request == SAVE_BACKUP) {
            byte[] bytes = backupData; backupData = null;
            if (result == RESULT_OK && data != null && data.getData() != null && bytes != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("No output stream");
                    out.write(bytes);
                    out.flush();
                    feedback(true, "RAAD backup saved successfully.");
                } catch (Exception ex) { feedback(false, "Could not save backup."); }
            } else feedback(false, "Backup export cancelled.");
        } else if (request == SPEECH_REQUEST) {
            if (result == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) { emitVoice("result", results.get(0)); return; }
            }
            emitVoice("cancelled", "");
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        main.postDelayed(this::deliverPendingWake,450);
        main.postDelayed(()->{if(web!=null)web.evaluateJavascript("window.dispatchEvent(new Event('raad-wake-check'))",null);},700);
    }
    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}