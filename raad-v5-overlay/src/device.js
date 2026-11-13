/** Device actions are deterministic requests. They never access accounts or send automatically. */
export function normalizeDigits(value='') {
  return String(value).replace(/[٠-٩۰-۹]/g, c => String('٠١٢٣٤٥٦٧٨٩'.indexOf(c) >= 0 ? '٠١٢٣٤٥٦٧٨٩'.indexOf(c) : '۰۱۲۳۴۵۶۷۸۹'.indexOf(c)));
}
export function checkDeviceAction(type, args={}) {
  const text=v=>String(v||'').trim();
  if(type==='alarm') {
    const time=normalizeDigits(text(args.time));
    const matched=/^([01]?\d|2[0-3]):([0-5]\d)$/.exec(time);
    if(!matched) throw Error('Enter alarm time as HH:MM (24-hour).');
    return {type,time:`${matched[1].padStart(2,'0')}:${matched[2]}`,label:text(args.label).slice(0,80)||'RAAD alarm'};
  }
  if(['sms','whatsapp','dial'].includes(type)) {
    const phone=normalizeDigits(text(args.phone)).replace(/[ ()-]/g,'');
    if(!/^\+?[0-9]{7,15}$/.test(phone)) throw Error('Enter a valid phone number (7–15 digits, optional +).');
    if(type==='dial') return {type,phone};
    const message=text(args.message);
    if(!message || message.length>1800) throw Error('Message must contain 1–1800 characters.');
    return {type,phone,message};
  }
  if(type==='email') {
    const to=text(args.to);
    if(to.length>254||!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(to))throw Error('Enter one valid recipient email address.');
    const subject=text(args.subject), body=text(args.body);
    if(subject.length>200||body.length>6000)throw Error('Email subject/body too long.');
    if(!body)throw Error('Write an email message.');
    return {type,to,subject,body};
  }
  if(type==='calendar'){
    const title=text(args.title);const start=text(args.start);
    if(!title||title.length>100)throw Error('Enter an event title (up to 100 characters).');
    if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(start)||!Number.isFinite(Date.parse(start)))throw Error('Enter an event start date and time.');
    const parsed=new Date(start);
    if(Number.isNaN(parsed.getTime()))throw Error('Invalid calendar date.');
    return {type,title,start,details:text(args.details).slice(0,1500)};
  }
  if(type==='media') {
    const service=text(args.service).toLowerCase();
    if(!['youtube','soundcloud'].includes(service))throw Error('Media source must be YouTube or SoundCloud.');
    const query=text(args.query).replace(/[\u0000-\u001F\u007F]/g,' ');
    if(query.length>160)throw Error('Search text must be 160 characters or fewer.');
    return {type,service,query};
  }
  if(type==='web-search') {
    const query=text(args.query).replace(/[\u0000-\u001F\u007F]/g,' ');
    if(!query||query.length>160)throw Error('Enter a search query (1–160 characters).');
    return {type,query};
  }
  // External navigation is read-only. Restrict every destination to a small, fixed allowlist.
  if(type==='media') {
    const platform=text(args.platform).toLowerCase();
    if(!['youtube','soundcloud'].includes(platform))throw Error('Unsupported media platform.');
    const query=text(args.query);
    if(query.length>180)throw Error('Search term is too long.');
    return {type,platform,query};
  }
  if(type==='web_search') {
    const query=text(args.query);
    if(!query||query.length>180)throw Error('Enter a search term (up to 180 characters).');
    return {type,query};
  }
  if(type==='open_app') {
    const app=text(args.app).toLowerCase();
    if(!['youtube','soundcloud','browser','maps','gmail','whatsapp','calendar','clock'].includes(app))throw Error('This app is not on the allowed list.');
    return {type,app};
  }
  if(type==='contacts'||type==='settings'||type==='notification-test')return {type};
  throw Error('Unsupported device action.');
}
export function parseDeviceCommand(input) {
  const text=normalizeDigits(String(input||'').trim());
  // Local Android app and web search commands work without any AI credentials.
  // We intentionally open search results rather than claiming direct music playback.
  const request=text.replace(/^(?:(?:يا\s+)?(?:رعد|raad)[،,:\s]*)/i,'')
    .replace(/^(?:ممكن|لو سمحت|please|can you)\s*/i,'').trim();
  const mediaSource=raw=>/ساوند\s?كلاود|ساوندكلاود|sound\s?cloud/i.test(raw)?'soundcloud':/يوتيوب|youtube|you\s?tube/i.test(raw)?'youtube':null;
  const mediaName=String.raw`(?:يوتيوب|اليوتيوب|youtube|you\s?tube|ساوند\s?كلاود|الساوند\s?كلاود|sound\s?cloud)`;
  let m=request.match(new RegExp('^(?:افتح|شغل|شغّل|open|launch)\\s+(?:برنامج|تطبيق|app)?\\s*('+mediaName+')\\s*$','i'));
  if(m)return checkDeviceAction('media',{service:mediaSource(m[1]),query:''});
  m=request.match(/^(?:شغل(?:لي|\s+لي)?|شغّل(?:لي|\s+لي)?|شغلي|play|find|دور(?:لي)?\s+على|ابحث(?:لي)?\s+عن|search(?:\s+for)?)\s+(.+?)\s+(?:من\s+على|على|في|on|from|in)\s+(?:ال)?(يوتيوب|youtube|you\s?tube|ساوند\s?كلاود|sound\s?cloud)\s*$/i);
  if(m){
    const query=m[1].replace(/^(?:اغنية|أغنية|اغنيه|أغنيه|فيديو|مقطع|song|track|video|music)\s+/i,'').trim();
    return checkDeviceAction('media',{service:mediaSource(m[2]),query});
  }
  m=request.match(/^(?:search|search\s+for|دور|دورلي|ابحث|ابحثلي)\s+(?:في|على|on|in)?\s*(يوتيوب|youtube|you\s?tube|ساوند\s?كلاود|sound\s?cloud)\s+(?:عن|for)?\s*(.+)$/i);
  if(m)return checkDeviceAction('media',{service:mediaSource(m[1]),query:m[2]});
  m=request.match(/^(?:اعمل\s+(?:سيرش|بحث)|ابحث|ابحثلي|دور|دورلي|search(?:\s+(?:google|the web))?(?:\s+for)?|google)\s+(?:عن|على|for)?\s*(.+)$/i);
  if(m&&m[1].trim())return checkDeviceAction('web-search',{query:m[1]});
  const alarm=text.match(/^(?:يا رعد[،, ]*)?(?:(?:اعمل|اضبط|اظبط|حط|ظبط|شغّل|شغل)\s*)?(?:منبه|منبّه|alarm|set alarm)(?:\s*(?:الساعة|at|for))?\s*([01]?\d|2[0-3]):([0-5]\d)(.*)$/i);
  if(alarm){return checkDeviceAction('alarm',{time:`${alarm[1]}:${alarm[2]}`,label:alarm[3].trim()||'RAAD alarm'});}
  const sms=text.match(/^(?:sms|send sms|رسالة نصية|ابعت sms)\s+(?:to\s+|إلى\s+|ل\s*)?(\+?\d[\d\s()-]{6,20})\s*[:：]\s*(.+)$/is);
  if(sms)return checkDeviceAction('sms',{phone:sms[1],message:sms[2]});
  const wa=text.match(/^(?:whatsapp|واتساب|ابعت واتساب)\s+(?:to\s+|إلى\s+|ل\s*)?(\+?\d[\d\s()-]{6,20})\s*[:：]\s*(.+)$/is);
  if(wa)return checkDeviceAction('whatsapp',{phone:wa[1],message:wa[2]});
  const command=text.replace(/^(?:(?:يا رعد|يا رعاد|hey raad|raad)[،,\s]*)/i,'').trim();
  const youtube=/(?:اليوتيوب|يوتيوب|يوتوب|youtube)/i.test(command);
  const soundcloud=/(?:الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud|soundcloud)/i.test(command);
  const platform=soundcloud?'soundcloud':youtube?'youtube':null;
  const play=/(?:شغّل|شغل|شغلي|شغللي|شغل\s*لي|play(?:\s+me)?|دورلي|دوّرلي|دور|ابحث|search(?:\s+for)?|اعمل\s+سيرش|سيرش)\s+/i;
  const playMatch=command.match(play);
  if(platform && playMatch){
    let q=command.slice(playMatch.index+playMatch[0].length).trim();
    q=q.replace(/^(?:لي\s+|عن\s+|على\s+|علي\s+|لـ\s*|أغنية\s+|اغنية\s+|أغنيه\s+|الأغنية\s+|الاغنية\s+|song\s+|فيديو\s+|video\s+)+/i,'');
    q=q.replace(/\s+(?:(?:من\s+على|من\s+علي|من|على|علي|في|on|from|in)\s+)?(?:اليوتيوب|يوتيوب|يوتوب|youtube|الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud|soundcloud)\s*$/i,'').trim();
    if(q && !/^(?:برنامج\s*)?(?:اليوتيوب|يوتيوب|youtube|ساوند\s*كلاود|soundcloud)$/i.test(q))return checkDeviceAction('media',{platform,query:q});
  }
  const pureOpen=command.match(/^(?:(?:افتح|افتحلي|فتح|open|efta7|eفتاح)\s+)?(?:برنامج\s+|تطبيق\s+|app\s+)?(?:اليوتيوب|يوتيوب|يوتوب|youtube|الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud|soundcloud)\s*$/i);
  if(platform&&pureOpen)return checkDeviceAction('media',{platform,query:''});
  const defaultPlay=command.match(/^(?:شغّل|شغل|شغللي|شغل\s*لي|play(?:\s+me)?)\s+(?:لي\s+)?(?:أغنية|اغنية|أغنيه|song|موسيقى|music)\s+(.+)$/i);
  if(defaultPlay)return checkDeviceAction('media',{platform:platform||'youtube',query:defaultPlay[1].trim()});
  if(platform&&/(?:افتح|open)/i.test(command))return checkDeviceAction('media',{platform,query:''});
  const openApp=command.match(/^(?:افتح|افتحلي|open|efta7)\s+(?:برنامج\s+|تطبيق\s+|app\s+)?(جوجل\s*مابس|خرائط|maps|جيميل|gmail|واتساب|whatsapp|المتصفح|كروم|browser|chrome|التقويم|calendar|الساعة|المنبه|clock)\s*$/i);
  if(openApp){
    const appName=openApp[1].toLowerCase();
    const app=/(?:مابس|خرائط|maps)/.test(appName)?'maps':/(?:جيميل|gmail)/.test(appName)?'gmail':/(?:واتساب|whatsapp)/.test(appName)?'whatsapp':/(?:تقويم|calendar)/.test(appName)?'calendar':/(?:الساعة|المنبه|clock)/.test(appName)?'clock':'browser';
    return checkDeviceAction('open_app',{app});
  }
  const search=command.match(/^(?:اعمل\s+(?:لي\s+)?سيرش|سيرش|ابحث|ابحثلي|دور|دورلي|search(?:\s+for)?|google)\s+(?:عن\s+|على\s+|for\s+)?(.+)$/i);
  if(search){
    const query=search[1].trim();
    return platform?checkDeviceAction('media',{platform,query}):checkDeviceAction('web_search',{query});
  }
  return null;
}
export const DEVICE_COPY={
 alarm:'Your Clock app will open to review this alarm.',
 sms:'Your SMS app will open with a draft. You must press Send.',
 whatsapp:'WhatsApp will open with a prepared message. You must press Send.',
 email:'Your email app will open with a draft. You must press Send.',
 calendar:'Your calendar will open to review and save the event.',
 contacts:'The Android contact picker will open. Only the contact you choose is shared.',
 dial:'Your phone dialer will open. The call will not start automatically.',
 settings:'Android will open RAAD app settings for you to manage permissions.',
 'notification-test':'A test notification will be shown only if permission is granted.',
 media:'Opened a music/video search in the selected app or browser. Choose a result to play; automatic playback is not guaranteed.',
 web_search:'Opened search results in your browser.',
 open_app:'Requested the selected app. If unavailable, a supported website may open.' ,
 media:'Opens YouTube or SoundCloud search results; choose a track and press Play in that app.',
 'web-search':'Opens Google search results in your browser.'
};

/** Explicit agent-action envelope. Model responses are data, never executable code. */
export function parseAgentAction(answer) {
  const text=String(answer||'').trim();
  const match=/^RAAD_ACTION:\s*(\{[^]*\})$/.exec(text);
  if(!match||match[1].length>9000)return null;
  let parsed;
  try{parsed=JSON.parse(match[1]);}catch{return null;}
  if(!parsed||typeof parsed!=='object'||Array.isArray(parsed))return null;
  const type=parsed.type;
  if(!['alarm','sms','whatsapp','email','dial','calendar','task','media','web-search'].includes(type))return null;
  if(type==='task') {
    const title=String(parsed.title||'').trim();
    if(!title||title.length>120)throw Error('Task title must be 1–120 characters.');
    return {type,title};
  }
  return checkDeviceAction(type,parsed);
}