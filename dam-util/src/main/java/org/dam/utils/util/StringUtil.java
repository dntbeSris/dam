package org.dam.utils.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;

/**
 * Created by zhujianxin on 2018/3/7.
 */
public class StringUtil {

    private static final String FORMATTER = "EEE, dd MMM YYYY hh:mm:ss 'GMT'";
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat(FORMATTER,Locale.ENGLISH);
    public static boolean httpLineEquals(String server,String client){
        char[] serverArr = server.toCharArray();
        char[] clientArr = client.toCharArray();
        boolean isEquals = true;
        if(server.length() != client.length()){
            return false;
        }
        for(int i=0;i<serverArr.length;i++){
            if(serverArr[i] != '_' && clientArr[i] != '-' && serverArr[i] != clientArr[i]){
                isEquals = false;
            }
        }
        return isEquals;
    }

    public static String format(String msg,Object ...args){
        int index = 0;
        while (msg.contains("{}")){
            if(index < args.length){
                Object arg = args[index++];
                if(arg instanceof Integer){
                    arg = String.valueOf(arg);
                }
                int startIndex = msg.indexOf("{}");
                String before = msg.substring(0,startIndex+2);
                String end = msg.substring(startIndex+2,msg.length());
                msg = before.replace("{}",arg.toString()) + end;
            }else{
                break;
            }
        }
        return msg;
    }

    public static String getConfigPath(){

        String filePath = StringUtil.class.getResource("").getPath();
        try {
            if(filePath.contains("file:/")){
                filePath = filePath.replace("file:/","");
                filePath = filePath.substring(0,filePath.indexOf("/lib/dam-"));
            }else{
                filePath = filePath.substring(1,filePath.indexOf("/lib/dam-"));
            }
        }catch (Exception e){
            filePath = "C:/Users/roro/IdeaProjects/dam";
        }
        return filePath + "/config/config.properties";
    }


    public static String getGMTDate(Date date){
        Calendar calendar = Calendar.getInstance(new SimpleTimeZone(0,"GMT"));
        FORMAT.setCalendar(calendar);
        return FORMAT.format(date);
    }

}
