package com.zheng.ems_link.api;

import com.zheng.ems_link.model.JsonBody;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class IndexController {

    @Get
    public JsonBody<String> home() {
        return new JsonBody<String>(1,"success","hell the world");
    }
}
