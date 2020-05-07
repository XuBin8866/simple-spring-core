package com.xxbb.framework.v1;

import com.xxbb.framework.annotation.AutoWired;
import com.xxbb.framework.annotation.Controller;
import com.xxbb.framework.annotation.RequestMapping;
import com.xxbb.framework.annotation.Service;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author xxbb
 */
public class DispatcherServlet extends HttpServlet {
    private Map<String, Object> mapping = new HashMap<>();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String url = req.getRequestURI();
        System.out.println("url:" + url);
        String contextPath = req.getContextPath();
        System.out.println("contextPath:" + contextPath);
        url = url.replace(contextPath, "").replaceAll("/+", "/");
        System.out.println("replaced_url:" + url);

        if (!this.mapping.containsKey(url)) {
            resp.getWriter().write("404 not Found!!!");
            return;
        }

        Method method = (Method) this.mapping.get(url);
        Map<String, String[]> params = req.getParameterMap();
        method.invoke(this.mapping.get(method.getDeclaringClass().getName()), req, resp, params.get("name")[0]);

    }

    @Override
    public void init() throws ServletException {
        System.out.println("init start!!!");
        InputStream is = null;
        try {
            Properties configContext = new Properties();
            is = this.getClass().getClassLoader().getResourceAsStream("application.properties");
            configContext.load(is);
            String scanPackage = configContext.getProperty("scanPackage");
            doScanner(scanPackage);
            for (String className : mapping.keySet()) {
                System.out.println("this.name:"+className);
                if (!className.contains(".")) {
                    continue;
                }
                Class<?> clazz = Class.forName(className);
                //该类上是否有Controller注解
                if (clazz.isAnnotationPresent(Controller.class)) {
                    mapping.put(className, clazz.newInstance());
                    //该类上是否有RequestMapping注解
                    if (clazz.isAnnotationPresent(RequestMapping.class)) {
                        RequestMapping requestMapping = clazz.getAnnotation(RequestMapping.class);
                        String baseUrl = requestMapping.value();
                        Method[] methods = clazz.getMethods();
                        for (Method method : methods) {
                            if (!method.isAnnotationPresent(RequestMapping.class)) {
                                continue;
                            }
                            String url = (baseUrl + "/" + requestMapping.value()).replaceAll("/+", "/");
                            mapping.put(url, method);
                            System.out.println("Mapped" + url + "," + method);
                        }
                    }
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    if ("".equals(beanName)) {
                        beanName = clazz.getName();
                    }
                    Object instance = clazz.newInstance();
                    mapping.put(beanName, instance);
                    for (Class<?> inf : clazz.getInterfaces()) {
                        mapping.put(inf.getName(), instance);
                    }
                }
            }
            for (Object object : mapping.values()) {
                if (null == object) {
                    continue;
                }
                Class<?> clazz = object.getClass();
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (!field.isAnnotationPresent(AutoWired.class)) {
                            continue;
                        }
                        AutoWired autoWired = clazz.getAnnotation(AutoWired.class);
                        String beanName = autoWired.value();
                        if ("".equals(beanName)) {
                            beanName=field.getType().getName();
                        }
                        field.setAccessible(true);
                        try {
                            field.set(mapping.get(clazz.getName()),mapping.get(beanName));
                        } catch (IllegalArgumentException | IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(is!=null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("MVC Framework has inited");
        System.out.println(mapping);
    }

    private void doScanner(String scanPackage) {
        URL url=this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        if(null!=url){
            File classDir=new File(url.getFile());
            for(File file: Objects.requireNonNull(classDir.listFiles())){
                if(file.isDirectory()){
                    doScanner(scanPackage+"."+file.getName());
                }else{
                    if(!file.getName().endsWith(".class")){
                        continue;
                    }
                    String className=(scanPackage+"."+file.getName().replace(".class",""));
                    mapping.put(className,null);
                }
            }
        }

    }
}
