package com.xxbb.demo.mvc;

import com.xxbb.demo.service.IDemoService;
import com.xxbb.framework.annotation.AutoWired;
import com.xxbb.framework.annotation.Controller;
import com.xxbb.framework.annotation.RequestMapping;
import com.xxbb.framework.annotation.RequestParam;

import javax.servlet.http.*;
import java.io.IOException;

/**
 * @author xxbb
 */
@Controller
@RequestMapping("/demo")
public class DemoAction {
    @AutoWired
    private IDemoService demoService;
    @RequestMapping("/query")
    public void query(HttpServletRequest req, HttpServletResponse resp, @RequestParam("name") String name){
        String result=demoService.get(name);
        try {
            resp.getWriter().write(result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void add(HttpServletRequest req, HttpServletResponse resp, @RequestParam("a") Integer a,@RequestParam("b") Integer b){
        try {
            resp.getWriter().write(a+""+b+"="+(a+b));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @RequestMapping("/remove")
    public void remove(HttpServletRequest req, HttpServletResponse resp, @RequestParam("id") Integer id){

    }
}
