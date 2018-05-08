package org.dam.server.cache;

import org.dam.bridge.bean.ControllerBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ControllerCache {

    private static final Map<String,List<ControllerBean>> appToControllersMap = WebappsCache.webAppMap;

    public static List<ControllerBean> getControllerByAppName(String appName){
        if(appToControllersMap.containsKey(appName)){
            return appToControllersMap.get(appName);
        }
        throw new IllegalArgumentException("can not found this webapp by the name : "+ appName);
    }

    public static void addWebApp(String appName){
        if(appToControllersMap.containsKey(appName)){
            throw new IllegalArgumentException("this webapp:"+appName+"was exited");
        }
        appToControllersMap.putIfAbsent(appName,new ArrayList<>());
    }

    public static void replaceControllersByAppName(String appName,List<ControllerBean> controllerBeans){
        if(appToControllersMap.containsKey(appName)){
            appToControllersMap.replace(appName,appToControllersMap.get(appName),controllerBeans);
        }
        throw new IllegalArgumentException("can not found this webapp by the name : "+ appName);
    }

    public static void appendControllersByAppName(String appName,List<ControllerBean> controllerBeans){
        if(appToControllersMap.containsKey(appName)){
            List<ControllerBean> old = appToControllersMap.get(appName);
            old.addAll(controllerBeans);
            appToControllersMap.replace(appName,appToControllersMap.get(appName),old);
        }
        throw new IllegalArgumentException("can not found this webapp by the name : "+ appName);
    }
}