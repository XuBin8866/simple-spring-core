package com.xxbb.framework.v2;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author xxbb
 */
public class DispatcherServlet extends HttpServlet {
    /**
     * 保存application.properties配置文件中的内容
     */
    private Properties contextCofig=new Properties();
    /**
     * 容器
     */
    private Map<String ,Object> ioc=new HashMap<>();
    /**
     * 扫描得到的所有类名
     */
    private List<String> classNames=new ArrayList<>();
    /**
     * 请求与方法之间的对应关系
     */
    private Map<String, Method> handlerMapping=new HashMap<>();

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
        //5.厨师阿华HandlerMapping
        initHandlerMapping();

        System.out.println("MVC Framework has inited");
    }

    private void initHandlerMapping() {
    }

    private void doAutoWired() {

    }

    private void doInstance() {
    }

    private void doScanner(String scanPackage) {
    }

    private void doLoadConfig(String contextConfigLocation) {
    }



    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
    }
}
