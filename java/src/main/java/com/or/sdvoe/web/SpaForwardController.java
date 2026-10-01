package com.or.sdvoe.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** SPA fallback：工作空间前端路由。 */
@Controller
public class SpaForwardController {

    @GetMapping({"/workspace", "/workspace/", "/workspace/{*path}"})
    public String workspaceSpa() {
        return "forward:/index.html";
    }
}
