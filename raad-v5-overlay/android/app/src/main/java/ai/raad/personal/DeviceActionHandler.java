package ai.raad.personal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.widget.Toast;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

/** Whitelisted user-reviewed Android intents. Never sends messages, places calls, or edits accounts silently. */
public final class DeviceActionHandler {
  private DeviceActionHandler() {}
  static String limited(JSONObject obj, String key, int max) throws Exception {
    String value = obj.optString(key, "").trim();
    if(value.length() > max) throw new Exception("Field is too long: " + key);
    return value;
  }
  public static void review(Activity activity, String json) {
    try {
      if(json == null || json.length() > 10000) throw new Exception("Invalid action request");
      JSONObject data = new JSONObject(json);
      String type = limited(data,"type",24);
      String summary;
      Intent intent;
      switch(type){
        case "alarm": {
          String time=limited(data,"time",5);
          if(!time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new Exception("Invalid alarm time");
          int hour=Integer.parseInt(time.substring(0,2)), min=Integer.parseInt(time.substring(3));
          intent=new Intent(AlarmClock.ACTION_SET_ALARM);
          intent.putExtra(AlarmClock.EXTRA_HOUR,hour);
          intent.putExtra(AlarmClock.EXTRA_MINUTES,min);
          intent.putExtra(AlarmClock.EXTRA_MESSAGE,limited(data,"label",80));
          intent.putExtra(AlarmClock.EXTRA_SKIP_UI,false);
          summary="Open Clock to review alarm at " + time + "?";
          break;
        }
        case "sms": {
          String phone=limited(data,"phone",18);if(!phone.matches("\\+?[0-9]{7,15}"))throw new Exception("Invalid phone");
          String message=limited(data,"message",1800);if(message.isEmpty())throw new Exception("Empty message");
          intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+Uri.encode(phone)));
          intent.putExtra("sms_body",message);
          summary="Open SMS draft to " + phone + "? You press Send.";
          break;
        }
        case "whatsapp": {
          String phone=limited(data,"phone",18);if(!phone.matches("\\+?[0-9]{7,15}"))throw new Exception("Invalid phone");
          String message=limited(data,"message",1800);if(message.isEmpty())throw new Exception("Empty message");
          intent=new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/"+phone.replace("+","")+"?text="+Uri.encode(message)));
          intent.setPackage("com.whatsapp");
          summary="Open WhatsApp draft to " + phone + "? You press Send.";
          break;
        }
        case "email": {
          String to=limited(data,"to",254);if(!to.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new Exception("Invalid email");
          String subject=limited(data,"subject",200), body=limited(data,"body",6000);
          if(body.isEmpty())throw new Exception("Empty email");
          intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:"+Uri.encode(to)));
          intent.putExtra(Intent.EXTRA_SUBJECT,subject);
          intent.putExtra(Intent.EXTRA_TEXT,body);
          summary="Open email draft to " + to + "? You press Send.";
          break;
        }
        case "media": {
          String service=limited(data,"service",20).toLowerCase(Locale.ROOT);
          if(!service.equals("youtube") && !service.equals("soundcloud")) throw new Exception("Unsupported media service");
          String query=limited(data,"query",160);
          final String link;
          if(service.equals("youtube")) link=query.isEmpty()?"https://www.youtube.com/": "https://www.youtube.com/results?search_query="+Uri.encode(query);
          else link=query.isEmpty()?"https://soundcloud.com/":"https://soundcloud.com/search?q="+Uri.encode(query);
          intent=new Intent(Intent.ACTION_VIEW,Uri.parse(link));
          intent.addCategory(Intent.CATEGORY_BROWSABLE);
          // Prefer the installed app; if it cannot handle the link, fall back to a browser.
          intent.setPackage(service.equals("youtube")?"com.google.android.youtube":"com.soundcloud.android");
          summary=query.isEmpty()?"Open "+service+"?":"Search "+service+" for: "+query+"? Choose a result and press Play; automatic playback is not guaranteed.";
          break;
        }
        case "web-search": {
          String query=limited(data,"query",160);
          if(query.isEmpty())throw new Exception("Search text required");
          intent=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(query)));
          intent.addCategory(Intent.CATEGORY_BROWSABLE);
          summary="Search Google for: "+query+"?";
          break;
        }
        case "dial": {
          String phone=limited(data,"phone",18);if(!phone.matches("\\+?[0-9]{7,15}"))throw new Exception("Invalid phone");
          intent=new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(phone)));
          summary="Open dialer for " + phone + "? No call will be placed yet.";
          break;
        }
        case "calendar": {
          String title=limited(data,"title",100), start=limited(data,"start",16);
          if(title.isEmpty()||!start.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}"))throw new Exception("Invalid event");
          java.time.LocalDateTime date=java.time.LocalDateTime.parse(start);
          long millis=date.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
          intent=new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI);
          intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,millis);
          intent.putExtra(CalendarContract.Events.TITLE,title);
          intent.putExtra(CalendarContract.Events.DESCRIPTION,limited(data,"details",1500));
          summary="Open Calendar to review event: " + title + "?";
          break;
        }
        default:throw new Exception("Unsupported device action");
      }
      final Intent safe = intent;
      new AlertDialog.Builder(activity).setTitle("RAAD — Review action")
        .setMessage(summary).setNegativeButton("Cancel",(d,w)->{})
        .setPositiveButton("Open app",(d,w)->{
          try{activity.startActivity(safe);}catch(ActivityNotFoundException e){
            // Some Android apps do not support web-search deep links. Open the same HTTPS URL using the default browser.
            if("media".equals(type)){
              try {safe.setPackage(null);activity.startActivity(safe);}catch(ActivityNotFoundException ignored){Toast.makeText(activity,"No compatible app or browser found",Toast.LENGTH_LONG).show();}
            }else Toast.makeText(activity,"No compatible app is installed",Toast.LENGTH_LONG).show();
          }
        }).show();
    } catch(Exception e) {Toast.makeText(activity,"RAAD: " + e.getMessage(),Toast.LENGTH_LONG).show();}
  }
}