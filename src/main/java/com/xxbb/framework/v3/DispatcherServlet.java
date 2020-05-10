package com.xxbb.framework.v3;

import com.xxbb.framework.annotation.*;
import com.xxbb.framework.utils.StringUtils;
import lombok.Getter;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

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
    private List<Handler> handlerMapping = new ArrayList<>();

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
        System.out.println("ioc:"+ioc);
        System.out.println("handlerMapping:"+handlerMapping);
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
                //映射url
                RequestMapping requestMapping=method.getAnnotation(RequestMapping.class);
                //使用“/+”可以将多个/为一个/，避免baseUrl为空时导致出现多个//
                String regex=("/"+baseUrl+"/"+requestMapping.value()).replaceAll("/+","/");
                Pattern pattern=Pattern.compile(regex);
                handlerMapping.add(new Handler(entry.getValue(),method,pattern));
                System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->" +
                        "DispatcherServlet.initHandlerMapping:"+regex+"--->"+method);
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
                        String infBeanName=StringUtils.firstCharToLowerCase(inf.getSimpleName());
                        ioc.put(infBeanName,instance);

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
        System.out.println(contextPath);
        System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->"+
                "url:"+url+",contextPath:"+contextPath);
        url=url.replaceAll(contextPath,"").replaceAll("/+","/");
        System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->"+
                "replacedUrl:"+url);
        Handler handler=getHandler(req);
        if(null==handler){
            System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->"+
                    url+" is not in the container");
            try {
                resp.getWriter().write("404 Not Found");
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage());
            }
            return;
        }
        //获取方法的参数列表
        Class<?>[] paramTypes=handler.getMethod().getParameterTypes();
        //存储方法需要的形参值
        Object[] paramValues=new Object[paramTypes.length];
        //获取请求参数
        Map<String,String[]> reqParams=req.getParameterMap();
        //遍历参数



        Method method=this.handlerMapping.get(url);
        //第一个参数：方法所在的实例
        //第二个参数：调用时需要的参数
        Map<String,String[]> params=req.getParameterMap();
        //获取方法的形参列表
        Class<?>[] parameterTypes=method.getParameterTypes();
        //保存请求的url参数列表
        Map<String,String[]> parameterMap=req.getParameterMap();
        //保存赋值参数的位置
        Object[] paramValues=new Object[parameterTypes.length];
        //根据参数位置动态赋值
        for(int i=0;i<parameterTypes.length;i++){
            Class<?> parameterType=parameterTypes[i];
            if(parameterType==HttpServletRequest.class){
                paramValues[i]=req;
            }else if(parameterType==HttpServletResponse.class){
                paramValues[i]=resp;
            }else if(parameterType==String.class){
                //提取方法中加了注解的参数
                Annotation[][] annotations=method.getParameterAnnotations();
                for(int j=0;j<annotations.length;j++){
                    for(Annotation annotation:annotations[j]){
                        if(annotation instanceof RequestParam){
                            String paramName=((RequestParam)annotation).value().trim();
                            if(!"".equals(paramName)){
                                String value=Arrays.toString(parameterMap.get(paramName))
                                        .replace("\\[|\\]","")
                                        .replace("\\s","");
                                paramValues[i]=value;
                            }
                        }
                    }
                }

            }
        }
        //通过反射获取Method所在类及其类名
        String beanName=StringUtils.firstCharToLowerCase(method.getDeclaringClass().getSimpleName());
        Object instance=ioc.get(beanName);
        System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->"+
                "doDispatcher.instance:"+instance);
        System.out.println("[" + Thread.currentThread().getName() + "]" + this.getClass().getName() + "--->"+
                "doDispatcher.method:"+method);
        try {
            method.invoke(ioc.get(beanName), req,resp,params.get("name")[0]);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Handler getHandler(HttpServletRequest req) {
        return null;
    }

    /**
     * 记录Controller中的RequestMapping和Method的对应关系
     * @author xxbb
     */
    @Getter
    private class Handler{
        /**
         * controller实例
         */
        protected Object controller;
        /**
         * 方法
         */
        protected Method method;
        /**
         * 正则参数
         * 
         */
        protected Pattern pattern;
        /**
         * 参数顺序
         */
        protected Map<String,Integer> paramIndexMapping;
        
        protected Handler(Object controller,Method method,Pattern pattern){
            this.controller=controller;
            this.method=method;
            this.pattern=pattern;
            paramIndexMapping=new HashMap<>();
            putParamIndexMapping(method);
        }

        /**
         * 提取传入方法的参数
         * @param method
         */
        private void putParamIndexMapping(Method method) {
            //提取方法中加了注解的参数
            Annotation[][] annotations=method.getParameterAnnotations();
            for(int i=0;i<annotations.length;i++){
                for(Annotation annotation:annotations[i]){
                    if(annotation instanceof RequestParam){
                        String paramName=((RequestParam)annotation).value();
                        if(!"".equals(paramName)){
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }
            }
            //提取方法中的request和response参数
            Class<?>[] paramTypes=method.getParameterTypes();
            for(int i=0;i<paramTypes.length;i++){
                Class<?> type=paramTypes[i];
                if(type==HttpServletRequest.class||type==HttpServletResponse.class){
                    paramIndexMapping.put(type.getName(),i);
                }
            }
        }
    }
}
