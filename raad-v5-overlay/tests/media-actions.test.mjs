import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {parseDeviceCommand,checkDeviceAction,parseAgentAction} from '../src/device.js';
import {extractModelAction} from '../src/voice-actions.js';
const read=p=>readFile(new URL('../'+p,import.meta.url),'utf8');
test('open YouTube and search music in YouTube or SoundCloud from Egyptian Arabic and English',()=>{
 assert.deepEqual(parseDeviceCommand('يا رعد افتح اليوتيوب'),{type:'media',service:'youtube',query:''});
 assert.deepEqual(parseDeviceCommand('شغل لي أغنية أم كلثوم على اليوتيوب'),{type:'media',service:'youtube',query:'أم كلثوم'});
 assert.deepEqual(parseDeviceCommand('يا رعد دور على عمرو دياب على ساوند كلاود'),{type:'media',service:'soundcloud',query:'عمرو دياب'});
 assert.deepEqual(parseDeviceCommand('open YouTube'),{type:'media',service:'youtube',query:''});
 assert.deepEqual(parseDeviceCommand('play jazz on SoundCloud'),{type:'media',service:'soundcloud',query:'jazz'});
});
test('web searching and launching approved installed apps',()=>{
 assert.deepEqual(parseDeviceCommand('يا رعد اعمل سيرش عن تسويق مصر'),{type:'web-search',query:'تسويق مصر'});
 assert.deepEqual(parseDeviceCommand('search for latest news'),{type:'web-search',query:'latest news'});
 assert.deepEqual(parseDeviceCommand('افتح جيميل'),{type:'open_app',app:'gmail'});
 assert.deepEqual(parseDeviceCommand('افتح خرائط'),{type:'open_app',app:'maps'});
});
test('model navigation tools remain restricted',()=>{
 assert.deepEqual(parseAgentAction('RAAD_ACTION:{"type":"media","service":"youtube","query":"music"}'),{type:'media',service:'youtube',query:'music'});
 assert.deepEqual(extractModelAction('RAAD_ACTION: {"type":"web-search","query":"web"}').action,{type:'web-search',query:'web'});
 assert.deepEqual(extractModelAction('RAAD_ACTION: {"type":"open_app","app":"gmail"}').action,{type:'open_app',app:'gmail'});
 assert.throws(()=>checkDeviceAction('media',{service:'evil',query:'music'}));
 assert.throws(()=>checkDeviceAction('open_app',{app:'com.bank.app'}));
 assert.throws(()=>checkDeviceAction('web-search',{query:''}));
 assert.throws(()=>checkDeviceAction('media',{service:'youtube',query:'x'.repeat(161)}));
 assert.equal(parseAgentAction('RAAD_ACTION:{"type":"purchase","amount":100}'),null);
});
test('Android opens approved HTTPS platforms without unrestricted permissions',async()=>{
 const java=await read('android/app/src/main/java/ai/raad/personal/DeviceActionHandler.java');
 const manifest=await read('android/app/src/main/AndroidManifest.xml');
 for(const s of ['https://www.youtube.com/results?search_query=','https://soundcloud.com/search?q=','https://www.google.com/search?q=','getLaunchIntentForPackage','ACTION_VIEW']) assert.ok(java.includes(s),s);
 assert.doesNotMatch(manifest,/QUERY_ALL_PACKAGES/);
 assert.doesNotMatch(java,/SmsManager|sendTextMessage|ACTION_CALL/);
});