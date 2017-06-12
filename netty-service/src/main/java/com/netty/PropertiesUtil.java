package com.netty;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Properties;

public class PropertiesUtil {

	private static final String DEFAULT_CONFIGURATION_FILE = "netty_service_ports.properties";
	public static Properties prop = getProperties();
	
	public static String getProperty(String property) {
        return prop.getProperty(property);
    }
	
	public static Properties getProperties() {
        Properties prop = new Properties();
        URL url = getPropertyFileURL();
        
        try {
            FileInputStream file = new FileInputStream(new File(url.toURI()));
            prop.load(file);
            file.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return prop;
    }
	
	/**
	 * 获取配置文件 URL 路径
	 * @return
	 */
	public static URL getPropertyFileURL(){
		URL url = null;
		try {
			Method method = Thread.class.getMethod("getContextClassLoader", null);
			ClassLoader classLoader =(ClassLoader) method.invoke(Thread.currentThread(), null);
			if(null == classLoader)
				classLoader = PropertiesUtil.class.getClassLoader();
			else
				url = classLoader.getResource(DEFAULT_CONFIGURATION_FILE);
			
			if(null == url)
				url = ClassLoader.getSystemResource(DEFAULT_CONFIGURATION_FILE);
			
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
		return url;
	}
}
