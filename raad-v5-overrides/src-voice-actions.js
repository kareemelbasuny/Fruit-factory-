import {checkDeviceAction} from './device.js';
/** A model may PROPOSE only one allowlisted action; never perform it without review. */
export function extractModelAction(raw='') {
  const text=String(raw||'');
  const match=text.match(/(?:^|\n)(?:RAAD_ACTION_JSON|RAAD_ACTION):\s*(\{[^\r\n]*\})\s*(?=\n|$)/i);
  if(!match) return {text,action:null,error:''};
  const display=text.replace(match[0],'').trim();
  try {
    const candidate=JSON.parse(match[1]);
    if(!candidate || Array.isArray(candidate) || typeof candidate!=='object')throw Error('Action must be an object');
    if(candidate.type==='task') {
      const title=String(candidate.title||'').trim();
      if(!title||title.length>140)throw Error('Invalid task title');
      return {text:display||'I prepared a new task for your approval.',action:{type:'task',title},error:''};
    }
    const action=checkDeviceAction(candidate.type,candidate);
    if(!['alarm','sms','whatsapp','email','calendar','dial','contacts','media','web-search','open_app'].includes(action.type))throw Error('Action not approved for AI');
    return {text:display||'I prepared an action for you to review.',action,error:''};
  }catch(e){return {text:display||'I could not validate the requested action.',action:null,error:`Proposed action rejected: ${e.message}`};}
}
export function describeAction(action) {
 const a=action.type==='task'?action:checkDeviceAction(action.type,action);
 switch(a.type){
  case'task':return `Create a local task: ${a.title}`;
  case'alarm':return `Set alarm for ${a.time} (${a.label})`;
  case'sms':return `Open SMS draft to ${a.phone}: ${a.message}`;
  case'whatsapp':return `Open WhatsApp draft to ${a.phone}: ${a.message}`;
  case'email':return `Open email draft to ${a.to} · ${a.subject}: ${a.body}`;
  case'calendar':return `Open calendar event: ${a.title} · ${a.start}`;
  case'dial':return `Open dialer with ${a.phone}`;
  case'contacts':return 'Open contact picker';
  case'media':return a.query?`Open ${a.service==='youtube'?'YouTube':'SoundCloud'} search for: ${a.query}`:`Open ${a.service==='youtube'?'YouTube':'SoundCloud'}`;
  case'web-search':return `Search Google for: ${a.query}`;
  case'open_app':return `Open ${a.app}`;
  default:throw Error('Action not supported');
 }
}