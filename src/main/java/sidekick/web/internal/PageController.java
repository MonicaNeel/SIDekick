package sidekick.web.internal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** The Thymeleaf shell (PLAN §4): one page, a thin consumer of the JSON API. */
@Controller
class PageController {

    @GetMapping("/")
    String index() {
        return "index";
    }
}
