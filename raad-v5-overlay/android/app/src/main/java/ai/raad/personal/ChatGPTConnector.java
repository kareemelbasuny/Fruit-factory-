package ai.raad.personal;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local, opt-in OpenAI Sign in with ChatGPT plan usage for personal/open-source clients.
 * OAuth tokens never cross the JavaScript interface; only text/status passes to WebView.
 * Specifications: https://developers.openai.com/siwc/token-sharing-open-source/ */
public final class ChatGPTConnector {
  private static final String AUTH = "https://auth.openai.com/api/accounts/authorize";
  private static final String TOKEN = "https://auth.openai.com/api/accounts/oauth/token";
  private static final String JWKS = "https://auth.openai.com/.well-known/jwks.json";
  private static final String RESOURCE = "https://api.openai.com/v1";
  private static final String PREFS = "raad-chatgpt-v1";
  private static final String AES_ALIAS = "RAADChatGPTPrivateTokens";
  private final Activity activity;
  private final WebView web;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final SharedPreferences storage;
  private final Object tokenLock = new Object();
  private volatile String lastError = "";
  private volatile boolean connecting = false;
  private volatile boolean replying = false;
  private JSONObject profile;

  public ChatGPTConnector(Activity activity, WebView web) {
    this.activity = activity; this.web = web;
    this.storage = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
    try { profile = loadProtectedProfile(); } catch (Exception e) { lastError = "Protected account storage is unavailable; sign in again."; }
  }
  private static String enc(String s) throws Exception {return URLEncoder.encode(s, "UTF-8");}
  private static String base64(byte[] raw) {return Base64.encodeToString(raw, Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);}
  private static byte[] decode(String raw) {return Base64.decode(raw, Base64.URL_SAFE|Base64.NO_WRAP);}
  private static String secret(int bytes) {byte[] b = new byte[bytes]; new SecureRandom().nextBytes(b);return base64(b);}
  private static String hostId(SharedPreferences prefs) {
    String v=prefs.getString("host_id","");
    if(v.isEmpty()){v="urn:uuid:"+UUID.randomUUID();prefs.edit().putString("host_id",v).apply();}return v;
  }
  public JSONObject status() {
    JSONObject o = new JSONObject();
    try { boolean granted = profile != null && profile.optString("scopes").contains("chatgpt.tokens.use.direct") && !profile.optString("refresh_token").isEmpty();
      o.put("status", connecting ? "connecting" : granted ? "connected" : "not_connected");
      o.put("connected",granted);
      o.put("email",profile == null ? "" : profile.optString("email"));
      o.put("busy",replying);
      o.put("error",lastError);
      o.put("provider","ChatGPT Plan");
    }catch(Exception ignored){}return o;
  }
  private void emit(JSONObject data){
    main.post(()->{try{String encoded=JSONObject.quote(data.toString());web.evaluateJavascript(
      "window.dispatchEvent(new CustomEvent('raad-chatgpt',{detail:JSON.parse("+encoded+")}))",null);
    }catch(Exception ignored){}});
  }
  private void emitStatus(){JSONObject j=status();try{j.put("type","status");}catch(Exception ignored){}emit(j);}
  private void emitReply(String requestId, String text, String error){
    JSONObject j=new JSONObject();try{j.put("type","reply");j.put("requestId",requestId);j.put("text",text);j.put("error",error);}catch(Exception ignored){}emit(j);
  }
  private SecretKey key() throws Exception{
    KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
    SecretKey existing=(SecretKey)store.getKey(AES_ALIAS,null);if(existing!=null)return existing;
    KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
    generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
    return generator.generateKey();
  }
  private void saveProfile(JSONObject p) throws Exception{
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
    byte[] encrypted=cipher.doFinal(p.toString().getBytes(StandardCharsets.UTF_8));
    storage.edit().putString("vault",base64(cipher.getIV())+"."+base64(encrypted)).commit();
    synchronized(tokenLock){profile=p;}
  }
  private JSONObject loadProtectedProfile() throws Exception{
    String v=storage.getString("vault","");if(v.isEmpty())return null;
    String[] halves=v.split("\\.",2);if(halves.length!=2)throw new Exception("Invalid credentials");
    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,decode(halves[0])));
    return new JSONObject(new String(cipher.doFinal(decode(halves[1])),StandardCharsets.UTF_8));
  }
  private static JSONObject request(String endpoint, String method, String bearer, String body, String type) throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
    c.setRequestMethod(method);c.setConnectTimeout(20000);c.setReadTimeout(35000);c.setUseCaches(false);
    c.setRequestProperty("Accept","application/json");
    if(bearer!=null)c.setRequestProperty("Authorization","Bearer "+bearer);
    if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type",type);try(OutputStream stream=c.getOutputStream()){stream.write(body.getBytes(StandardCharsets.UTF_8));}}
    int status=c.getResponseCode();
    try(InputStream in=status>=400?c.getErrorStream():c.getInputStream()){
      if(in==null)throw new Exception("Request returned HTTP "+status);
      byte[] data=readLimited(in,1500000);
      JSONObject payload=new JSONObject(new String(data,StandardCharsets.UTF_8));
      if(status>=400){JSONObject err=payload.optJSONObject("error");throw new Exception(err!=null?err.optString("message","Request failed ("+status+")"):"Request failed ("+status+")");}
      return payload;
    }finally{c.disconnect();}
  }
  private static byte[] readLimited(InputStream in,int max) throws Exception{
    ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
    while((n=in.read(buf))!=-1){if(result.size()+n>max)throw new Exception("Response exceeded safe size");result.write(buf,0,n);}return result.toByteArray();
  }
  private static String form(String... values) throws Exception{
    StringBuilder b=new StringBuilder();for(int i=0;i<values.length;i+=2){if(i>0)b.append('&');b.append(enc(values[i])).append('=').append(enc(values[i+1]));}return b.toString();
  }
  private static JSONObject verifyId(String jwt, String issuedId, String nonce) throws Exception {
    String[] parts=jwt.split("\\.");if(parts.length!=3)throw new Exception("Invalid identity token");
    JSONObject header=new JSONObject(new String(decode(parts[0]),StandardCharsets.UTF_8));
    if(!"RS256".equals(header.optString("alg")))throw new Exception("Unsupported identity signature");
    JSONObject claims=new JSONObject(new String(decode(parts[1]),StandardCharsets.UTF_8));
    if(!"https://auth.openai.com".equals(claims.optString("iss")))throw new Exception("Identity issuer mismatch");
    Object aud=claims.opt("aud");boolean match=issuedId.equals(aud);
    if(aud instanceof JSONArray){JSONArray a=(JSONArray)aud;for(int i=0;i<a.length();i++)if(issuedId.equals(a.optString(i)))match=true;}
    if(!match||!nonce.equals(claims.optString("nonce")))throw new Exception("Identity verification failed");
    long now=System.currentTimeMillis()/1000;
    if(claims.optLong("exp",0)<now-30 || claims.optLong("iat",now)>now+90 || claims.optString("sub").isEmpty())throw new Exception("Identity token expired or invalid");
    JSONObject keys=request(JWKS,"GET",null,null,null);
    JSONArray list=keys.optJSONArray("keys");if(list==null)throw new Exception("OpenAI identity keys unavailable");
    JSONObject jwk=null;for(int i=0;i<list.length();i++){JSONObject k=list.getJSONObject(i);if(header.optString("kid").equals(k.optString("kid"))&&"RSA".equals(k.optString("kty"))){jwk=k;break;}}
    if(jwk==null)throw new Exception("No matching signing key");
    BigInteger modulus=new BigInteger(1,decode(jwk.getString("n"))),exponent=new BigInteger(1,decode(jwk.getString("e")));
    Signature verifier=Signature.getInstance("SHA256withRSA");
    verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus,exponent)));
    verifier.update((parts[0]+"."+parts[1]).getBytes(StandardCharsets.US_ASCII));
    if(!verifier.verify(decode(parts[2])))throw new Exception("Identity signature mismatch");
    return claims;
  }
  public void connect(){
    if(connecting)return;connecting=true;lastError="";emitStatus();
    new Thread(()->{
      try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
        listener.setSoTimeout(150000);
        String callback="http://127.0.0.1:"+listener.getLocalPort()+"/auth/callback";
        String state=secret(24),nonce=secret(24),verifier=secret(48);
        String challenge=base64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String requestedClient;JSONObject existing;
        synchronized(tokenLock){existing=profile;requestedClient=existing==null?"dynamic_agent_client":existing.optString("client_id","dynamic_agent_client");}
        boolean registering="dynamic_agent_client".equals(requestedClient);
        String url=AUTH+"?"+form("client_id",requestedClient,"response_type","code","redirect_uri",callback,
          "scope","openid profile email offline_access resource.invoke chatgpt.tokens.use.direct",
          "resource",RESOURCE,"state",state,"nonce",nonce,"code_challenge",challenge,"code_challenge_method","S256",
          "ext_agent_host_id",hostId(storage));
        if(registering)url+="&"+form("agent_name_hint","RAAD AI");
        else if(existing!=null&&!existing.optString("id_token").isEmpty())url+="&"+form("id_token_hint",existing.optString("id_token"));
        final String browserUrl=url;
        main.post(()->{
          try{Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(browserUrl));activity.startActivity(intent);}
          catch(Exception e){lastError="Unable to open system browser";emitStatus();}
        });
        try(Socket peer=listener.accept()){
          peer.setSoTimeout(12000);
          BufferedReader br=new BufferedReader(new InputStreamReader(peer.getInputStream(),StandardCharsets.UTF_8));
          String first=br.readLine();if(first==null||first.length()>8000||!first.startsWith("GET /auth/callback?"))throw new Exception("Invalid OAuth callback");
          String qs=first.split(" ")[1].split("\\?",2)[1];
          java.util.HashMap<String,String> params=new java.util.HashMap<>();
          for(String part:qs.split("&")){String[] pair=part.split("=",2);params.put(URLDecoder.decode(pair[0],"UTF-8"),pair.length>1?URLDecoder.decode(pair[1],"UTF-8"):"");}
          String page="<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><body style='background:#070b17;color:#dffaff;font:16px system-ui;text-align:center;padding:17vh 20px'><h1>RAAD AI</h1><p>Authorization received. Return to RAAD to continue.</p><a style='color:#74ddff' href='raad://auth-complete'>Return to RAAD</a></body></html>";
          byte[] bytes=page.getBytes(StandardCharsets.UTF_8);
          String resp="HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "+bytes.length+"\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
          peer.getOutputStream().write(resp.getBytes(StandardCharsets.US_ASCII));peer.getOutputStream().write(bytes);peer.getOutputStream().flush();
          if(!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),params.getOrDefault("state","").getBytes(StandardCharsets.UTF_8)))throw new Exception("Authorization state mismatch");
          if(params.containsKey("error"))throw new Exception("ChatGPT authorization not granted");
          String client=registering?params.getOrDefault("client_id",""):requestedClient;
          if(client.isEmpty()||"dynamic_agent_client".equals(client)||!client.startsWith("oaiapp_"))throw new Exception("Registration did not return an issued client ID");
          if(!registering&&params.containsKey("client_id")&&!client.equals(params.get("client_id")))throw new Exception("Client registration mismatch");
          String code=params.getOrDefault("code","");if(code.isEmpty())throw new Exception("OpenAI did not return an authorization code");
          JSONObject tokens=request(TOKEN,"POST",null,form("grant_type","authorization_code","client_id",client,
              "code",code,"code_verifier",verifier,"redirect_uri",callback,"resource",RESOURCE),"application/x-www-form-urlencoded");
          JSONObject claims=verifyId(tokens.getString("id_token"),client,nonce);
          if(existing!=null&&!existing.optString("subject").equals(claims.optString("sub")))throw new Exception("Different ChatGPT account; disconnect first to switch");
          String scopes=tokens.optString("scope","");
          if(!scopes.contains("chatgpt.tokens.use.direct"))throw new Exception("ChatGPT plan usage permission was not granted");
          if(tokens.optString("refresh_token").isEmpty())throw new Exception("Offline access permission was not granted");
          JSONObject fresh=new JSONObject();fresh.put("client_id",client);fresh.put("subject",claims.getString("sub"));
          fresh.put("email",claims.optString("email"));fresh.put("id_token",tokens.getString("id_token"));
          fresh.put("access_token",tokens.getString("access_token"));fresh.put("refresh_token",tokens.getString("refresh_token"));
          fresh.put("scopes",scopes);fresh.put("expires_at",System.currentTimeMillis()+Math.max(120,tokens.optLong("expires_in",3600))*1000);
          saveProfile(fresh);lastError="";
        }
      }catch(Exception e){lastError=e.getMessage()==null?"Connection failed":e.getMessage();}
      finally{connecting=false;emitStatus();}
    },"raad-siwc-oauth").start();
  }
  private JSONObject refreshIfNeeded() throws Exception{
    synchronized(tokenLock){
      if(profile==null)throw new Exception("Connect your ChatGPT plan first");
      if(System.currentTimeMillis()+120000<profile.optLong("expires_at"))return profile;
      JSONObject prev=profile;
      JSONObject result=request(TOKEN,"POST",null,form("grant_type","refresh_token","client_id",prev.getString("client_id"),
        "refresh_token",prev.getString("refresh_token"),"resource",RESOURCE),"application/x-www-form-urlencoded");
      JSONObject next=new JSONObject(prev.toString());
      next.put("access_token",result.getString("access_token"));
      next.put("refresh_token",result.getString("refresh_token"));
      next.put("expires_at",System.currentTimeMillis()+Math.max(120,result.optLong("expires_in",3600))*1000);
      if(result.has("scope"))next.put("scopes",result.getString("scope"));
      saveProfile(next);return next;
    }
  }
  public void disconnect(){
    new Thread(()->{
      String warning="";
      try{
        JSONObject prior;
        synchronized(tokenLock){prior=profile;}
        if(prior!=null){
          JSONObject discovery=request("https://auth.openai.com/.well-known/openid-configuration","GET",null,null,null);
          String revocation=discovery.optString("revocation_endpoint");
          if(!revocation.startsWith("https://auth.openai.com/"))throw new Exception("Unsafe revocation endpoint");
          HttpURLConnection c=(HttpURLConnection)new URL(revocation).openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(12000);
          c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
          try(OutputStream o=c.getOutputStream()){o.write(form("token",prior.getString("refresh_token"),"token_type_hint","refresh_token","client_id",prior.getString("client_id")).getBytes(StandardCharsets.UTF_8));}
          if(c.getResponseCode()!=200)warning="Remote revocation could not be confirmed. Disconnect RAAD in ChatGPT settings.";
          c.disconnect();
        }
      }catch(Exception e){warning="Remote revocation could not be confirmed. Disconnect RAAD in ChatGPT settings.";}
      synchronized(tokenLock){profile=null;storage.edit().remove("vault").commit();}
      lastError=warning;emitStatus();
    },"raad-siwc-revoke").start();
  }
  public void ask(String requestId, String text, String historyJson, String language){
    if(replying){emitReply(requestId,"","Another AI request is still running.");return;}
    if(text==null||text.trim().isEmpty()||text.length()>6000){emitReply(requestId,"","Message is empty or too long.");return;}
    replying=true;emitStatus();
    new Thread(()->{
      String answer="",error="";
      try{
        JSONObject auth=refreshIfNeeded();String token=auth.getString("access_token");
        JSONObject catalog=request(RESOURCE+"/models","GET",token,null,null);
        JSONArray list=catalog.optJSONArray("models");if(list==null)list=catalog.optJSONArray("data");
        String model="";if(list!=null)for(int i=0;i<list.length();i++){
          JSONObject entry=list.optJSONObject(i);if(entry==null)continue;
          String id=entry.optString("slug",entry.optString("id"));if(id.isEmpty())continue;
          String vis=entry.optString("visibility","list");if(!"list".equals(vis))continue;
          if(model.isEmpty())model=id;
          if(id.contains("instant")||id.contains("mini")){model=id;break;}
        }
        if(model.isEmpty())throw new Exception("No eligible ChatGPT plan models were returned");
        JSONArray input=new JSONArray();JSONArray history=new JSONArray(historyJson==null?"[]":historyJson);
        int start=Math.max(0,history.length()-8);
        for(int i=start;i<history.length();i++){
          JSONObject item=history.optJSONObject(i);if(item==null)continue;
          String role=item.optString("role"),content=item.optString("text");
          if((role.equals("user")||role.equals("assistant"))&&!content.isEmpty()&&content.length()<4000)
            input.put(new JSONObject().put("role",role).put("content",content));
        }
        input.put(new JSONObject().put("role","user").put("content",text));
        String instructions="You are RAAD (رعد), a practical Egyptian personal AI assistant. Speak naturally in Egyptian Arabic unless user prefers English. You have no automatic access to other accounts, user files or real-time external information. For ordinary conversation reply concisely in plain language. If and only if the user explicitly asks to perform an Android action, emit only one exact JSON envelope prefixed RAAD_ACTION: and no other text. Allowed types and fields: media {type,service youtube|soundcloud,query (empty query to open app)}; web-search {type,query}; open_app {type,app youtube|soundcloud|browser|maps|gmail|whatsapp|calendar|clock}; alarm {type,time HH:MM 24h,label}; sms {type,phone,message}; whatsapp {type,phone,message}; email {type,to,subject,body}; dial {type,phone}; calendar {type,title,start YYYY-MM-DDTHH:MM,details}; task {type,title}. Media actions open app/web search results and do not guarantee autoplay. Read-only navigation is immediate on explicit request; do not invent search findings. For SMS, WhatsApp, email and dial you prepare a draft/dialer; the user decides to send or call. If required details are missing, ask the user one question instead of producing an action. No purchases, financial transfers, live messages, data deletion, credential access, or other action types. Never fake success. Current timezone is Africa/Cairo. User local date/time: " + java.time.ZonedDateTime.now(java.time.ZoneId.of("Africa/Cairo")).toString() + ".";
        if("en".equals(language))instructions+=" Respond in English.";
        JSONObject payload=new JSONObject().put("model",model).put("input",input).put("instructions",instructions).put("store",false).put("stream",true);
        HttpURLConnection c=(HttpURLConnection)new URL(RESOURCE+"/responses").openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(20000);c.setReadTimeout(120000);c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","text/event-stream");c.setRequestProperty("Authorization","Bearer "+token);
        try(OutputStream stream=c.getOutputStream()){stream.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
        if(c.getResponseCode()!=200){try(InputStream stream=c.getErrorStream()){JSONObject problem=new JSONObject(new String(readLimited(stream,300000),StandardCharsets.UTF_8));JSONObject err=problem.optJSONObject("error");throw new Exception(err==null?"AI request failed":err.optString("message","AI request failed"));}}
        boolean completed=false;StringBuilder textOut=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8))){
          String line;while((line=br.readLine())!=null){
            if(!line.startsWith("data: "))continue;String raw=line.substring(6).trim();if(raw.equals("[DONE]"))break;
            JSONObject msg;try{msg=new JSONObject(raw);}catch(Exception ignored){continue;}
            String type=msg.optString("type");
            if(type.equals("response.output_text.delta")){String d=msg.optString("delta");if(textOut.length()+d.length()>30000)throw new Exception("Response was too long");textOut.append(d);}
            else if(type.equals("response.completed")){completed=true;
              if(textOut.length()==0){JSONObject response=msg.optJSONObject("response");JSONArray output=response==null?null:response.optJSONArray("output");if(output!=null)for(int i=0;i<output.length();i++){JSONObject item=output.optJSONObject(i);JSONArray content=item==null?null:item.optJSONArray("content");if(content!=null)for(int k=0;k<content.length();k++){JSONObject part=content.optJSONObject(k);if(part!=null&&"output_text".equals(part.optString("type")))textOut.append(part.optString("text"));}}}
            }
            else if(type.equals("response.failed")||type.equals("response.incomplete")||type.equals("error"))throw new Exception("ChatGPT returned an incomplete response. Check your plan limits or retry.");
          }
        }finally{c.disconnect();}
        if(!completed)throw new Exception("ChatGPT stream ended before completion");
        answer=textOut.toString().trim();if(answer.isEmpty())throw new Exception("ChatGPT sent an empty response");
      }catch(Exception e){error=e.getMessage()==null?"AI request failed":e.getMessage();}
      finally{replying=false;emitStatus();emitReply(requestId,answer,error);}
    },"raad-siwc-inference").start();
  }
}