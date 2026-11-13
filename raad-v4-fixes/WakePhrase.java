package ai.raad.personal;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java wake phrase matcher. Never treats non-wake speech as an instruction. */
public final class WakePhrase {
    private static final Pattern PREFIX=Pattern.compile("(?:^|[\\s،,.:!])(?:يا\\s*)?(?:رعد|راعد|raad|rad)(?=$|[\\s،,.:!?])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private WakePhrase(){}
    public static String command(String transcript){
        if(transcript==null||transcript.length()>500)return null;
        String normalized=transcript.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي');
        Matcher m=PREFIX.matcher(normalized);
        if(!m.find())return null;
        return normalized.substring(m.end()).replaceFirst("^[،,.:!\\s]+","").trim();
    }
}