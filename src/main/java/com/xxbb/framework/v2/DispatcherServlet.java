package com.xxbb.framework.v2;

import com.xxbb.framework.annotation.AutoWired;
import com.xxbb.framework.annotation.Controller;
import com.xxbb.framework.annotation.RequestMapping;
import com.xxbb.framework.annotation.Service;
import com.xxbb.framework.utils.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author xxbb
 */
public class DispatcherServlet extends HttpServlet {
    /**
     * 保存application.properties配置文件中的内容
     */
    private Properties contextCofig = new Properties();
    /**
     * 容器
     */
    private Map<String, Object> ioc = new HashMap<>();
    /**
     * 扫描得到的所有类名
     */
    private List<String> classNames = new ArrayList<>();
    /**
     * 请求与方法之间的对应关系
     */
    private Map<String, Method> handlerMapping = new HashMap<>();

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500 Exception" + Arrays.toString(e.getStackTrace()));
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        //1.加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描相关的类
        doScanner(contextCofig.getProperty("scanPackage"));
        //3.初始化扫描到的类，并且将它们放入IoC容器中
        doInstance();
        //4.完成依赖注入
        doAutoWired();
        //5.初始化HandlerMapping
        initHandlerMapping();

        System.out.println("MVC Framework has inited");
    }

    /**
     * 策略模式的应用案例，mapping的处理Controller类
     */
    private void initHandlerMapping() {
        if (ioc.isEmpty()) {
            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                    "DispatcherServlet.initHandlerMapping:" + "ioc is empty");
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz=entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(Controller.class)){
                continue;
            }
            //获取请求url
            //要求类和方法上都需要与requestMapping注解
            String baseUrl="";
            if(clazz.isAnnotationPresent(RequestMapping.class)){
                RequestMapping requestMapping=clazz.getAnnotation(RequestMapping.class);
                baseUrl=requestMapping.value();
            }
            //默认获取公共方法
            for(Method method:clazz.getMethods()){
                if(!method.isAnnotationPresent(RequestMapping.class)){
                    continue;
                }
                RequestMapping requestMapping=method.getAnnotation(RequestMapping.class);
                //使用“/+”可以将多个/为一个/，避免baseUrl为空时导致出现多个//
                String url=("/"+baseUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                handlerMapping.put(url,method);
                System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                        "DispatcherServlet.initHandlerMapping:"+url+"--->"+method);
            }
        }
    }

    /**
     * 依赖注入，自动注入
     */
    private void doAutoWired() {
        if (ioc.isEmpty()) {
            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                    "DispatcherServlet.doAutoWired:" + "ioc is empty");
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            //获取所有类型的声明字段
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                //查找AutoWired标签
                if (!field.isAnnotationPresent(AutoWired.class)) {
                    continue;
                }
                AutoWired autoWired = field.getAnnotation(AutoWired.class);
                //判断是否有自定义名称
                String beanName = autoWired.value().trim();
                if ("".equals(beanName)) {
                    //截取出类名并将首字母小写,
                    //要求基本数据类型和包装类不使用依赖注入
                    String preBeanName = field.getType().getName();
                    int index=preBeanName.lastIndexOf(".");
                    beanName=StringUtils.firstCharToLowerCase(preBeanName.substring(index+1));
                }
                //强制赋值
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 工厂方法的具体实现
     */
    private void doInstance() {
        //判断是否进行了包扫描
        if (classNames.isEmpty()) {
            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                    "DispatcherServlet.doInstance:" + "list of className is empty");
        }
        try {
            //实例化所有类，IoC容器
            for (String className : classNames) {
                Class<?> clazz = Class.forName(className);
                //思考什么样的类需要初始化
                //加注解的类需要初始化，怎么判断
                //这里只用Controller和Service注解进行举例

                //判断B注解是否在A类上: A..isAnnotationPresent(B)
                if (clazz.isAnnotationPresent(Controller.class)) {
                    Object instance = clazz.newInstance();
                    //Spring中默认类名的首字母小写
                    String beanName = StringUtils.firstCharToLowerCase(clazz.getSimpleName());
                    ioc.put(beanName, instance);
                } else if (clazz.isAnnotationPresent(Service.class)) {
                    //1.获取service的自定义名称
                    Service service = clazz.getAnnotation(Service.class);
                    String beanName = service.value();
                    //2.没有自定义名称，默认是哦那个类名的首字母小写
                    if ("".equals(beanName.trim())) {
                        beanName = StringUtils.firstCharToLowerCase(clazz.getSimpleName());
                    }
                    //3.实例化bean对象
                    Object instance = clazz.newInstance();
                    ioc.put(beanName, instance);
                    //4.根据接口类型自动赋值，即控制反转，直接通过接口获取实现类，这里会很单一
                    //假设有多个类都实现了一个接口，那么这个接口理论上要有多个K-V对值，但是这里只能取第一个K-V对的值。
                    //之后如果还有别的类的实例要对应该接口的key则会抛出异常，不够优雅
                    for (Class<?> inf : clazz.getInterfaces()) {
                        if (ioc.containsKey(inf.getName())) {
                            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                                    inf.getName() + " has existed!");
                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描配置文件中给出的包路径中的所有类,使用list存储它们的类名
     *
     * @param scanPackage 包路径
     */
    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                "DispatcherServlet.doScanner.url" + url);
        if (null != url) {
            File files = new File(url.getFile());
            for (File file : Objects.requireNonNull(files.listFiles())) {
                System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                        "doScanner listFile:" + file.getName());
                if (file.isDirectory()) {
                    doScanner(scanPackage + "." + file.getName());
                } else {
                    if (!file.getName().endsWith(".class")) {
                        continue;
                    }
                    String className = (scanPackage + "." + file.getName().replace(".class", ""));
                    classNames.add(className);


                }
            }
        } else {
            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                    "DispatcherServlet.doScanner.url" + ":url is null");
        }

    }

    /**
     * 加载配置文件
     *
     * @param contextConfigLocation properties配置文件
     */
    private void doLoadConfig(String contextConfigLocation) {
        //直接通过类路径找到框架主配置文件的路径
        //并将配置文件内容读取到properties对象中
        String prefix="classpath:";
        String location=contextConfigLocation;
        if(contextConfigLocation.contains(prefix)){
            location=contextConfigLocation.substring(10);
        }
        InputStream is=null;
        try {
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            contextCofig.load(is);
        } catch (IOException e) {
            throw new RuntimeException("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                    "DispatcherServlet.doLoadConfig:" + e.getMessage());
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 逻辑处理与业务分发
     * @param req req
     * @param resp resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws Exception{
        String url=req.getRequestURI();
        String contextPath=req.getContextPath();
        System.out.println(url+"   "+contextPath);

        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handlerMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found");
            return;
        }
        Method method=this.handlerMapping.get(url);
        //第一个参数：方法所在的实例
        //第二个参数：调用时需要的参数
        Map<String,String[]> params=req.getParameterMap();
        //获取方法的形参列表
        Class<?>[] parameterTypes=method.getParameterTypes();
    }
}
