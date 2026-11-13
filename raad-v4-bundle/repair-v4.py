from pathlib import Path
p=Path('raad-v4-app');m=p/'android/app/src/main/AndroidManifest.xml'
s=m.read_text();start=s.index('    <!-- Network');end=s.index('    <application',start)
s=s[:start]+'''    <!-- Device permissions are requested only when a feature is used. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="com.android.alarm.permission.SET_ALARM" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />
    <queries>
        <intent><action android:name="android.speech.RecognitionService" /></intent>
        <intent><action android:name="android.intent.action.SET_ALARM" /></intent>
    </queries>
''' + s[end:]
service='''        <service android:name=".WakeListeningService"
            android:exported="false"
            android:foregroundServiceType="microphone" />'''
s=s.replace(service,'')
m.write_text(s)

java=p/'android/app/src/main/java/ai/raad/personal/MainActivity.java'
s=java.read_text()
s=s.replace('import android.app.Activity;','import android.app.Activity;\nimport android.Manifest;\nimport android.content.pm.PackageManager;\nimport android.os.Build;')
s=s.replace('    private static final int SPEECH_REQUEST = 603;', '    private static final int SPEECH_REQUEST = 603;\n    private static final int MIC_PERMISSION = 604;\n    private String pendingWakeLanguage = "ar";')
needle='        @JavascriptInterface public String getChatGPTStatus()'
add='''        @JavascriptInterface public void reviewAction(String json) {
            main.post(() -> DeviceActionHandler.review(MainActivity.this, json));
        }
        @JavascriptInterface public void startWakeListening(String language) {
            main.post(() -> {
                pendingWakeLanguage = "en".equals(language) ? "en" : "ar";
                if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION);
                    return;
                }
                startWakeService();
            });
        }
        @JavascriptInterface public void stopWakeListening() {
            main.post(() -> stopService(new Intent(MainActivity.this, WakeListeningService.class)));
        }
        @JavascriptInterface public String getPendingWakeCommand() {
            android.content.SharedPreferences prefs = getSharedPreferences("raad_wake", MODE_PRIVATE);
            synchronized (MainActivity.class) {
                String command=prefs.getString("command", "");
                if (!command.isEmpty()) prefs.edit().remove("command").commit();
                return command;
            }
        }
'''
assert needle in s;s=s.replace(needle,add+needle)
needle='    private void emitVoice(String status, String text) {'
add='''    private void startWakeService() {
        Intent service = new Intent(this, WakeListeningService.class).setAction(WakeListeningService.ACTION_START);
        service.putExtra("language", pendingWakeLanguage);
        try {if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);}
        catch (Exception e) { feedback(false, "Could not start temporary listening: " + e.getMessage()); }
    }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(requestCode, permissions, grants);
        if (requestCode == MIC_PERMISSION) {
            if (grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startWakeService();
            else feedback(false, "Microphone permission was not granted.");
        }
    }
    public static void recordWakeCommand(Context context, String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        String command=raw.trim();
        if(command.length()>2000) command=command.substring(0,2000);
        synchronized (MainActivity.class) {
            context.getSharedPreferences("raad_wake", MODE_PRIVATE).edit().putString("command",command).commit();
        }
    }
'''
assert needle in s;s=s.replace(needle,add+needle)
java.write_text(s)

app=p/'src/app.js';s=app.read_text()
s=s.replace("import {localReply,liveReply} from './ai.js';", "import {localReply,liveReply} from './ai.js';\nimport {parseAgentAction,parseDeviceCommand,checkDeviceAction,DEVICE_COPY} from './device.js';")
needle="function nativeChatGPTReply(input,history,language)"
add='''function submitDeviceAction(action){
 const validated = checkDeviceAction(action.type,action);
 if(!globalThis.RaadAndroid?.reviewAction){showToast('Android device actions are available only in the APK.',true);return false;}
 globalThis.RaadAndroid.reviewAction(JSON.stringify(validated));
 return true;
}
function askForDeviceAction(type){
 if(!globalThis.RaadAndroid?.reviewAction){showToast('Install the Android APK for native actions.',true);return;}
 try {
   let values={};
   if(type==='alarm'){const time=prompt('Alarm time (24-hour HH:MM)','07:30');if(time===null)return;values={time,label:prompt('Alarm label','RAAD alarm')||'RAAD alarm'};}
   else if(type==='email'){const to=prompt('Recipient email address');if(to===null)return;const subject=prompt('Subject','');if(subject===null)return;const body=prompt('Email body');if(body===null)return;values={to,subject,body};}
   else if(type==='sms'||type==='whatsapp'){const phone=prompt('Recipient phone (international format)');if(phone===null)return;const message=prompt('Message');if(message===null)return;values={phone,message};}
   const action=checkDeviceAction(type,values);
   if(!confirm('RAAD will open another app for you to review.\\n\\n'+(DEVICE_COPY[type]||type)+'\\n\\nContinue?'))return;
   submitDeviceAction(action);
 }catch(err){showToast(err.message,true);}
}
'''
assert needle in s;s=s.replace(needle,add+needle)
# add assistant native action buttons to chat panel immediately before form
needle='<form class="chat-form" id="chat-form">'
insert='''<div class="device-action-tray"><button type="button" data-action="device-quick" data-type="alarm">Alarm</button><button type="button" data-action="device-quick" data-type="sms">SMS</button><button type="button" data-action="device-quick" data-type="whatsapp">WhatsApp</button><button type="button" data-action="device-quick" data-type="email">Email</button><button type="button" data-action="wake-start">Listen 5 min</button><button type="button" data-action="wake-stop">Stop</button></div><form class="chat-form" id="chat-form">'''
assert needle in s;s=s.replace(needle,insert)
needle=" try{if(state.chatGPT.connected&&globalThis.RaadAndroid?.askChatGPT)reply=await nativeChatGPTReply(input,rowsSorted('messages').reverse().filter(m=>m.id!==userMessage.id).slice(-8),state.prefs.responseLanguage);"
replace=""" try{const deviceCommand=parseDeviceCommand(input);
 if(deviceCommand){const ok=confirm('Review this Android action?\\n'+(DEVICE_COPY[deviceCommand.type]||deviceCommand.type));const started=ok&&submitDeviceAction(deviceCommand);reply={text:started?'Opening Android app to review your request.':'Action cancelled or unavailable.',mode:'local'};}
 else if(state.chatGPT.connected&&globalThis.RaadAndroid?.askChatGPT)reply=await nativeChatGPTReply(input,rowsSorted('messages').reverse().filter(m=>m.id!==userMessage.id).slice(-8),state.prefs.responseLanguage);"""
assert needle in s;s=s.replace(needle,replace)
needle=" if(reply.mode==='confirm'&&reply.action?.kind==='tasks'){"
add=""" if(reply.mode==='chatgpt'){
   const requested=parseAgentAction(reply.text);
   if(requested){
     if(requested.type==='task'){
       const okay=confirm('RAAD proposes creating a task: '+requested.title+'\\nApprove?');
       if(okay){await persist('tasks',{id:uid(),title:requested.title,description:'Created from approved assistant action',priority:'Medium',status:'To Do',createdAt:new Date().toISOString()});reply.text='Task created: '+requested.title;}
       else reply.text='Task creation cancelled.';
     }else{
       const okay=confirm('RAAD proposes an Android action: '+requested.type+'\\n'+(DEVICE_COPY[requested.type]||'Review before proceeding')+'\\nApprove?');
       if(okay&&submitDeviceAction(requested))reply.text='Opened Android app to review your '+requested.type+' request.';
       else reply.text='Android action cancelled or unavailable.';
     }
   }
 }
"""
assert needle in s;s=s.replace(needle,add+needle)
needle=" case'voice-listen':toggleVoice();break;"
repl=""" case'device-quick':askForDeviceAction(btn.dataset.type);break;
 case'wake-start':if(!globalThis.RaadAndroid?.startWakeListening){showToast('Temporary background voice listening requires Android APK.',true);break;}if(!confirm('Start 5-minute microphone session? Android shows an ongoing notification. The service may stop listening with the screen off; it cannot bypass device lock.'))break;globalThis.RaadAndroid.startWakeListening(state.prefs.responseLanguage==='en'?'en':'ar');showToast('Temporary microphone session requested. Tap Stop to end.');break;
 case'wake-stop':globalThis.RaadAndroid?.stopWakeListening?.();showToast('Stop requested for temporary microphone session.');break;
 case'voice-listen':toggleVoice();break;"""
assert needle in s;s=s.replace(needle,repl)
# consume wake while on launching app, after load data and before renderShell: doAssistant after rendering
needle='renderShell();checkAutomations();autoTimer=setInterval(checkAutomations,60_000);'
repl='''renderShell();checkAutomations();autoTimer=setInterval(checkAutomations,60_000);
 if(globalThis.RaadAndroid?.getPendingWakeCommand){const wake=globalThis.RaadAndroid.getPendingWakeCommand();if(wake){setPage('assistant');showToast('Voice command captured — please review.');const input=document.getElementById('message-input');if(input)input.value=wake;}}
'''
assert needle in s;s=s.replace(needle,repl)
app.write_text(s)
# styles
css=p/'src/raadv2.css';css.write_text(css.read_text()+'''\n.device-action-tray{display:flex;gap:7px;overflow-x:auto;scrollbar-width:thin;padding:10px 0 7px;align-items:center;}\n.device-action-tray button{white-space:nowrap;border:1px solid rgba(94,185,235,.26);background:rgba(9,37,60,.62);color:#bfeafa;padding:9px 13px;border-radius:12px;font-size:12px;cursor:pointer;}\n.device-action-tray button:focus-visible{outline:2px solid #6ef2fd;outline-offset:2px;}\n.device-action-tray button:hover{background:rgba(18,90,128,.5);}\n''')
print('patched',m,java,app)