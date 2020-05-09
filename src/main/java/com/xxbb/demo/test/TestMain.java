package com.xxbb.demo.test;

import com.xxbb.demo.Order;
import com.xxbb.framework.utils.StringUtils;

import java.lang.reflect.Field;
import java.net.URL;

/**
 * @author xxbb
 */

public class TestMain {
    public static void main(String[] args) {
        TestMain testMain=new TestMain();
        testMain.StringTest();

    }
    public void StringTest(){
        int index="a.b.b.c".lastIndexOf(".");
        System.out.println("a.b.b.c".substring(index));
        System.out.println(index);
        System.out.println("////asdasd////sadasd".replaceAll("/+","/"));
    }
    public void reflectTest(){
        Class<?> clazz= Order.class;
        Field[] fields=clazz.getDeclaredFields();
        for(Field field:fields){
            System.out.println(field.getName());
            System.out.println(field.getType());
            System.out.println(field.getType().getTypeName());
            System.out.println(field.getType().getName());
        }
    }
    public static void soutTest(){
        System.out.println("/+");
    }

    public  void urlTest(){
        URL filePath=this.getClass().getClassLoader().getResource("com.xxbb.demo".replaceAll("\\.","/"));
        System.out.println(filePath);
    }
    public void charTest(){
        long start1=System.currentTimeMillis();
        for(int i=0;i<10000;i++){
            StringUtils.firstCharToLowerCase("asd");
        }
        long start2=System.currentTimeMillis();
        for(int i=0;i<10000;i++){
            StringUtils.firstCharToUpperCase("asd");
        }
        long start3=System.currentTimeMillis();

        System.out.println(start2-start1);
        System.out.println(start3-start2);
    }
}
