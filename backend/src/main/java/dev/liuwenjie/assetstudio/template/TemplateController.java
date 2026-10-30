package dev.liuwenjie.assetstudio.template;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<TemplateResponse> list(@RequestParam(required = false) String category) {
        return templateService.list(category).stream().map(TemplateResponse::from).toList();
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return templateService.categories();
    }

    @GetMapping("/{id}")
    public TemplateResponse detail(@PathVariable String id) {
        return TemplateResponse.from(templateService.get(id));
    }
}