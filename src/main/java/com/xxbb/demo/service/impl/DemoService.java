package com.xxbb.demo.service.impl;

import com.xxbb.demo.service.IDemoService;

/**
 * @author xxbb
 */
public class DemoService implements IDemoService {
    @Override
    public String get(String name) {
        return "My name is "+name;
    }
}
