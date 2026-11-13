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
    if(!query||query.length>160)throw Error('Enter a search query.');
    return {type,query};
  }
  if(type==='open_app') {
    const app=text(args.app).toLowerCase();
    if(!['youtube','soundcloud','browser','maps','gmail','whatsapp','calendar','clock'].includes(app))throw Error('This app is not supported by RAAD.');
    return {type,app};
  }
  if(type==='contacts'||type==='settings'||type==='notification-test')return {type};
  throw Error('Unsupported device action.');
}
export function parseDeviceCommand(input) {
  const text=normalizeDigits(String(input||'').trim());
  const request=text.replace(/^(?:(?:يا\s+)?(?:رعد|raad)|hey\s+raad)[،,:\s]*/i,'')
    .replace(/^(?:ممكن|لو سمحت|please|can you)\s*/i,'').trim();
  const sourceFor=s=>/ساوند\s*كلاود|ساوندكلاود|sound\s*cloud/i.test(s)?'soundcloud':/يوتيوب|يوتوب|youtube|you\s*tube/i.test(s)?'youtube':null;
  const service=sourceFor(request);
  const platformSuffix=/\s+(?:(?:من\s+على|من\s+علي|على|علي|في|on|from|in)\s+)?(?:اليوتيوب|يوتيوب|يوتوب|youtube|you\s*tube|الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud)\s*$/i;
  const playVerb=/(?:شغّل|شغل|شغللي|شغل\s+لي|شغلي|play(?:\s+me)?|دورلي(?:\s+على)?|دور(?:\s+على)?|ابحث(?:\s+عن)?|search(?:\s+for)?|اعمل\s+سيرش(?:\s+عن)?|سيرش(?:\s+عن)?)\s+/i;
  const mediaMatch=service?request.match(playVerb):null;
  if(mediaMatch){
    let query=request.slice(mediaMatch.index+mediaMatch[0].length).trim();
    query=query.replace(/^(?:(?:في|على|علي|in|on)\s+)?(?:اليوتيوب|يوتيوب|يوتوب|youtube|you\s*tube|الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud)\s*(?:(?:عن|for)\s+)?/i,'');
    query=query.replace(/^(?:(?:لي|عن|لـ|أغنية|اغنية|أغنيه|الأغنية|الاغنية|song|track|فيديو|video)\s+)+/i,'');
    query=query.replace(platformSuffix,'').trim();
    if(query && !/^(?:برنامج\s*)?(?:اليوتيوب|يوتيوب|youtube|ساوند\s*كلاود|soundcloud)$/i.test(query))
      return checkDeviceAction('media',{service,query});
  }
  const justOpen=/^(?:(?:افتح|افتحلي|open|launch|efta7)\s+)?(?:برنامج\s+|تطبيق\s+|app\s+)?(?:اليوتيوب|يوتيوب|يوتوب|youtube|you\s*tube|الساوند\s*كلاود|ساوند\s*كلاود|sound\s*cloud)\s*$/i;
  if(service&&justOpen.test(request))return checkDeviceAction('media',{service,query:''});
  if(service && /^(?:افتح|افتحلي|open)\s+/.test(request) && !mediaMatch)return checkDeviceAction('media',{service,query:''});
  const otherApp=request.match(/^(?:افتح|افتحلي|open|launch|efta7)\s+(?:برنامج\s+|تطبيق\s+|app\s+)?(جوجل\s*مابس|خرائط|maps|جيميل|gmail|واتساب|whatsapp|المتصفح|كروم|browser|chrome|التقويم|calendar|الساعة|المنبه|clock)\s*$/i);
  if(otherApp){
    const name=otherApp[1].toLowerCase();
    const app=/(?:مابس|خرائط|maps)/.test(name)?'maps':/(?:جيميل|gmail)/.test(name)?'gmail':/(?:واتساب|whatsapp)/.test(name)?'whatsapp':/(?:تقويم|calendar)/.test(name)?'calendar':/(?:الساعة|المنبه|clock)/.test(name)?'clock':'browser';
    return checkDeviceAction('open_app',{app});
  }
  const defaultSong=request.match(/^(?:شغّل|شغل|شغللي|شغل\s+لي|play(?:\s+me)?)\s+(?:لي\s+)?(?:أغنية|اغنية|أغنيه|song|موسيقى|music)\s+(.+)$/i);
  if(defaultSong)return checkDeviceAction('media',{service:service||'youtube',query:defaultSong[1].replace(platformSuffix,'').trim()});
  const google=request.match(/^(?:اعمل\s+(?:لي\s+)?(?:سيرش|بحث)|سيرش|ابحث|ابحثلي|دور|دورلي|search(?:\s+(?:google|the web))?(?:\s+for)?|google)\s+(?:عن\s+|على\s+|for\s+)?(.+)$/i);
  if(google&&google[1].trim())return service?checkDeviceAction('media',{service,query:google[1].replace(platformSuffix,'').trim()}):checkDeviceAction('web-search',{query:google[1]});
  const alarm=request.match(/^(?:(?:اعمل|اضبط|اظبط|حط|ظبط|شغّل|شغل)\s*)?(?:منبه|منبّه|alarm|set alarm)(?:\s*(?:الساعة|at|for))?\s*([01]?\d|2[0-3]):([0-5]\d)(.*)$/i);
  if(alarm)return checkDeviceAction('alarm',{time:`${alarm[1]}:${alarm[2]}`,label:alarm[3].trim()||'RAAD alarm'});
  const sms=request.match(/^(?:sms|send sms|رسالة نصية|ابعت sms)\s+(?:to\s+|إلى\s+|ل\s*)?(\+?\d[\d\s()-]{6,20})\s*[:：]\s*(.+)$/is);
  if(sms)return checkDeviceAction('sms',{phone:sms[1],message:sms[2]});
  const wa=request.match(/^(?:whatsapp|واتساب|ابعت واتساب)\s+(?:to\s+|إلى\s+|ل\s*)?(\+?\d[\d\s()-]{6,20})\s*[:：]\s*(.+)$/is);
  if(wa)return checkDeviceAction('whatsapp',{phone:wa[1],message:wa[2]});
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
 media:'Opens YouTube or SoundCloud search results; choose a track and press Play in that app.',
 'web-search':'Opens Google search results in your browser.',
 open_app:'Opens the chosen installed app, or a compatible website when unavailable.'
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
  if(!['alarm','sms','whatsapp','email','dial','calendar','task','media','web-search','open_app'].includes(type))return null;
  if(type==='task') {
    const title=String(parsed.title||'').trim();
    if(!title||title.length>120)throw Error('Task title must be 1–120 characters.');
    return {type,title};
  }
  return checkDeviceAction(type,parsed);
}