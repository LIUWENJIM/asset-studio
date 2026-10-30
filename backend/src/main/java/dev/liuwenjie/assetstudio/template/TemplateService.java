package dev.liuwenjie.assetstudio.template;

import dev.liuwenjie.assetstudio.common.ApiException;
import dev.liuwenjie.assetstudio.common.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    private final TemplateMapper templateMapper;

    public TemplateService(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public List<Template> list(String category) {
        if (category == null || category.isBlank()) {
            return templateMapper.findAll();
        }
        return templateMapper.findByCategory(category.trim());
    }

    public Template get(String id) {
        Template template = templateMapper.findById(id);
        if (template == null) {
            throw new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, "template", "模板不存在：" + id);
        }
        return template;
    }

    public List<String> categories() {
        return templateMapper.findCategories();
    }
}