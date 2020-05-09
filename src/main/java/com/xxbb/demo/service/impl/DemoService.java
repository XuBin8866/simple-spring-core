package com.xxbb.demo.service.impl;

import com.xxbb.demo.service.IDemoService;
import com.xxbb.framework.annotation.Service;

/**
 * @author xxbb
 */
@Service
public class DemoService implements IDemoService {
    @Override
    public String get(String name) {
        return "My name is "+name;
    }
}
