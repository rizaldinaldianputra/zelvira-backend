package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.HelpArticle;
import com.zelvira.zelvira.repository.HelpArticleRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/help-articles")
public class HelpArticleController {
    private final HelpArticleRepository repository;

    public HelpArticleController(HelpArticleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<HelpArticle> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeDraft,
            @RequestParam(required = false) Boolean onlyActive) {
        if (includeDraft && !Boolean.TRUE.equals(onlyActive)) {
            return repository.findAllByOrderByDisplayOrderAsc();
        }
        return repository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @GetMapping("/active")
    public List<HelpArticle> getActive() {
        return repository.findAllByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @PostMapping
    public HelpArticle create(@RequestBody HelpArticle article) {
        if (article.getDisplayOrder() == 0) {
            Integer maxOrder = repository.findMaxDisplayOrder();
            article.setDisplayOrder(maxOrder + 1);
        }
        return repository.save(article);
    }

    @PutMapping("/{id}")
    public HelpArticle update(@PathVariable Long id, @RequestBody HelpArticle article) {
        return repository.findById(id).map(existing -> {
            if (article.getTitle() != null) existing.setTitle(article.getTitle());
            if (article.getSubtitle() != null) existing.setSubtitle(article.getSubtitle());
            if (article.getContent() != null) existing.setContent(article.getContent());
            if (article.getCategory() != null) existing.setCategory(article.getCategory());
            if (article.getImageUrl() != null) existing.setImageUrl(article.getImageUrl());
            existing.setDisplayOrder(article.getDisplayOrder());
            if (article.getIsActive() != null) {
                existing.setIsActive(article.getIsActive());
            }
            return repository.save(existing);
        }).orElseGet(() -> {
            article.setId(id);
            return repository.save(article);
        });
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
