package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.entity.Faq;
import com.zelvira.zelvira.repository.FaqRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/faqs")
public class FaqController {
    private final FaqRepository repository;

    public FaqController(FaqRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Faq> getAll(
            @RequestParam(required = false, defaultValue = "false") boolean includeDraft,
            @RequestParam(required = false) Boolean onlyActive) {
        if (includeDraft && !Boolean.TRUE.equals(onlyActive)) {
            return repository.findAllByOrderByIdAsc();
        }
        return repository.findAllByIsActiveTrueOrderByIdAsc();
    }

    @GetMapping("/active")
    public List<Faq> getActive() {
        return repository.findAllByIsActiveTrueOrderByIdAsc();
    }

    @PostMapping
    public Faq create(@RequestBody Faq faq) {
        return repository.save(faq);
    }

    @PutMapping("/{id}")
    public Faq update(@PathVariable Long id, @RequestBody Faq faq) {
        return repository.findById(id).map(existing -> {
            if (faq.getQuestion() != null) existing.setQuestion(faq.getQuestion());
            if (faq.getAnswer() != null) existing.setAnswer(faq.getAnswer());
            if (faq.getCategory() != null) existing.setCategory(faq.getCategory());
            existing.setDisplayOrder(faq.getDisplayOrder());
            if (faq.getIsActive() != null) {
                existing.setIsActive(faq.getIsActive());
            }
            return repository.save(existing);
        }).orElseGet(() -> {
            faq.setId(id);
            return repository.save(faq);
        });
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }
}
